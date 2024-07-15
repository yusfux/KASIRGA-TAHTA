package wood.std

import chisel3._
import chisel3.util._

/**
  * DCPipelineRegister is a simple pipeline register with a combinationally coupled ready signal.
  *
  * @param numValids:  number of valid inputs to pipeline stage
  *
  * @example{{{
  *  * Pipeline register for a pipeline stage which is driven by 2 inputs from previous pipeline stages.
  *   new DCPipelineRegister(new MI(config))(2)
  * }}}
  */
class DCPipelineRegister[T <: Data](gen: T)(numValids: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Flipped(Decoupled(gen.cloneType))
    val valids = Input(Vec(numValids, Bool()))
    val out    = Decoupled(gen.cloneType)
  })
  require(numValids >= 1, "Stage must have one or more inputs.")

  io.out.bits  := RegEnable(io.in.bits, 0.U.asTypeOf(gen.cloneType), io.out.ready)
  io.out.valid := RegEnable(io.in.valid, 0.B, io.out.ready)

  io.in.ready := (!io.valids.asUInt.orR) | ((io.in.valid & io.out.ready) & io.valids.asUInt.andR)
}
