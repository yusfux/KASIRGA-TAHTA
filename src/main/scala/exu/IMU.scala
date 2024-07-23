package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

object IMUOp extends ChiselEnum {
  val mul, mulh, mulhsu, mulhu = Value
  val values                   = IndexedSeq(mul, mulh, mulhsu, mulhu)

  def toBitpat(op: IMUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: IMUOp.Type): String =
    toBitpat(op).rawString
}

class IMU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })

  println("size:  ", io.in.bits.exOp)
  // val (control, valid) = IMUOp.safe(io.in.bits.exOp)
  val (control, valid) = IMUOp.safe(io.in.bits.exOp.asTypeOf(IMUOp.mul.litValue.U))

  val data1       = Wire(UInt(33.W)) // TODO: hard coded bit lengths
  val data2       = Wire(UInt(33.W))
  val operand1    = Wire(UInt(66.W))
  val operand2    = Wire(UInt(66.W))
  val result0_raw = Wire(UInt(66.W))
  val result0     = Wire(UInt(32.W))

  val result1 = RegEnable(result0, 0.U, io.out.ready)
  val result2 = RegEnable(result1, 0.U, io.out.ready)

  val in1 = RegEnable(io.in.bits, 0.U.asTypeOf(new MI(config)), io.out.ready)
  val in2 = RegEnable(in1, 0.U.asTypeOf(new MI(config)), io.out.ready)

  val inValid1 = RegEnable(io.in.valid, 0.U, io.out.ready)
  val inValid2 = RegEnable(inValid1, 0.U, io.out.ready)

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

  operand1 := Cat(Fill(34, data1(32)), data1)
  operand2 := Cat(Fill(34, data2(32)), data2)

  result0_raw := operand1 * operand2

  result0 := result0_raw(63, 32)
  switch(control) {
    is(IMUOp.mul) {
      result0 := result0_raw(31, 0)
    }
  }

  io.out.bits        := in2
  io.out.bits.rdData := result2
  io.out.valid       := inValid2
  io.in.ready        := io.out.ready
}
