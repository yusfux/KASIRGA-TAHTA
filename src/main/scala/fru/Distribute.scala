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

  io.toInt   <> crossbar.io.out(0)
  io.toFloat <> crossbar.io.out(1)
}
