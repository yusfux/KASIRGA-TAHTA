package wood.std

import chisel3._
import chisel3.util._
import wood.std.DCRRShifter

class DCRRQueue[T <: Data](gen: T)(numPorts: Int, queueDepth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val queues = Seq.tabulate(numPorts) { j =>
    Module(new Queue(gen.cloneType, queueDepth))
  }

  val rrshifter = Module(new DCRRShifter(gen.cloneType)(numPorts))

  (0 until numPorts).foreach(j => rrshifter.io.in(j) <> io.in(j))
  (0 until numPorts).foreach(j => queues(j).io.enq <> rrshifter.io.out(j))
  (0 until numPorts).foreach(j => queues(j).io.deq <> io.out(j))
}
