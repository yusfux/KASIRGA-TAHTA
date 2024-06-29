package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RegisterReadStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writeBackBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val stall        = Input(UInt(1.W))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val prf = Module(
    new DecoupledBlockRAM(UInt(config.dataWidth.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide)
    )
  )

  (0 until config.nWide).foreach(j => {
    prf.io.rip(j).bits.addr                := io.in(j).bits.rs1_tag
    prf.io.rip(j).valid                    := io.in(j).valid
    io.in(j).ready                         := prf.io.rip(j).ready
    prf.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2_tag
    prf.io.rip(j + config.nWide).valid     := io.in(j).valid
    io.in(j).ready                         := prf.io.rip(j).ready & prf.io.rip(j + config.nWide).ready

    io.out(j).bits          := io.in(j).bits
    io.out(j).bits.rs1_data := prf.io.rop(j).bits.data
    io.out(j).bits.rs2_data := prf.io.rop(j + config.nWide).bits.data
    io.out(j).valid         := prf.io.rop(j + config.nWide).valid

    prf.io.rop(j).ready                := io.out(j).ready
    prf.io.rop(j + config.nWide).ready := io.out(j).ready

    prf.io.wp(j).bits.addr   := io.writeBackBus(j).bits.tag
    prf.io.wp(j).bits.enable := io.writeBackBus(j).valid
    prf.io.wp(j).bits.data   := 1.U
    prf.io.wp(j).valid       := io.writeBackBus(j).valid
    io.writeBackBus(j).ready := prf.io.wp(j).ready
  })
}
