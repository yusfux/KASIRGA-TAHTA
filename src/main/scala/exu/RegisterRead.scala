package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RegisterReadStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Vec(numPorts, Decoupled(new MI())))
    val tagBuses = Flipped(Vec(numPorts, Decoupled(new TagBus())))
    val stall    = Input(UInt(1.W))

    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val prf = Module(
    new DecoupledBlockRAM(UInt(ExConfig.dataWidth.W))(
      BlockRAMParams(ExConfig.prfDepth, numPorts * 2, numPorts)
    )
  )

  (0 until numPorts).foreach(j => {
    prf.io.rip(j).bits.addr            := io.in(j).bits.rs1_tag
    prf.io.rip(j).valid                := io.in(j).valid
    io.in(j).ready                     := prf.io.rip(j).ready
    prf.io.rip(j + numPorts).bits.addr := io.in(j).bits.rs2_tag
    prf.io.rip(j + numPorts).valid     := io.in(j).valid
    io.in(j).ready                     := prf.io.rip(j).ready & prf.io.rip(j + numPorts).ready

    io.out(j).bits          := io.in(j).bits
    io.out(j).bits.rs1_data := prf.io.rop(j).bits.data
    io.out(j).bits.rs2_data := prf.io.rop(j + numPorts).bits.data
    io.out(j).valid         := prf.io.rop(j + numPorts).valid

    prf.io.rop(j).ready            := 1.U // TODO
    prf.io.rop(j + numPorts).ready := 1.U // TODO

    prf.io.wp(j).bits.addr   := io.tagBuses(j).bits.tag
    prf.io.wp(j).bits.enable := io.tagBuses(j).valid
    prf.io.wp(j).bits.data   := 1.U
    prf.io.wp(j).valid       := io.tagBuses(j).valid
    io.tagBuses(j).ready     := prf.io.wp(j).ready
  })
}
