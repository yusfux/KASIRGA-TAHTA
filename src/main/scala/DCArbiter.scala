package dcarbiter

import chisel3._
import chisel3.util._

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
