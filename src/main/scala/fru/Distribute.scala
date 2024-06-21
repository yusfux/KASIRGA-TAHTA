package wood.fru

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.DCCrossbar

class DistributeStage(numInputs: Int, numOutInt: Int, numOutFloat: Int) extends Module {
  val io = IO(new Bundle {
    val in      = Flipped(Vec(numInputs, Decoupled(new MI())))
    val sel     = Input(Vec(numInputs, UInt(1.W)))
    val toFloat = Vec(numOutFloat, Decoupled(new MI()))
    val toInt   = Vec(numOutInt, Decoupled(new MI()))
  })
  require((numInputs <= numOutInt), "Blocking distribution is not supported.")
  require((numInputs <= numOutFloat), "Blocking distribution is not supported.")

  val crossbar = Module(new DCCrossbar(new MI())(numInputs, List(numOutFloat, numOutInt)))

  crossbar.io.sel := io.sel

  (0 until numInputs).foreach(j => crossbar.io.in(j) <> io.in(j))

  val in_ready = Wire(Vec(numInputs, Bool()))
  val in_valid = Wire(Vec(numInputs, Bool()))

  in_ready := io.in.map(_.ready)
  in_valid := io.in.map(_.valid)

  // all inputs have to be valid and all outputs have to be ready to not stall
  val valid = in_valid.asUInt.andR
  val ready = in_ready.asUInt.andR
  val stall = !(valid && ready)

  for (j <- 0 until numOutInt) {
    io.toInt(j).bits            := RegEnable(crossbar.io.out(0)(j).bits, 0.U.asTypeOf(new MI()), !stall)
    io.toInt(j).valid           := RegEnable(crossbar.io.out(0)(j).valid, 0.B, !stall)
    crossbar.io.out(0)(j).ready := io.toInt(j).ready
  }

  for (j <- 0 until numOutFloat) {
    io.toFloat(j).bits          := RegEnable(crossbar.io.out(1)(j).bits, 0.U.asTypeOf(new MI()), !stall)
    io.toFloat(j).valid         := RegEnable(crossbar.io.out(1)(j).valid, 0.B, !stall)
    crossbar.io.out(1)(j).ready := io.toFloat(j).ready
  }

}
