package wood.fru

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.DCRRShifter

class MIStage(numPorts: Int, queueDepth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(new MI())))
    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val queues = Seq.tabulate(numPorts) { j =>
    Module(new Queue(new MI(), queueDepth))
  }

  val rrshifter = Module(new DCRRShifter(new MI())(numPorts))

  (0 until numPorts).foreach(j => rrshifter.io.in(j) <> io.in(j))
  (0 until numPorts).foreach(j => queues(j).io.enq <> rrshifter.io.out(j))
  (0 until numPorts).foreach(j => queues(j).io.deq <> io.out(j))
}
