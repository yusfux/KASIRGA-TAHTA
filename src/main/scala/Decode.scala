package decode
import chisel3.util.experimental.decode.decoder
import chisel3.util.experimental.decode.EspressoMinimizer

import circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.TruthTable
import chisel3.util.experimental.decode.decoder

import wood._
import opcodes.Instructions._
import alu.ALUOp

class ExpandBits(bitVectors: List[String]) {
  // Calculate the max width
  private val maxWidth: Int = bitVectors.map(_.length).max

  // Method to extend a bit vector to the max width
  def e(bitVector: String): String = {
    bitVector.padTo(maxWidth, '0')
  }
}

// format: off
object Config {

  val I_Type   = 0.U(3.W)
  val S_Type   = 1.U(3.W)
  val R_Type   = 2.U(3.W)
  val B_Type   = 3.U(3.W)
  val J_Type   = 4.U(3.W)
  val U_Type   = 5.U(3.W)
  val SYS_Type = 6.U(3.W)

  // Use the maximum size of enums as storage size. Store all operations in the same wire.
  val op = new ExpandBits(List(ALUOp.toString(ALUOp.add)))

  val X = "?"
  val N = "0"
  val Y = "1"

  val TYPE_INT = "0"
  val TYPE_FLOAT = "1"

  val OPERAND_IMM   = "00"
  val OPERAND_REG   = "01"
  val OPERAND_PC    = "10"
  val OPERAND_PCIMM = "11"

  val WRITE_RF_1 = "1"
  val WRITE_RF_0 = "0"

  val defaultDec: String = Seq(
         op.e(ALUOp.toString(ALUOp.add)),ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_REG
  ).reduce(_ + _)
  val outWidth: Int = defaultDec.length

