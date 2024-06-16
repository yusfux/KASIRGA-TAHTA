package dcdemux

import chisel3._
import chisel3.util._

import wood._

/*
@note DCDemux is numIn to numIn * numRepl demux.
@param numIn: number of inputs
@param numRepl: number of replications.
@examples
 * Basic 1to2, 8 bit demux
  new DCDemux(UInt(8.W))(1, 2)
 * 2 to 4, 16 bit demux. Combination of 2 1to4 demuxes.
  new DCDemux(UInt(16.W))(2, 4)
 */
class DCDemux[T <: Data](gen: T)(numIn: Int, numRepl: Int) extends Module {
  val io = IO(new Bundle {
    val sel = Input(Vec(numIn, UInt(log2Ceil(numRepl).W)))
    val in = Flipped(Vec(numIn, Decoupled(gen.cloneType)))
    val out = Vec(numRepl, Vec(numIn, Decoupled(gen.cloneType)))
  })

  val rdys = Wire(Vec(numIn, Vec(numRepl, Bool())))

  for (j <- 0 until numRepl) {
    for (i <- 0 until numIn) {
      io.out(j)(i).bits := io.in(i).bits
      io.out(j)(i).valid :=
        Mux(
          io.sel(i) === j.U,
          io.in(i).valid,
          0.U
        )
    }
  }
  for (i <- 0 until numIn) {
    for (j <- 0 until numRepl) {
      rdys(i)(j) := io.out(j)(i).ready
    }
    // if io.sel == 3 then OH is 1000 so, third bit is set, then choose third bool from rdys(i)
    io.in(i).ready := MuxCase(0.U, (0 until numRepl).map(j => (UIntToOH(io.sel(i))(j), rdys(i)(j))))
  }
}
object DCDemuxMain extends App {
  // GenerateVerilog(new DCDemux(UInt(8.W))(3, 2))
  GenerateVerilog(new DCDemux(UInt(8.W))(1, 2))
}
