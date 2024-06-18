package dccrossbar

import chisel3._
import chisel3.util._
import dcarbiter.DCArbiter
import dcdemux.DCDemux

/*
@note DCCrossbar is an crossbar connecting a single numInputs number of ports input interface to numOut.length number of output interfaces each having numOut[idx] number of identical ports.
@param numInputs:  number of input ports
@param numOutputs: list of number of identical output ports for each interface
@examples
 * Crossbar connecting a single 1 port input interface to 2 interfaces one having 2 identical output ports and other having 3 identical output ports, with 8 bit data size.
  new DCArbiter(UInt(8.W))(1, List(2,3))
 */
class DCCrossbar[T <: Data](gen: T)(numInputs: Int, numOutputs: List[Int]) extends Module {
  val io = IO(new Bundle {
    val sel = Input(Vec(numInputs, UInt(log2Ceil(numOutputs.length).W)))
    val in  = Flipped(Vec(numInputs, Decoupled(gen.cloneType)))
    val out = MixedVec(numOutputs.map(length => Vec(length, Decoupled(gen.cloneType))))
  })

  val demux = Module(new DCDemux(gen.cloneType)(numInputs, numOutputs.length))

  val arbiters = Seq.tabulate(numOutputs.length) { j =>
    Module(new DCArbiter(gen.cloneType)(numInputs, numOutputs(j)))
  }

  demux.io.sel := io.sel

  for ((demux_in, j) <- demux.io.in.zipWithIndex) {
    demux_in.bits  := io.in(j).bits
    demux_in.valid := io.in(j).valid
    io.in(j).ready := demux_in.valid
  }

  for ((demux_out_interface, j) <- demux.io.out.zipWithIndex) {
    for ((demux_out_port, k) <- demux_out_interface.zipWithIndex) {
      arbiters(j).io.in(k).bits  := demux_out_port.bits
      arbiters(j).io.in(k).valid := demux_out_port.valid
      demux_out_port.ready       := arbiters(j).io.in(k).ready
    }
  }

  for ((arbiter, j) <- arbiters.zipWithIndex) {
    for ((arbiter_port, k) <- arbiter.io.out.zipWithIndex) {
      io.out(j)(k).bits  := arbiter_port.bits
      io.out(j)(k).valid := arbiter_port.valid
      arbiter_port.ready := io.out(j)(k).ready
    }
  }
}
