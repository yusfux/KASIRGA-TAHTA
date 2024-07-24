package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.DecodeConfig.{OPERAND1_PC, OPERAND1_REG, OPERAND2_IMM, OPERAND2_REG}
import wood.fru.MI

object ALUOp extends ChiselEnum {
  val sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass = Value
  val values                                                 = IndexedSeq(sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass)

  def toBitpat(op: ALUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ALUOp.Type): String =
    toBitpat(op).rawString
}

class ALU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })

  val shamt = if (config.dataWidth > 1) log2Ceil(config.dataWidth) - 1 else 0 // Shift amount.

  val rawOp = Wire(UInt(ALUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, valid) = ALUOp.safe(rawOp)

  // assert(valid, "Enum state must be valid, got %d!", io.mi.bits.exOp) // https://github.com/llvm/circt/issues/6970

  val data1 = MuxCase(
    io.in.bits.rs1Data,
    Array(
      (io.in.bits.operand1 === Integer.parseInt(OPERAND1_REG, 2).U) -> io.in.bits.rs1Data,
      (io.in.bits.operand1 === Integer.parseInt(OPERAND1_PC, 2).U)  -> io.in.bits.pcIdx
    ).toIndexedSeq
  )
  val data2 = MuxCase(
    io.in.bits.rs2Data,
    Array(
      (io.in.bits.operand2 === Integer.parseInt(OPERAND2_REG, 2).U) -> io.in.bits.rs2Data,
      (io.in.bits.operand2 === Integer.parseInt(OPERAND2_IMM, 2).U) -> io.in.bits.imm
    ).toIndexedSeq
  )

  val arithmeticData1 = Mux(control === ALUOp.sub, Cat(data1, 1.U(1.W)), Cat(data1, 0.U(1.W)))
  val arithmeticData2 = Mux(control === ALUOp.sub, Cat(~data2, 1.U(1.W)), Cat(data2, 0.U(1.W)))
  val resultAdd       = arithmeticData1 + arithmeticData2

  val result = Wire(UInt(config.dataWidth.W))
  result := 0.U
  switch(control) {
    is(ALUOp.sub, ALUOp.add) { result := resultAdd(config.dataWidth, 1) }
    is(ALUOp.xor) { result := data1 ^ data2 }
    is(ALUOp.or) { result := data1 | data2 }
    is(ALUOp.and) { result := data1 & data2 }
    is(ALUOp.sll) { result := data1 << data2(shamt, 0) }
    is(ALUOp.srl) { result := data1 >> data2(shamt, 0) }
    is(ALUOp.sra) { result := (data1.asSInt >> data2(shamt, 0)).asUInt }
    is(ALUOp.slt) { result := (data1.asSInt < data2.asSInt) }
    is(ALUOp.sltu) { result := (data1 < data2).asUInt }
    is(ALUOp.pass) { result := data2 }
  }

  io.out.bits        := io.in.bits
  io.out.bits.rdData := result
  io.out.valid       := io.in.valid
  io.in.ready        := io.out.ready
}
