package wood.std

import chisel3._
import chisel3.util._

/**
  * DCPipelineRegister is a simple pipeline register with combinationally coupled ready signal.
  *
  * @param numPorts:  number of ports
  *
  * @example{{{
  *  * Pipeline register for 2 port MI.
  *   new DCPipelineRegister(new MI(config))(2)
  * }}}
  */
class DCPipelineRegister[T <: Data](gen: T)(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val stall = Wire(Vec(numPorts, Bool()))
  stall := io.out.map(!_.ready)

  (0 until numPorts).foreach(j => {
    io.out(j).bits  := RegEnable(io.in(j).bits, 0.U.asTypeOf(gen.cloneType), !stall(j).asBool)
    io.out(j).valid := RegEnable(io.in(j).valid, 0.B, !stall(j).asBool)
    io.in(j).ready  := io.out(j).ready | !io.in(j).valid
  })
}
