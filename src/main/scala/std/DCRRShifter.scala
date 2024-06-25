package wood.std

import chisel3._
import chisel3.util._

/*
@note DCRRShifter is a round robin shifter from a single numPorts number of ports input interface to a single numPorts number of ports output interface. Input is always assumed to be right aligned. For example: xvvv, xxvv etc. There should not be any holes like xvxv.
@param numPorts: number of input/output ports
@examples
 * Round Robin Shifter from a single 2 port input interface to 2 port output interface, with 8 bit data size.
  new DCRRShifter(UInt(8.W))(2)
 */
class DCRRShifter[T <: Data](gen: T)(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val number_of_valid_inputs = Mux(io.in.count(_.valid) === numPorts.U, 0.U, io.in.count(_.valid))

  val in_valid = Wire(Vec(numPorts, Bool()))
  in_valid := io.in.map(_.valid)

  val stall = Wire(Bool())
  stall := !(in_valid.asUInt.orR) // at least one input has to be valid

  val shamt_next = Wire(UInt(log2Ceil(numPorts).W))
  val shamt      = RegEnable(shamt_next, 0.U, !stall)

  val tmp_var = Wire(UInt((log2Ceil(numPorts) + 1).W)) // Overflows if size is inferred
  tmp_var := (number_of_valid_inputs +& shamt) // Addition (with width expansion)

  if (shamt == 0) {
    shamt_next := numPorts.U - (tmp_var % numPorts.U)
  } else {
    shamt_next := (tmp_var) % numPorts.U
  }

  val shifter = Module(new DCShifter(gen.cloneType)(numPorts))

  shifter.io.shamt := shamt
  shifter.io.in    <> io.in
  shifter.io.out   <> io.out
}
