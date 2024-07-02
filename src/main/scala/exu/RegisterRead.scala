package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{MI, PipelineRegister}
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RegisterReadStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writeBackBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val forwardBus   = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val wakeupBus    = Vec(config.nWide, Decoupled(new Bus(config)))
    val out          = Vec(config.nWide, Decoupled(new MI(config)))

    val stall = Input(UInt(1.W))
  })

  val prf = Module(
    new DecoupledBlockRAM(UInt(config.dataWidth.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide)
    )
  )
  val pReg = Module(new PipelineRegister(config))

  val overrideForward = Module(new OverrideFromBuses(config)) // override from the forwardBus
  overrideForward.io.inBus <> io.forwardBus

  val overridenRFData = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  (0 until config.nWide).foreach(j => {
    io.wakeupBus(j).bits.tag  := io.in(j).bits.rdTag
    io.wakeupBus(j).bits.data := DontCare
    io.wakeupBus(j).valid     := io.in(j).bits.wakeup

    prf.io.rip(j).bits.addr                := io.in(j).bits.rs1Tag
    prf.io.rip(j).valid                    := io.in(j).valid
    io.in(j).ready                         := prf.io.rip(j).ready
    prf.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2Tag
    prf.io.rip(j + config.nWide).valid     := io.in(j).valid
    io.in(j).ready                         := prf.io.rip(j).ready & prf.io.rip(j + config.nWide).ready

    overridenRFData(j).bits         := io.in(j).bits
    overridenRFData(j).bits.rs1Data := prf.io.rop(j).bits.data
    overridenRFData(j).bits.rs2Data := prf.io.rop(j + config.nWide).bits.data
    overridenRFData(j).valid        := prf.io.rop(j).valid & prf.io.rop(j + config.nWide).valid

    prf.io.rop(j).ready                := io.out(j).ready
    prf.io.rop(j + config.nWide).ready := io.out(j).ready

    prf.io.wp(j).bits.addr   := io.writeBackBus(j).bits.tag
    prf.io.wp(j).bits.enable := io.writeBackBus(j).valid
    prf.io.wp(j).bits.data   := io.writeBackBus(j).bits.data
    prf.io.wp(j).valid       := io.writeBackBus(j).valid
    io.writeBackBus(j).ready := prf.io.wp(j).ready
  })
  overrideForward.io.in <> overridenRFData

  pReg.io.in <> overrideForward.io.out
  io.out     <> pReg.io.out
}
