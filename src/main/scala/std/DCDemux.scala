package wood.std

import chisel3._
import chisel3.util._

/**
  * DCDemux is a demultiplexer from a single numInputs ports input interface to numInterfaces number of output interfaces each having numInputs number of ports.
  *
  * @param numInputs: number of input ports
  * @param numInterfaces: number of interfaces each having numInputs number of ports.
  *
  * @example{{{
  * Demux from a single 1 port input interface to 2 output interfaces each having 1 port. 1to2 demux
  *  new DCDemux(UInt(8.W))(1, 2)
  *
  * Demux from a single 2 port input interface to 2 output interfaces each having 2 port. 2to4 demux
  *   2 select signals each diverting input port 0 and input port 1 respectively.
  *   Beware input port 0 can only diverted to port 0 of any interface, in this case io.out(0)(0) (interface 0 port 0) and io.out(1)(0) (interface 1 port 0). Similarly for input port 1.
  *  new DCDemux(UInt(16.W))(2, 2)
  * }}}
  */
class DCDemux[T <: Data](gen: T)(numInputs: Int, numInterfaces: Int) extends Module {
  val io = IO(new Bundle {
    val sel = Input(Vec(numInputs, UInt(log2Ceil(numInterfaces).W)))
    val in  = Flipped(Vec(numInputs, Decoupled(gen.cloneType)))
    val out = Vec(numInterfaces, Vec(numInputs, Decoupled(gen.cloneType)))
  })

  val rdys = Wire(Vec(numInputs, Vec(numInterfaces, Bool())))

  for (j <- 0 until numInterfaces) {
    for (i <- 0 until numInputs) {
      io.out(j)(i).bits := io.in(i).bits
      io.out(j)(i).valid :=
        Mux(
          io.sel(i) === j.U,
          io.in(i).valid,
          0.U
        )
    }
  }
  for (i <- 0 until numInputs) {
    for (j <- 0 until numInterfaces) {
      rdys(i)(j) := io.out(j)(i).ready
    }
    // if io.sel == 3 then OH is 1000 so, third bit is set, then choose third bool from rdys(i)
    io.in(i).ready := !io.in(i).valid | MuxCase(0.U, (0 until numInterfaces).map(j => (UIntToOH(io.sel(i))(j), rdys(i)(j))))
  }
}
