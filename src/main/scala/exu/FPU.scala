package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig._

// format: off
object FPUOp extends ChiselEnum {
  val fadd_s, fclass_s, fcvt_s_w, fcvt_s_wu, fcvt_w_s, fcvt_wu_s, fdiv_s,
      feq_s, fle_s, flt_s, fmadd_s, fmax_s, fmin_s, fmsub_s, fmul_s,
      fmv_w_x, fmv_x_w, fnmadd_s, fnmsub_s, fsgnj_s, fsgnjn_s, fsgnjx_s,
      fsqrt_s, fsub_s = Value

  val values = IndexedSeq(
      fadd_s, fclass_s, fcvt_s_w, fcvt_s_wu, fcvt_w_s, fcvt_wu_s, fdiv_s,
      feq_s, fle_s, flt_s, fmadd_s, fmax_s, fmin_s, fmsub_s, fmul_s,
      fmv_w_x, fmv_x_w, fnmadd_s, fnmsub_s, fsgnj_s, fsgnjn_s, fsgnjx_s,
      fsqrt_s, fsub_s)

  def toBitpat(op: FPUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def str(op: FPUOp.Type): String =
    toBitpat(op).rawString
}

object OP extends ChiselEnum {
    val FMADD, FNMSUB, ADD, MUL,     // ADDMUL operation group
        DIV, SQRT,                   // DIVSQRT operation group
        SGNJ, MINMAX, CMP, CLASSIFY, // NONCOMP operation group
        F2F, F2I, I2F, CPKAB, CPKCD, // CONV operation group
        ADDS = Value                 // ADDMUL operation group (ADDS is added here to preserve bit encoding of operations)
}


class FPU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val flush = Input(Bool())
    val out = Decoupled(new MI(config))
  })

  val rawOp = Wire(UInt(FPUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, valid) = FPUOp.safe(rawOp)

  val data1 = MuxCase(
    io.in.bits.rs1Data,
    Array(
      (io.in.bits.operand1 === Integer.parseInt(OPSRC1_IRF, 2).U) -> io.in.bits.rs1Data,
      (io.in.bits.operand1 === Integer.parseInt(OPSRC1_FRF, 2).U) -> io.in.bits.rs1Data,
    ).toIndexedSeq
  )

  val data2 = MuxCase(
    io.in.bits.rs2Data,
    Array(
      (io.in.bits.operand2 === Integer.parseInt(OPSRC2_IRF, 2).U) -> io.in.bits.rs2Data,
      (io.in.bits.operand2 === Integer.parseInt(OPSRC2_FRF, 2).U) -> io.in.bits.rs2Data,
      (io.in.bits.operand2 === Integer.parseInt(OPSRC2_IMM, 2).U) -> io.in.bits.imm,
    ).toIndexedSeq
  )

  val data3 = MuxCase(
    io.in.bits.rs3Data,
    Array(
      (io.in.bits.operand3 === Integer.parseInt(OPSRC3_FRF, 2).U) -> io.in.bits.rs3Data,
      (io.in.bits.operand3 === Integer.parseInt(OPSRC3_IMM, 2).U) -> io.in.bits.imm,
    ).toIndexedSeq
  )

  val operands = WireInit(VecInit(Seq.fill(3)(0.U(config.xlen.W))))
  val rndMode  = WireInit(0.U(3.W))
  val op       = WireInit(0.U(4.W))
  val opMod    = WireInit(false.B)
  val result   = WireInit(0.U(config.xlen.W))
  val status   = WireInit(0.U(5.W))

  //TODO: ik there is a better way to do this, just can't find it now
  operands(0) := io.in.bits.rs1Data
  operands(2) := io.in.bits.rs2Data
  operands(3) := io.in.bits.rs3Data
  rndMode     := io.in.bits.rm
  switch(control) {
    is(FPUOp.fadd_s)    { op :=  OP.ADDS.asUInt;     opMod := 0.U}
    is(FPUOp.fclass_s)  { op :=  OP.CLASSIFY.asUInt; opMod := 0.U}
    is(FPUOp.fcvt_s_w)  { op :=  OP.I2F.asUInt;      opMod := 0.U}
    is(FPUOp.fcvt_s_wu) { op :=  OP.I2F.asUInt;      opMod := 1.U}
    is(FPUOp.fcvt_w_s)  { op :=  OP.F2I.asUInt;      opMod := 0.U}
    is(FPUOp.fcvt_wu_s) { op :=  OP.F2I.asUInt;      opMod := 1.U}
    is(FPUOp.fdiv_s)    { op :=  OP.DIV.asUInt;      opMod := 0.U}
    is(FPUOp.feq_s)     { op :=  OP.CMP.asUInt;      opMod := 0.U}
    is(FPUOp.fle_s)     { op :=  OP.CMP.asUInt;      opMod := 0.U}
    is(FPUOp.flt_s)     { op :=  OP.CMP.asUInt;      opMod := 0.U}
    is(FPUOp.fmadd_s)   { op :=  OP.FMADD.asUInt;    opMod := 0.U}
    is(FPUOp.fmax_s)    { op :=  OP.MINMAX.asUInt;   opMod := 0.U}
    is(FPUOp.fmin_s)    { op :=  OP.MINMAX.asUInt;   opMod := 0.U}
    is(FPUOp.fmsub_s)   { op :=  OP.FMADD.asUInt;    opMod := 1.U}
    is(FPUOp.fmul_s)    { op :=  OP.MUL.asUInt;      opMod := 0.U}
    is(FPUOp.fmv_w_x)   { op :=  OP.ADDS.asUInt;     opMod := 0.U}
    is(FPUOp.fmv_x_w)   { op :=  OP.ADDS.asUInt;     opMod := 0.U}
    is(FPUOp.fnmadd_s)  { op :=  OP.FNMSUB.asUInt;   opMod := 1.U}
    is(FPUOp.fnmsub_s)  { op :=  OP.FNMSUB.asUInt;   opMod := 0.U}
    is(FPUOp.fsgnj_s)   { op :=  OP.SGNJ.asUInt;     opMod := 0.U}
    is(FPUOp.fsgnjn_s)  { op :=  OP.SGNJ.asUInt;     opMod := 0.U}
    is(FPUOp.fsgnjx_s)  { op :=  OP.SGNJ.asUInt;     opMod := 0.U}
    is(FPUOp.fsqrt_s)   { op :=  OP.SQRT.asUInt;     opMod := 0.U}
    is(FPUOp.fsub_s)    { op :=  OP.ADDS.asUInt;     opMod := 1.U}
  }

  // ---------------- BLACK BOX FPU FROM CVFPU ----------------
  val fpu = Module(new BlackBoxFPU(config))

  fpu.io.clk_i  := clock
  fpu.io.rst_ni := ~reset.asBool

  fpu.io.operands_i     := operands
  fpu.io.rnd_mode_i     := rndMode
  fpu.io.op_i           := op
  fpu.io.op_mod_i       := opMod
  fpu.io.src_fmt_i      := 0.U
  fpu.io.dst_fmt_i      := 0.U
  fpu.io.int_fmt_i      := 2.U
  fpu.io.vectorial_op_i := false.B
  fpu.io.tag_i          := false.B
  fpu.io.simd_mask_i    := false.B
  
  fpu.io.in_valid_i := io.in.valid
  io.in.ready       := fpu.io.in_ready_o
  fpu.io.flush_i    := io.flush
  
  result := fpu.io.result_o
  status := fpu.io.status_o
  
  io.out.valid       := fpu.io.out_valid_o
  fpu.io.out_ready_i := io.out.ready
  
  // fpu.io.busy_o := ???
  // ---------------- BLACK BOX FPU FROM CVFPU ----------------

  io.out.bits := io.in.bits
  io.out.bits.rdData := result
}
// format: on
