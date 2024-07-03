package wood.std

import chisel3._
import chisel3.util._

/**
  * DCArbiter is an arbiter from a single numInputs number of ports input interface to a single identical numOutputs number of ports output interface.
  *
  * @param numInputs:  number of input ports
  * @param numOutputs: number of identical output ports
  *
  * @example {{{
  *  * Arbiter from a single 1 port input interface to identical 2 port output interface, with 8 bit data size.
  *   new DCArbiter(UInt(8.W))(1, 2)
  *  * Arbiter from a single 4 port input interface to identical 2 port output interface, with 16 bit data size.
  *    Beware, there are more input ports than output ports. If all inputs are valid: io.out(0) := io.in(0) and io.out(1) := io.in(1), other inputs will get not ready.
  *   new DCArbiter(UInt(16.W))(4, 2)
  *  }}}
  */
class DCArbiter[T <: Data](gen: T)(numInputs: Int, numOutputs: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numInputs, Decoupled(gen.cloneType)))
    val out = Vec(numOutputs, Decoupled(gen.cloneType))
  })

  val arbiters = Seq.fill(numOutputs)(Module(new Arbiter(gen.cloneType, numInputs)))
  val masks    = Wire(Vec(numOutputs, UInt(numInputs.W)))

  for (j <- 0 until numOutputs) {
    for (i <- 0 until numInputs) {
      arbiters(j).io.in(i).valid := io.in(i).valid && !(if (j == 0) false.B else masks(j - 1)(i))
      arbiters(j).io.in(i).bits  := io.in(i).bits
    }
    masks(j) := (if (j == 0) {
                   Mux(
                     arbiters(j).io.out.ready,
                     UIntToOH(arbiters(j).io.chosen),
                     arbiters(j).io.out.ready
                   )
                 } else {
                   Mux(
                     arbiters(j).io.out.ready,
                     masks(j - 1) | UIntToOH(arbiters(j).io.chosen),
                     masks(j - 1)
                   )
                 })
  }

  for (i <- 0 until numInputs) {
    io.in(i).ready := masks(numOutputs - 1)(i)
  }

  for (j <- 0 until numOutputs) {
    io.out(j) <> arbiters(j).io.out
  }
}
