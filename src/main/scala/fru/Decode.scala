package wood.fru

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{EspressoMinimizer, TruthTable, decoder}
import wood.WoodConfig
import wood.exu.{ALUOp, ExEngine}
import wood.fru.Instructions._

class ExpandBits(bitVectors: List[String]) {
  // Calculate the max width
  val maxWidth: Int = bitVectors.map(_.length).max

  // Method to extend a bit vector to the max width
  def e(bitVector: String): String = {
    bitVector.padTo(maxWidth, '0')
  }
}

// format: off
object DecodeConfig {
  val typeWidth = 3
  val I_Type   = 0.U(typeWidth.W)
  val S_Type   = 1.U(typeWidth.W)
  val R_Type   = 2.U(typeWidth.W)
  val B_Type   = 3.U(typeWidth.W)
  val J_Type   = 4.U(typeWidth.W)
  val U_Type   = 5.U(typeWidth.W)
  val SYS_Type = 6.U(typeWidth.W)

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

  val WAKEUP_1 = "1"
  val WAKEUP_0 = "0"

  val defaultDecSeq: Seq[String] = Seq(
                           op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT
  )
  val defaultDec: String = defaultDecSeq.reduce(_ + _)
  val width: Int = defaultDec.length
  val subWidths: Seq[Int] = defaultDecSeq.map(_.length).reverse
  def subwidthsToBitranges(subWidths: Seq[Int]): Seq[(Int, Int)] = {
    var br: List[(Int, Int)] = Nil
    var currentIndex = 0
    for (subWidth <- subWidths) {
      val msbIndex = currentIndex + subWidth - 1
      val lsbIndex = currentIndex
      br = br :+ (msbIndex, lsbIndex)
      currentIndex += subWidth
    }
    br
  }
  val bitRanges: Seq[(Int, Int)] =subwidthsToBitranges(subWidths)