  val miTable: TruthTable =  TruthTable(Map(
   ADD              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   ADDI             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AND              -> Seq(op.e(ALUOp.toString(ALUOp.and)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   ANDI             -> Seq(op.e(ALUOp.toString(ALUOp.and)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AUIPC            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_PCIMM,TYPE_INT),
   BEQ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   BGE              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   BGEU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   BLT              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   BLTU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   BNE              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_PCIMM,TYPE_INT),
   EBREAK           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   ECALL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   FENCE            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   FENCE_TSO        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   JAL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_PCIMM,TYPE_INT),
   JALR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LB               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LBU              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LH               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LHU              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LUI              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LW               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   OR               -> Seq(op.e(ALUOp.toString(ALUOp.or))  ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   ORI              -> Seq(op.e(ALUOp.toString(ALUOp.or))  ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   PAUSE            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SB               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_IMM  ,TYPE_INT),
   SBREAK           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SCALL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SH               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_IMM  ,TYPE_INT),
   SLL              -> Seq(op.e(ALUOp.toString(ALUOp.sll)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SLT              -> Seq(op.e(ALUOp.toString(ALUOp.slt)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SLTI             -> Seq(op.e(ALUOp.toString(ALUOp.slt)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SLTIU            -> Seq(op.e(ALUOp.toString(ALUOp.sltu)),ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SLTU             -> Seq(op.e(ALUOp.toString(ALUOp.sltu)),ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SRA              -> Seq(op.e(ALUOp.toString(ALUOp.sra)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SRL              -> Seq(op.e(ALUOp.toString(ALUOp.srl)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SUB              -> Seq(op.e(ALUOp.toString(ALUOp.sub)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   SW               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_0,OPERAND_IMM  ,TYPE_INT),
   XOR              -> Seq(op.e(ALUOp.toString(ALUOp.xor)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_REG  ,TYPE_INT),
   XORI             -> Seq(op.e(ALUOp.toString(ALUOp.xor)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),

   AMOADD_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOAND_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOMAX_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOMAXU_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOMIN_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOMINU_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOOR_W          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOSWAP_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   AMOXOR_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   LR_W             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SC_W             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),

   ANDN             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   CLZ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   CPOP             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   CTZ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MAX              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MAXU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MIN              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MINU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   ORC_B            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   ORN              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   ROL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   ROR              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SEXT_B           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SEXT_H           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   XNOR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),

   BCLR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   BEXT             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   BINV             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   BSET             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   
   CLMUL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   CLMULH           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   CLMULR           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   
   DIV              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   DIVU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MUL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MULH             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MULHSU           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   MULHU            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   REM              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   REMU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),

   FADD_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FCLASS_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FCVT_S_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FCVT_S_WU        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FCVT_W_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FCVT_WU_S        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FDIV_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FEQ_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FLE_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FLT_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FLW              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMADD_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMAX_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMIN_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMSUB_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMUL_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMV_S_X          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMV_W_X          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMV_X_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FMV_X_W          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FNMADD_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FNMSUB_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSGNJ_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSGNJN_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSGNJX_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSQRT_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSUB_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),
   FSW              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_FLOAT),

   SH1ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SH2ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
   SH3ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExUnit.toString(ExUnit.alu),WRITE_RF_1,OPERAND_IMM  ,TYPE_INT),
  ).map({case (k, v) => k -> BitPat(s"b${v.reduce(_ + _)}")}), BitPat(s"b$defaultDec"))
  // format: on
}

class DecoderMI_IO extends Bundle {
  val mi = UInt(Config.outWidth.W)
  val imm = UInt(32.W)
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
}

class DecodeStageMI_IO(pcIndexWidth: Int) extends DecoderMI_IO {
  val pc_idx = UInt(pcIndexWidth.W)
}
class Decoder() extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
    val out = Output(new DecoderMI_IO)
  })

  // format: off
  io.out.rs1 := (io.inst(19, 15))
  io.out.rs2 := (io.inst(24, 20))
  io.out.rd  := (io.inst(11,  7))
  // format: on

  val instDecoder: UInt = decoder(minimizer = EspressoMinimizer, input = io.inst, truthTable = Config.miTable)
  io.out.mi := instDecoder

  val inst_type = WireDefault(Config.I_Type)
  switch(io.inst(6, 2)) {
    is("b00000".U) { inst_type := Config.I_Type } // lw
    is("b01000".U) { inst_type := Config.S_Type } // sw
    is("b01100".U) { inst_type := Config.R_Type } // R type
    is("b11000".U) { inst_type := Config.B_Type } // B type
    is("b00100".U) { inst_type := Config.I_Type } // I type ALU
    is("b11011".U) { inst_type := Config.J_Type } // jal
    is("b00101".U) { inst_type := Config.U_Type } // auipc
    is("b01101".U) { inst_type := Config.U_Type } // lui
    is("b11001".U) { inst_type := Config.I_Type } // jalr
    is("b11100".U) { inst_type := Config.SYS_Type } // SYSTEM instructions
    // default case is already handled by WireDefault(Config.I_Type)
  }

  io.out.imm := Map(
    Config.SYS_Type -> (() => Cat(Fill(15, io.inst(31)), io.inst(19, 15), io.inst(31, 20))),
    Config.I_Type -> (() => Cat(Fill(20, io.inst(31)), io.inst(31, 20))),
    Config.S_Type -> (() => Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))),
    Config.B_Type -> (() => Cat(Fill(20, io.inst(31)), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))),
    Config.J_Type -> (() => Cat(Fill(12, io.inst(31)), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))),
    Config.U_Type -> (() => Cat(io.inst(31, 12), 0.U(12.W)))
  ).getOrElse(inst_type, () => 0.U(32.W))()
}

class DecodeStage(numOut: Int, pcIndexWidth: Int) extends Module {
  val io = IO(new Bundle {
    val inst = Flipped(Vec(numOut, Decoupled(UInt(32.W))))
    val pc_idx = Flipped(Decoupled(UInt(pcIndexWidth.W)))
    val out = Vec(numOut, Decoupled(new DecodeStageMI_IO(pcIndexWidth)))
  })

  val decoders = Seq.fill(numOut)(Module(new Decoder()))

  val out_ready = Wire(Vec(numOut, Bool()))
  val in_valid = Wire(Vec(numOut + 1, Bool()))
  out_ready := io.out.map(_.ready)
  in_valid := io.inst.map(_.valid) ++ Seq(io.pc_idx.valid)

  // all inputs have to be valid and all outputs have to be ready to not stall
  val valid = in_valid.asUInt.andR
  val ready = out_ready.asUInt.andR
  val stall = !(valid && ready)

  for (j <- 0 until numOut) {
    decoders(j).io.inst := io.inst(j).bits
    io.inst(j).ready := ready

    val decoded = Wire(new DecodeStageMI_IO(pcIndexWidth))
    decoded := decoders(j).io.asTypeOf(new DecodeStageMI_IO(pcIndexWidth))
    decoded.pc_idx := io.pc_idx.bits

    io.out(j).bits := RegEnable(decoded, stall)
    io.out(j).valid := RegEnable(io.inst(j).valid, 0.U, stall)
  }

  io.pc_idx.ready := ready
}
