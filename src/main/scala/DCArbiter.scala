package dcarbiter

import chisel3._
import chisel3.util._

/*
@note DCArbiter is an arbiter from a single numInputs number of ports input interface to a single identical numOutputs number of ports output interface.
@param numInputs:  number of input ports
@param numOutputs: number of identical output ports
@examples
 * Arbiter from a single 1 port input interface to identical 2 port output interface, with 8 bit data size.
  new DCArbiter(UInt(8.W))(1, 2)
 * Arbiter from a single 4 port input interface to identical 2 port output interface, with 16 bit data size.
   Beware, there are more input ports than output ports. If all inputs are valid: io.out(0) := io.in(0) and io.out(1) := io.in(1), other inputs will get not ready.
  new DCArbiter(UInt(16.W))(4, 2)
 */
class DCArbiter[T <: Data](gen: T)(numIn: Int, numOut: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numIn, Decoupled(gen.cloneType)))
    val out = Vec(numOut, Decoupled(gen.cloneType))
  })

  val arbiters = Seq.fill(numOut)(Module(new Arbiter(gen.cloneType, numIn)))
  val masks    = Wire(Vec(numOut, UInt(numIn.W)))

  for (j <- 0 until numOut) {
    for (i <- 0 until numIn) {
      arbiters(j).io.in(i).valid := io.in(i).valid && !(if (j == 0) false.B else masks(j - 1)(i))
      arbiters(j).io.in(i).bits  := io.in(i).bits
    }
    masks(j) := (if (j == 0) {
                   Mux(
                     arbiters(j).io.out.ready,
                     UIntToOH(arbiters(j).io.chosen),
                     0.U
                   )
                 } else {
                   Mux(
                     arbiters(j).io.out.ready,
                     masks(j - 1) | UIntToOH(arbiters(j).io.chosen),
                     masks(j - 1)
                   )
                 })
  }

  for (i <- 0 until numIn) {
    io.in(i).ready := masks(numOut - 1)(i)
  }

  for (j <- 0 until numOut) {
    io.out(j) <> arbiters(j).io.out
  }
}
