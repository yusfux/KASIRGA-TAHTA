package wood.std

import chisel3._
import chisel3.util._
import wood.std.DCRRShifter

/**
  * DCRRQueue is a round robin queue from a single numPorts number of ports input interface to a single numPorts number of ports queue. Input is always assumed to be right aligned. For example: xvvv, xxvv etc. There should not be any holes like xvxv. For the read operations all output ports has to be ready at the same time.
  *
  * @param numPorts: number of input/output ports
  * @param queueDepth: queue depth
  * @param unique: if true queue will reject consequtive identical inputs
  *
  * @example{{{
  * 32 deep Round Robin Queue from a single 2 port input interface to 2 port output interface, with 8 bit data size.
  *  new DCRRQueue(UInt(8.W))(2,32)
  * }}}
  */
class DCRRQueue[T <: Data](gen: T)(numPorts: Int, queueDepth: Int, unique: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val previousIn   = RegInit(VecInit(Seq.fill(numPorts)(0.U.asTypeOf(gen.cloneType))))
  val is_duplicate = io.in.zip(previousIn).map { case (in, reg) => in.valid && (in.bits === reg) }
  previousIn := io.in.zipWithIndex.map { case (in, i) => Mux(in.valid && !is_duplicate(i), in.bits, previousIn(i)) }

  val queues = Seq.tabulate(numPorts) { _ =>
    Module(new Queue(gen.cloneType, queueDepth, flow = true))
  }

  val rrshifter = Module(new DCRRShifter(gen.cloneType)(numPorts))

  if (unique)
    (0 until numPorts).foreach(j => rrshifter.io.in(j).valid := io.in(j).valid && !is_duplicate(j))
  else
    (0 until numPorts).foreach(j => rrshifter.io.in(j).valid := io.in(j).valid)

  rrshifter.io.in.zip(io.in).foreach { case (rrshift_in, in) => rrshift_in.bits := in.bits }
  rrshifter.io.in.zip(io.in).foreach { case (rrshift_in, in) => in.ready := rrshift_in.ready }

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
