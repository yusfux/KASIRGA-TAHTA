package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig

object IMUOp extends ChiselEnum {
  val mul, mulh, mulhsu, mulhu = Value
  val values                   = IndexedSeq(mul, mulh, mulhsu, mulhu)
  def toBitpat(op: IMUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))
  def str(op: IMUOp.Type): String =
    toBitpat(op).rawString
}

class IMU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new MI(config)))
    val flush = Input(Bool())
    val out   = Decoupled(new MI(config))
  })

  val rawOp = Wire(UInt(IMUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, valid) = IMUOp.safe(rawOp)

  val data1       = Wire(UInt(33.W))
  val data2       = Wire(UInt(33.W))
  val operand1    = Wire(UInt(66.W))
  val operand2    = Wire(UInt(66.W))
  val result0_raw = Wire(UInt(66.W))
  val result0     = Wire(UInt(32.W))

  val result1  = RegInit(0.U(32.W))
  val result2  = RegInit(0.U(32.W))
  val in1      = RegInit(0.U.asTypeOf(new MI(config)))
  val in2      = RegInit(0.U.asTypeOf(new MI(config)))
  val inValid1 = RegInit(false.B)
  val inValid2 = RegInit(false.B)

  // Reset the internal state when the flush signal is asserted
  when(io.flush) {
    result1  := 0.U
    result2  := 0.U
    in1      := 0.U.asTypeOf(new MI(config))
    in2      := 0.U.asTypeOf(new MI(config))
    inValid1 := false.B
    inValid2 := false.B
  }.otherwise {
    // Update the state on the normal processing path
    result1  := RegEnable(result0, 0.U, io.out.ready)
    result2  := RegEnable(result1, 0.U, io.out.ready)
    in1      := RegEnable(io.in.bits, 0.U.asTypeOf(new MI(config)), io.out.ready)
    in2      := RegEnable(in1, 0.U.asTypeOf(new MI(config)), io.out.ready)
    inValid1 := RegEnable(io.in.valid, 0.U, io.out.ready)
    inValid2 := RegEnable(inValid1, 0.U, io.out.ready)
  }

  data1 := Cat(io.in.bits.rs1Data(31), io.in.bits.rs1Data)
  data2 := Cat(io.in.bits.rs2Data(31), io.in.bits.rs2Data)

  switch(control) {
    is(IMUOp.mul, IMUOp.mulh) {
      data1 := Cat(io.in.bits.rs1Data(31), io.in.bits.rs1Data)
      data2 := Cat(io.in.bits.rs2Data(31), io.in.bits.rs2Data)
    }
    is(IMUOp.mulhu) {
      data1 := Cat(0.U, io.in.bits.rs1Data)
      data2 := Cat(0.U, io.in.bits.rs2Data)
    }
    is(IMUOp.mulhsu) {
      data1 := Cat(io.in.bits.rs1Data(31), io.in.bits.rs1Data)
      data2 := Cat(0.U, io.in.bits.rs2Data)
    }
  }

  operand1    := Cat(Fill(34, data1(32)), data1)
  operand2    := Cat(Fill(34, data2(32)), data2)
  result0_raw := operand1 * operand2
  result0     := result0_raw(31, 0)

  switch(control) {
    is(IMUOp.mulh, IMUOp.mulhu, IMUOp.mulhsu) {
      result0 := result0_raw(63, 32)
    }
  }

  io.out.bits        := in2
  io.out.bits.rdData := result2
  io.out.valid       := inValid2
  io.in.ready        := io.out.ready
}
