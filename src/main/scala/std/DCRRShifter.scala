package wood.std

import chisel3._
import chisel3.util._

/**
  * DCRRShifter is a round robin shifter from a single numPorts number of ports input interface to a single numPorts number of ports output interface. Input is always assumed to be right aligned. For example: xvvv, xxvv etc. There should not be any holes like xvxv.
  *
  * @param numPorts: number of input/output ports
  *
  * @example{{{
  * Round Robin Shifter from a single 2 port input interface to 2 port output interface, with 8 bit data size.
  *  new DCRRShifter(UInt(8.W))(2)
  * }}}
  */
class DCRRShifter[T <: Data](gen: T)(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val flush = Input(Bool())
    val out   = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val arbiter = Module(new DCArbiter(gen.cloneType)(numPorts, numPorts))
  val shifter = Module(new DCShifter(gen.cloneType)(numPorts))

  val shamt_next = Wire(UInt(log2Ceil(numPorts).W))
  val shamt      = RegEnable(shamt_next, 0.U, 1.B)

  val in_valid               = Wire(Vec(numPorts, Bool()))
  val stall                  = Wire(Bool())
  val tmp_var                = Wire(UInt((log2Ceil(numPorts) + 1).W)) // Overflows if size is inferred
  val number_of_valid_inputs = Mux(io.in.count(_.valid) === numPorts.U, 0.U, io.in.count(_.valid))

  in_valid := io.in.map(_.valid)
  stall    := !(in_valid.asUInt.orR) // at least one input has to be valid

  when(io.flush) {
    shamt := 0.U
  }.elsewhen(stall) {
    shamt := shamt
  }.otherwise {
    shamt := shamt_next
  }

  tmp_var := (number_of_valid_inputs +& shamt) // Addition (with width expansion)
  if (shamt == 0) {
    shamt_next := numPorts.U - (tmp_var % numPorts.U)
  } else {
    shamt_next := (tmp_var) % numPorts.U
  }

  arbiter.io.in    <> io.in
  shifter.io.shamt := shamt
  shifter.io.in    <> arbiter.io.out
  shifter.io.out   <> io.out
}
