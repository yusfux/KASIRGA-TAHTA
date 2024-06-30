package wood.std

import chisel3._
import chisel3.std.BarrelShifter
import chisel3.util._

/**
  * DCShifter is a shifter from a single numPorts number of ports input interface to a single numPorts number of ports output interface.
  *
  * @param numPorts: number of input/output ports
  *
  * @example{{{
  * Shifter from a single 2 port input interface to 2 port output interface, with 8 bit data size.
  *  new DCShifter(UInt(8.W))(2)
  * }}}
  */
class DCShifter[T <: Data](gen: T)(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val shamt = Input(UInt(log2Ceil(numPorts).W))
    val out   = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val in_valids  = Wire(Vec(numPorts, Bool()))
  val out_readys = Wire(Vec(numPorts, Bool()))
  val in_bits    = Wire(Vec(numPorts, gen.cloneType))

  in_valids  := io.in.map(_.valid)
  out_readys := io.out.map(_.ready)
  in_bits    := io.in.map(_.bits)

  val shifted_in_valids  = Wire(Vec(numPorts, Bool()))
  val shifted_out_readys = Wire(Vec(numPorts, Bool()))
  val shifted_in_bits    = Wire(Vec(numPorts, gen.cloneType))

  shifted_in_valids  := BarrelShifter.rightRotate(in_valids, io.shamt)
  shifted_out_readys := BarrelShifter.leftRotate(out_readys, io.shamt)
  shifted_in_bits    := BarrelShifter.rightRotate(in_bits, io.shamt)

  (0 until numPorts).foreach(j => io.out(j).valid := shifted_in_valids(j))
  (0 until numPorts).foreach(j => io.in(j).ready := shifted_out_readys(j))
  (0 until numPorts).foreach(j => io.out(j).bits := shifted_in_bits(j))
}
