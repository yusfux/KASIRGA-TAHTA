package wood.std

import chisel3._
import chisel3.util._
import wood.std.DCRRShifter

/**
  * DCRRQueue is a round robin queue from a single numPorts number of ports input interface to a single numPorts number of ports queue. Input is always assumed to be right aligned. For example: xvvv, xxvv etc. There should not be any holes like xvxv. For the read operations all output ports has to be ready at the same time.
  *
  * @param numPorts: number of input/output ports
  * @param queueDepth: queue depth
  *
  * @example{{{
  * 32 deep Round Robin Queue from a single 2 port input interface to 2 port output interface, with 8 bit data size.
  *  new DCRRQueue(UInt(8.W))(2,32)
  * }}}
  */
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

  val out_ready = Wire(Vec(numPorts, Bool()))
  out_ready := io.out.map(_.ready)
  val ready = out_ready.asUInt.andR // all ports has to be ready at the same time. Otherwise Round Robin is broken.

  (0 until numPorts).foreach(j => {
    queues(j).io.deq.ready := ready
    io.out(j).valid        := queues(j).io.deq.valid
    io.out(j).bits         := queues(j).io.deq.bits
  })
}
