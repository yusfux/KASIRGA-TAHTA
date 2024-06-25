package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.MI

class WriteBackStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Vec(numPorts, Decoupled(new MI())))
    val tagBuses = Vec(numPorts, Decoupled(new Tag()))
  })

  (0 until numPorts).foreach(j => {
    io.tagBuses(j).bits.tag := io.in(j).bits.rd_tag
    io.tagBuses(j).valid    := io.in(j).valid
    io.in(j).ready          := io.tagBuses(j).ready
  })
}
