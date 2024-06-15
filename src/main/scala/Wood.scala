package wood

import circt.stage.ChiselStage

import chisel3._

import chisel3.util._
import chisel3.util.experimental.decode.TruthTable
import chisel3.util.experimental.decode.decoder

import opcodes.Instructions._
import alu.ALUOp

class TagBus(dataWidth: Int, tagWidth: Int) extends Bundle {
  val tag = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
}

class WriteBack(dataWidth: Int, tagWidth: Int) extends Bundle {
  val tag = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
}

class MicroOperation(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Bundle {
  val data1 = UInt(dataWidth.W)
  val data2 = UInt(dataWidth.W)
  val tag = UInt(tagWidth.W)
  val op = UInt(opWidth.W)
}

class MicroInstruction(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Bundle {
  val readtag1 = UInt(tagWidth.W)
  val readtag2 = UInt(tagWidth.W)
  val writetag = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
  val tag = UInt(tagWidth.W)
  val op = UInt(opWidth.W)
}

object GenerateVerilog {
  def apply(gen: => RawModule, path: String = ""): Unit = {
    val projectDir = System.getProperty("user.dir")

    val verilogDir = s"$projectDir/build"
    val file_path = if (path.trim().nonEmpty) {
      path
    } else {
      verilogDir + '/' + "test.sv"
    }

    val x = ChiselStage.emitSystemVerilog(
      gen = gen,
      firtoolOpts = Array(
        "--disable-all-randomization",
        "--strip-debug-info",
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--split-verilog",
        "--lowering-options=disallowLocalVariables",
        "--lower-memories",
        // "--ignore-read-enable-mem",
        "-o=" + file_path,
        "-O=release"
      )
    )
  }
}

object ExUnit extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExUnit.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExUnit.Type): String =
    toBitpat(op).rawString
}

class ExpandBits(bitVectors: List[String]) {
  // Calculate the max width
  private val maxWidth: Int = bitVectors.map(_.length).max

  // Method to extend a bit vector to the max width
  def e(bitVector: String): String = {
    bitVector.padTo(maxWidth, '0')
  }
}
// format: off

object Decode {
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
