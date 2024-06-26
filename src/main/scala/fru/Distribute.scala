package wood.fru

import chisel3._
import chisel3.util._
import wood.exu.ExConfig
import wood.fru.DecodeConfig.{TYPE_FLOAT, TYPE_INT}
import wood.fru.MI
import wood.std.DCCrossbar

class DistributeStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in      = Flipped(Vec(numPorts, Decoupled(new MI())))
    val toFloat = Vec(ExConfig.numPortsFloat, Decoupled(new MI()))
    val toInt   = Vec(ExConfig.numPortsInt, Decoupled(new MI()))
  })
  require((numPorts <= ExConfig.numPortsInt), "Blocking distribution is not supported.")
  require((numPorts <= ExConfig.numPortsFloat), "Blocking distribution is not supported.")

  val crossbar = Module(new DCCrossbar(new MI())(numPorts, List(ExConfig.numPortsFloat, ExConfig.numPortsInt)))

  crossbar.io.sel := io.in.map(_.bits.isFloat)

  (0 until numPorts).foreach(j => crossbar.io.in(j) <> io.in(j))

  val in_ready = Wire(Vec(numPorts, Bool()))
  val in_valid = Wire(Vec(numPorts, Bool()))

  in_ready := io.in.map(_.ready)
  in_valid := io.in.map(_.valid)

  // all inputs have to be valid and all outputs have to be ready to not stall
  val valid = in_valid.asUInt.andR
  val ready = in_ready.asUInt.andR
  val stall = !(valid && ready)

  for (j <- 0 until ExConfig.numPortsInt) {
    io.toInt(j).bits                         := RegEnable(crossbar.io.out(TYPE_INT.toInt)(j).bits, 0.U.asTypeOf(new MI()), !stall)
    io.toInt(j).valid                        := RegEnable(crossbar.io.out(TYPE_INT.toInt)(j).valid, 0.B, !stall)
    crossbar.io.out(TYPE_INT.toInt)(j).ready := io.toInt(j).ready
  }

  for (j <- 0 until ExConfig.numPortsFloat) {
    io.toFloat(j).bits                         := RegEnable(crossbar.io.out(TYPE_FLOAT.toInt)(j).bits, 0.U.asTypeOf(new MI()), !stall)
    io.toFloat(j).valid                        := RegEnable(crossbar.io.out(TYPE_FLOAT.toInt)(j).valid, 1.B, !stall)
    crossbar.io.out(TYPE_FLOAT.toInt)(j).ready := io.toFloat(j).ready

  }

}
