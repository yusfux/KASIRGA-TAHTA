package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAM, BlockRAMParams, DCCrossbar, DCPipelineRegister}

class RegisterReadStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val forwardBus   = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val wakeupBus    = Vec(config.nWide, ValidIO(new TagBus(config)))
    val out          = MixedVec(config.listExUnits.map(length => Vec(length, Decoupled(new MI(config)))))
  })

  val overrideForward   = Module(new OverrideFromBuses(config))
  val overrideWriteBack = Module(new OverrideFromBuses(config))
  val crossbar          = Module(new DCCrossbar(new MI(config))(config.nWide, config.listExUnits))
  val aluPRegs          = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  val aluCrossbarIndex = 0 // TODO: move to WoodConfig

  val prf = Module(
    new BlockRAM(UInt(config.dataWidth.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide)
    )
  )

  val overridenRFData = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRFData <> io.in

  (0 until config.nWide).foreach(j => {
    io.wakeupBus(j).bits.tag := io.in(j).bits.rdTag
    io.wakeupBus(j).valid    := io.in(j).bits.wakeup

    prf.io.rip(j).addr                := io.in(j).bits.rs1Tag
    prf.io.rip(j + config.nWide).addr := io.in(j).bits.rs2Tag

    overridenRFData(j).bits.rs1Data := prf.io.rop(j).data
    overridenRFData(j).bits.rs2Data := prf.io.rop(j + config.nWide).data

    prf.io.wp(j).addr   := io.writebackBus(j).bits.tag
    prf.io.wp(j).enable := io.writebackBus(j).valid
    prf.io.wp(j).data   := io.writebackBus(j).bits.data

    crossbar.io.sel(j) := io.in(j).bits.exEngine === ExEngine.alu.asUInt
  })

  overrideForward.io.inBus   <> io.forwardBus
  overrideWriteBack.io.inBus <> io.writebackBus
  overrideForward.io.in      <> overridenRFData
  overrideWriteBack.io.in    <> overrideForward.io.out
  crossbar.io.in             <> overrideWriteBack.io.out

  aluPRegs.io.in           <> crossbar.io.out(aluCrossbarIndex)
  io.out(aluCrossbarIndex) <> aluPRegs.io.out
}
