package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

object IDUOp extends ChiselEnum {
  val div, divu, rem, remu = Value
  val values               = IndexedSeq(div, divu, rem, remu)

  def toBitpat(op: IDUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: IDUOp.Type): String =
    toBitpat(op).rawString
}

class IDU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })

  val divider = Module(new SRT16DividerDataModule(32))
  val mi      = RegInit(0.U.asTypeOf(new MI(config)))
  val valid   = RegInit(0.B)

  when(io.in.fire & io.out.fire) {
    mi    := io.in.bits
    valid := io.in.valid
  }.elsewhen(io.in.fire) {
    mi    := io.in.bits
    valid := io.in.valid
  }.elsewhen(io.out.fire) {
    mi    := 0.U.asTypeOf(new MI(config))
    valid := 0.B
  }.otherwise {
    mi    := mi
    valid := valid
  }

  divider.io.src(0) := io.in.bits.rs1Data
  divider.io.src(1) := io.in.bits.rs2Data

  divider.io.valid := io.in.valid
  divider.io.isW   := 0.U

  divider.io.kill_w    := 0.U
  divider.io.kill_r    := 0.U
  divider.io.out_ready := io.out.ready

  io.in.ready        := divider.io.in_ready
  io.out.valid       := divider.io.out_valid
  io.out.bits        := mi
  io.out.bits.rdData := divider.io.out_data

  val rawOp = Wire(UInt(IDUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, v) = IDUOp.safe(rawOp)

  divider.io.sign := 0.U
  switch(control) {
    is(IDUOp.div, IDUOp.rem) {
      divider.io.sign := 1.U
    }
    is(IDUOp.divu, IDUOp.remu) {
      divider.io.sign := 0.U
    }
  }

  divider.io.isHi := 0.U
  switch(control) {
    is(IDUOp.div, IDUOp.divu) {
      divider.io.isHi := 0.U
    }
    is(IDUOp.rem, IDUOp.remu) {
      divider.io.isHi := 1.U
    }
  }
}
