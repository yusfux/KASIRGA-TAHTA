package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.DecodeConfig.OPERAND_REG
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
    val mi  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })

  val shamt = if (config.dataWidth > 1) log2Ceil(config.dataWidth) - 1 else 0 // Shift amount.

  val (control, valid) = ALUOp.safe(io.mi.bits.exOp)
  // assert(valid, "Enum state must be valid, got %d!", io.mi.bits.exOp) // https://github.com/llvm/circt/issues/6970

  val data1 = Mux(io.mi.bits.operand === OPERAND_REG.toInt.U, io.mi.bits.rs1_data, io.mi.bits.imm)
  val data2 = Mux(io.mi.bits.operand === OPERAND_REG.toInt.U, io.mi.bits.rs2_data, io.mi.bits.imm)

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

  io.out.bits         := io.mi.bits
  io.out.bits.rd_data := result
  io.out.valid        := io.mi.valid
  io.mi.ready         := io.out.ready
}