  val miTable: TruthTable =  TruthTable(Map(
   ADD              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   ADDI             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AND              -> Seq(op.e(ALUOp.toString(ALUOp.and)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   ANDI             -> Seq(op.e(ALUOp.toString(ALUOp.and)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AUIPC            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BEQ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BGE              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BGEU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BLT              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BLTU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   BNE              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   EBREAK           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   ECALL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   FENCE            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   FENCE_TSO        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   JAL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_PCIMM,WAKEUP_1,TYPE_INT),
   JALR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LB               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LBU              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LH               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LHU              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LUI              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LW               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   OR               -> Seq(op.e(ALUOp.toString(ALUOp.or))  ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   ORI              -> Seq(op.e(ALUOp.toString(ALUOp.or))  ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   PAUSE            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SB               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SBREAK           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SCALL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SH               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SLL              -> Seq(op.e(ALUOp.toString(ALUOp.sll)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SLT              -> Seq(op.e(ALUOp.toString(ALUOp.slt)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SLTI             -> Seq(op.e(ALUOp.toString(ALUOp.slt)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SLTIU            -> Seq(op.e(ALUOp.toString(ALUOp.sltu)),ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SLTU             -> Seq(op.e(ALUOp.toString(ALUOp.sltu)),ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SRA              -> Seq(op.e(ALUOp.toString(ALUOp.sra)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SRL              -> Seq(op.e(ALUOp.toString(ALUOp.srl)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SUB              -> Seq(op.e(ALUOp.toString(ALUOp.sub)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   SW               -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_0,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   XOR              -> Seq(op.e(ALUOp.toString(ALUOp.xor)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_REG  ,WAKEUP_1,TYPE_INT),
   XORI             -> Seq(op.e(ALUOp.toString(ALUOp.xor)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   AMOADD_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOAND_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOMAX_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOMAXU_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOMIN_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOMINU_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOOR_W          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOSWAP_W        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   AMOXOR_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   LR_W             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SC_W             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   ANDN             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   CLZ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   CPOP             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   CTZ              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   MAX              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   MAXU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   MIN              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   MINU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   ORC_B            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   ORN              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   ROL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   ROR              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SEXT_B           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SEXT_H           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   XNOR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   REV8_RV32        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   RORI_RV32        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   ZEXT_H_RV32      -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   BCLR             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BEXT             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BINV             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BSET             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   BCLRI_RV32       -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BEXTI_RV32       -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BINVI_RV32       -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   BSETI_RV32       -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),

   CLMUL            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   CLMULH           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   CLMULR           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   
   DIV              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   DIVU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   MUL              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   MULH             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   MULHSU           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   MULHU            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   REM              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),
   REMU             -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_INT),

   FADD_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FCLASS_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FCVT_S_W         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FCVT_S_WU        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FCVT_W_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FCVT_WU_S        -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FDIV_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FEQ_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FLE_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FLT_S            -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FLW              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMADD_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMAX_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMIN_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMSUB_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMUL_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMV_S_X          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMV_W_X          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMV_X_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FMV_X_W          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FNMADD_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FNMSUB_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSGNJ_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSGNJN_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSGNJX_S         -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSQRT_S          -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSUB_S           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),
   FSW              -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_0,TYPE_FLOAT),

   SH1ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SH2ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
   SH3ADD           -> Seq(op.e(ALUOp.toString(ALUOp.add)) ,ExEngine.toString(ExEngine.alu),WRITE_RF_1,OPERAND_IMM  ,WAKEUP_1,TYPE_INT),
  ).map({case (k, v) => k -> BitPat(s"b${v.reduce(_ + _)}")}), BitPat(s"b$defaultDec"))
  // format: on
}

class Decoder(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(32.W))
    val out = Output(new MI(config))
  })

  val instDecoder: UInt = decoder(minimizer = EspressoMinimizer, input = io.in, truthTable = DecodeConfig.miTable)

  Seq( // indexing is from right to left in the TruthTable
    io.out.isFloat  -> DecodeConfig.bitRanges(0),
    io.out.wakeup   -> DecodeConfig.bitRanges(1),
    io.out.operand  -> DecodeConfig.bitRanges(2),
    io.out.writeRf  -> DecodeConfig.bitRanges(3),
    io.out.exEngine -> DecodeConfig.bitRanges(4),
    io.out.exOp     -> DecodeConfig.bitRanges(5)
  ).foreach {
    case (outField, (msbIndex, lsbIndex)) =>
      outField := instDecoder(msbIndex, lsbIndex)
  }

  Seq( // Override if rd is 0
    io.out.writeRf -> DecodeConfig.bitRanges(2)
  ).foreach {
    case (outField, (msbIndex, lsbIndex)) =>
      outField := Mux(
        io.out.rd === 0.U,
        0.U,
        instDecoder(msbIndex, lsbIndex)
      )
  }

  io.out.rs2TagValid := MuxCase(
    0.U,
    Array(
      (io.out.operand === Integer.parseInt(DecodeConfig.OPERAND_IMM, 2).U)   -> 1.U,
      (io.out.operand === Integer.parseInt(DecodeConfig.OPERAND_PCIMM, 2).U) -> 1.U
    ).toIndexedSeq
  )

  io.out.rs1TagValid := MuxCase(
    0.U,
    Array(
      (io.out.operand === Integer.parseInt(DecodeConfig.OPERAND_PCIMM, 2).U) -> 1.U
    ).toIndexedSeq
  )

  // format: off
  io.out.rs1      := io.in(19, 15)
  io.out.rs2      := io.in(24, 20)
  io.out.rd       := io.in(11,  7)
  // format: on

  io.out.pcIdx   := DontCare
  io.out.rs1Tag  := DontCare
  io.out.rs2Tag  := DontCare
  io.out.rdTag   := DontCare
  io.out.retired := DontCare
  io.out.rs1Data := DontCare
  io.out.rs2Data := DontCare
  io.out.rdData  := DontCare
  io.out.inst    := io.in

  val inst_type = Wire(UInt(DecodeConfig.typeWidth.W))
  inst_type := MuxCase(
    DecodeConfig.I_Type,
    Array(
      (io.in(6, 2) === "b00000".U) -> DecodeConfig.I_Type, // lw
      (io.in(6, 2) === "b01000".U) -> DecodeConfig.S_Type, // sw
      (io.in(6, 2) === "b01100".U) -> DecodeConfig.R_Type, // R type
      (io.in(6, 2) === "b11000".U) -> DecodeConfig.B_Type, // B type
      (io.in(6, 2) === "b00100".U) -> DecodeConfig.I_Type, // I type ALU
      (io.in(6, 2) === "b11011".U) -> DecodeConfig.J_Type, // jal
      (io.in(6, 2) === "b00101".U) -> DecodeConfig.U_Type, // auipc
      (io.in(6, 2) === "b01101".U) -> DecodeConfig.U_Type, // lui
      (io.in(6, 2) === "b11001".U) -> DecodeConfig.I_Type, // jalr
      (io.in(6, 2) === "b11100".U) -> DecodeConfig.SYS_Type // SYSTEM instructions
    ).toIndexedSeq
  )

  // format: off
  io.out.imm := MuxCase(
   0.U(32.W),
    Array(
      (inst_type === DecodeConfig.SYS_Type) -> Cat(Fill(15, io.in(31)), io.in(19, 15), io.in(31, 20)),
      (inst_type === DecodeConfig.I_Type)   -> Cat(Fill(20, io.in(31)), io.in(31, 20)),
      (inst_type === DecodeConfig.S_Type)   -> Cat(Fill(20, io.in(31)), io.in(31, 25), io.in(11, 7)),
      (inst_type === DecodeConfig.B_Type)   -> Cat(Fill(20, io.in(31)), io.in(7), io.in(30, 25), io.in(11, 8), 0.U(1.W)),
      (inst_type === DecodeConfig.J_Type)   -> Cat(Fill(12, io.in(31)), io.in(19, 12), io.in(20), io.in(30, 21), 0.U(1.W)),
      (inst_type === DecodeConfig.U_Type)   -> Cat(io.in(31, 12), 0.U(12.W))
    ).toIndexedSeq
  )
  // format: on
}

class DecodeStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val out   = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pReg        = Module(new wood.std.DCPipelineRegisterMultiValid(new MI(config))(config.nWide, 1))
  val decoders    = Seq.fill(config.nWide)(Module(new Decoder(config)))
  val overriden   = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val extraValids = Wire(Vec(config.nWide, UInt(1.W)))

  for (j <- 0 until config.nWide) {
    extraValids(j)    := io.pcIdx.valid
    decoders(j).io.in := io.in(j).bits

    overriden(j).bits       := decoders(j).io.out
    overriden(j).bits.pcIdx := io.pcIdx.bits
    overriden(j).valid      := io.in(j).valid
    io.in(j).ready          := overriden(j).ready
  }
  pReg.io.valids(0) := extraValids
  pReg.io.in        <> overriden
  io.out            <> pReg.io.out

  pReg.io.in <> overriden
  io.out     <> pReg.io.out

  val allOutReady = Wire(Vec(config.nWide, Bool()))
  allOutReady    := pReg.io.in.map(_.ready)
  io.pcIdx.ready := allOutReady.asUInt.andR
}
