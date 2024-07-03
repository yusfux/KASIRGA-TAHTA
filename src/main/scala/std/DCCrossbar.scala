package wood.std

import chisel3._
import chisel3.util._

/**
  * DCCrossbar is an crossbar connecting a single numInputs number of ports input interface to numOut.length number of output interfaces each having numOut[idx] number of identical ports.
  *
  * @param numInputs:  number of input ports
  * @param numOutputs: list of number of identical output ports for each interface
  *
  * @example{{{
  *  * Crossbar connecting a single 1 port input interface to 2 interfaces one having 2 identical output ports and other having 3 identical output ports, with 8 bit data size.
  *   new DCCrossbar(UInt(8.W))(1, List(2,3))
  * }}}
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
    demux_in <> io.in(j)
  }
  for ((demux_out_interface, j) <- demux.io.out.zipWithIndex) {
    arbiters(j).io.in <> demux_out_interface
  }

  for ((arbiter, j) <- arbiters.zipWithIndex) {
    io.out(j) <> arbiter.io.out
  }
}
