package wood.exu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{decoder, EspressoMinimizer, TruthTable}
import wood.WoodConfig
import wood.exu.Instructions._
import wood.exu.{ALUOp, ExEngine, IDUOp, IMUOp}
import wood.fru.PCInst
import wood.lsu.LSOp
import wood.util.WoodMIPipelineRegister

class ExpandBits(bitVectors: List[String]) {
  // Calculate the max width
  val maxWidth: Int = bitVectors.map(_.length).max

  // Method to extend a bit vector to the max width by padding on the left
  def e(bitVector: String): String = {
    "0" * (maxWidth - bitVector.length) + bitVector
  }
}
// format: off
object DecodeConfig {
  //TODO: F EXTENSION
  val typeWidth = 3
  val I_Type   = 0.U(typeWidth.W)
  val S_Type   = 1.U(typeWidth.W)
  val R_Type   = 2.U(typeWidth.W)
  val B_Type   = 3.U(typeWidth.W)
  val J_Type   = 4.U(typeWidth.W)
  val U_Type   = 5.U(typeWidth.W)
  val SYS_Type = 6.U(typeWidth.W)

  // Use the maximum size of enums as storage size. Store all operations in the same wire.
  val op = new ExpandBits(
    List(
      ALUOp.str(ALUOp.add),
      IMUOp.str(IMUOp.mul),
      IDUOp.str(IDUOp.div),
      FPUOp.str(FPUOp.fadd_s),
      LSOp.str(LSOp.lw)
    )
  )

  val X = "?"
  val N = "0"
  val Y = "1"

  val T_I = "0"
  val T_F = "1"

  val IS_BRANCH_0 = "0"
  val IS_BRANCH_1 = "1"

  val IS_JAL_0 = "0"
  val IS_JAL_1 = "1"

  val LS_T_N = "00" // nothing
  val LS_T_L = "01" // load
  val LS_T_S = "10" // store
  val LS_T_A = "11" // atom

  val OPSRC3_IMM   = "00"
  val OPSRC3_FRF   = "01"

  val OPSRC2_IRF   = "00"
  val OPSRC2_IMM   = "01"
  val OPSRC2_FRF   = "10"

  val OPSRC1_IRF   = "00"
  val OPSRC1_PC    = "01"
  val OPSRC1_X0    = "10"
  val OPSRC1_FRF   = "11"


  val W_RF_F = "10"
  val W_RF_I = "01"
  val W_RF_0 = "00"

  val WAKEUP_1 = "1"
  val WAKEUP_0 = "0"

  val defaultDecSeq: Seq[String] = Seq(
                     op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add)),       ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I
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

 // indexing is from right to left in the TruthTable
  val isFloatIdx     = 0
  val isBranchIdx    = 1
  val isJALIdx       = 2
  val lsTypeIdx      = 3
  val wakeupIdx      = 4
  val operand3Idx    = 5
  val operand2Idx    = 6
  val operand1Idx    = 7
  val writeRfIdx     = 8
  val exEngineIdx    = 9
  val exOpIdx        = 10
  val lsOpIdx        = 11

  val miTable: TruthTable =  TruthTable(Map(
   ADD        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ADDI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   AND        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.and))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ANDI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.and))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   AUIPC      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_PC, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BEQ        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.beq))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   BGE        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bge))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   BGEU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bgeu))      ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   BLT        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.blt))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   BLTU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bltu))      ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   BNE        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bne))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,T_I),
   EBREAK     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ECALL      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   FENCE      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   FENCE_TSO  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   JAL        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.jal))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_PC, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_1,IS_BRANCH_0,T_I),
   JALR       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.jalr))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_1,IS_BRANCH_0,T_I),
   LB         -> Seq(op.e(LSOp.str(LSOp.lb)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,T_I),
   LBU        -> Seq(op.e(LSOp.str(LSOp.lbu)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,T_I),
   LH         -> Seq(op.e(LSOp.str(LSOp.lh)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,T_I),
   LHU        -> Seq(op.e(LSOp.str(LSOp.lhu)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,T_I),
   LUI        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_X0, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   LW         -> Seq(op.e(LSOp.str(LSOp.lw)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,T_I),
   OR         -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.or))        ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ORI        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.or))        ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   PAUSE      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SB         -> Seq(op.e(LSOp.str(LSOp.sb)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,T_I),
   SBREAK     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SCALL      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SH         -> Seq(op.e(LSOp.str(LSOp.sh)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,T_I),
   SLL        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sll))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SLT        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.slt))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SLTI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.slt))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SLTIU      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sltu))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SLTU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sltu))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SRA        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sra))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SRL        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.srl))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SUB        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sub))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SW         -> Seq(op.e(LSOp.str(LSOp.sw)),      op.e(ALUOp.str(ALUOp.add))       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,T_I),
   XOR        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.xor))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   XORI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.xor))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

  // i32
   SLLI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sll))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SRAI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sra))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SRLI       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.srl))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   AMOADD_W   -> Seq(op.e(LSOp.str(LSOp.amoadd)),  op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOAND_W   -> Seq(op.e(LSOp.str(LSOp.amoand)),  op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOMAX_W   -> Seq(op.e(LSOp.str(LSOp.amomax)),  op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOMAXU_W  -> Seq(op.e(LSOp.str(LSOp.amomaxu)), op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOMIN_W   -> Seq(op.e(LSOp.str(LSOp.amomin)),  op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOMINU_W  -> Seq(op.e(LSOp.str(LSOp.amominu)), op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOOR_W    -> Seq(op.e(LSOp.str(LSOp.amoor)),   op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOSWAP_W  -> Seq(op.e(LSOp.str(LSOp.amoswap)), op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   AMOXOR_W   -> Seq(op.e(LSOp.str(LSOp.amoxor)),  op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   LR_W       -> Seq(op.e(LSOp.str(LSOp.lrw)),     op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),
   SC_W       -> Seq(op.e(LSOp.str(LSOp.scw)),     op.e(ALUOp.str(ALUOp.pass))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,T_I),

   ANDN       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.andn))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   CLZ        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.clz))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   CPOP       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.cpop))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   CTZ        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.ctz))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MAX        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.max))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MAXU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.maxu))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MIN        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.min))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MINU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.minu))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ORC_B      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.orc_b))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ORN        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.orn))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ROL        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.rol))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ROR        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.ror))       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SEXT_B     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sext_b))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SEXT_H     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sext_h))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   XNOR       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.xnor))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   REV8_RV32  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.rev8))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   RORI_RV32  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.rori))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   ZEXT_H_RV32-> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.zext_h))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   BCLR       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bclr))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BEXT       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bext))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BINV       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.binv))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BSET       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bset))      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   BCLRI_RV32 -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bclri))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BEXTI_RV32 -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bexti))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BINVI_RV32 -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.binvi))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   BSETI_RV32 -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.bseti))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   CLMUL      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.clmul))     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   CLMULH     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.clmulh))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   CLMULR     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.clmulr))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   DIV        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IDUOp.str(IDUOp.div))       ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   DIVU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IDUOp.str(IDUOp.divu))      ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MUL        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IMUOp.str(IMUOp.mul))       ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MULH       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IMUOp.str(IMUOp.mulh))      ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MULHSU     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IMUOp.str(IMUOp.mulhsu))    ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   MULHU      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IMUOp.str(IMUOp.mulhu))     ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   REM        -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IDUOp.str(IDUOp.rem))       ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   REMU       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(IDUOp.str(IDUOp.remu))      ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),

   FADD_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fadd_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FCLASS_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fclass_s))  ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FCVT_S_W   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fcvt_s_w))  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FCVT_S_WU  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fcvt_s_wu)) ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FCVT_W_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fcvt_w_s))  ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FCVT_WU_S  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fcvt_wu_s)) ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FDIV_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fdiv_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FEQ_S      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.feq_s))     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FLE_S      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fle_s))     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FLT_S      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.flt_s))     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
  //TODO: LOAD  FLW
   FMADD_S    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmadd_s))   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMAX_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmax_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMIN_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmin_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMSUB_S    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmsub_s))   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMUL_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmul_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMV_W_X    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmv_w_x))   ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FMV_X_W    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fmv_x_w))   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FNMADD_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fnmadd_s))  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FNMSUB_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fnmsub_s))  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FSGNJ_S    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fsgnj_s))   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FSGNJN_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fsgnjn_s))  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FSGNJX_S   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fsgnjx_s))  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FSQRT_S    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fsqrt_s))   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
   FSUB_S     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(FPUOp.str(FPUOp.fsub_s))    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_F),
  //TODO: LOAD STORE FSW

   SH1ADD     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sh1add))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SH2ADD     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sh2add))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   SH3ADD     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(ALUOp.str(ALUOp.sh3add))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
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
    io.out.isFloat  -> DecodeConfig.bitRanges(DecodeConfig.isFloatIdx),
    io.out.isBranch -> DecodeConfig.bitRanges(DecodeConfig.isBranchIdx),
    io.out.isJAL    -> DecodeConfig.bitRanges(DecodeConfig.isJALIdx),
    io.out.lsType   -> DecodeConfig.bitRanges(DecodeConfig.lsTypeIdx),
    io.out.wakeup   -> DecodeConfig.bitRanges(DecodeConfig.wakeupIdx),
    io.out.opsrc3   -> DecodeConfig.bitRanges(DecodeConfig.operand3Idx),
    io.out.opsrc2   -> DecodeConfig.bitRanges(DecodeConfig.operand2Idx),
    io.out.opsrc1   -> DecodeConfig.bitRanges(DecodeConfig.operand1Idx),
    io.out.writeRf  -> DecodeConfig.bitRanges(DecodeConfig.writeRfIdx),
    io.out.exEngine -> DecodeConfig.bitRanges(DecodeConfig.exEngineIdx),
    io.out.exOp     -> DecodeConfig.bitRanges(DecodeConfig.exOpIdx),
    io.out.lsOp     -> DecodeConfig.bitRanges(DecodeConfig.lsOpIdx)
  ).foreach {
    case (outField, (msbIndex, lsbIndex)) =>
      outField := instDecoder(msbIndex, lsbIndex)
  }

  Seq( // Override if rd is 0 and the dest register is int register
    io.out.writeRf -> DecodeConfig.bitRanges(DecodeConfig.writeRfIdx)
  ).foreach {
    case (outField, (msbIndex, lsbIndex)) =>
      outField := Mux(
        io.out.rd === 0.U && io.out.isFloat === Integer.parseInt(DecodeConfig.T_I, 2).U,
        Integer.parseInt(DecodeConfig.W_RF_0, 2).U,
        instDecoder(msbIndex, lsbIndex)
      )
  }

  io.out.rs3TagReady := MuxCase(
    0.U,
    Array(
      ((io.out.opsrc3 === Integer.parseInt(DecodeConfig.OPSRC3_IMM, 2).U)) -> 1.U
    ).toIndexedSeq
  )

  io.out.rs2TagReady := MuxCase(
    0.U,
    Array(
      ((io.out.opsrc2 === Integer.parseInt(DecodeConfig.OPSRC2_IMM, 2).U))                        -> 1.U,
      ((io.out.opsrc2 === Integer.parseInt(DecodeConfig.OPSRC2_IRF, 2).U) & (io.out.rs2 === 0.U)) -> 1.U
    ).toIndexedSeq
  )

  io.out.rs1TagReady := MuxCase(
    0.U,
    Array(
      ((io.out.opsrc1 === Integer.parseInt(DecodeConfig.OPSRC1_X0, 2).U))                         -> 1.U,
      ((io.out.opsrc1 === Integer.parseInt(DecodeConfig.OPSRC1_IRF, 2).U) & (io.out.rs1 === 0.U)) -> 1.U,
      (io.out.opsrc1 === Integer.parseInt(DecodeConfig.OPSRC1_PC, 2).U)                           -> 1.U
    ).toIndexedSeq
  )

  // format: off
  io.out.rs1      := io.in(19, 15)
  io.out.rs2      := io.in(24, 20)
  io.out.rs3      := io.in(31, 27)
  io.out.rd       := io.in(11,  7)
  io.out.rm       := io.in(14, 12)
  // format: on

  io.out.exception    := 0.B
  io.out.taken        := 0.B
  io.out.retired      := 0.B
  io.out.operandReady := 0.B

  io.out.targetPC := DontCare
  io.out.pc       := DontCare
  io.out.rs1Tag   := DontCare
  io.out.rs2Tag   := DontCare
  io.out.rs3Tag   := DontCare
  io.out.rdTag    := DontCare
  io.out.flushed  := DontCare
  io.out.arfTag   := DontCare
  io.out.arfValid := DontCare
  io.out.rs1Data  := DontCare
  io.out.rs2Data  := DontCare
  io.out.rs3Data  := DontCare
  io.out.rdData   := DontCare
  io.out.inst     := io.in

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
    val in    = Flipped(Vec(config.nWide, Decoupled(new PCInst(config))))
    val flush = Input(Bool())
    val out   = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pRegs    = Seq.fill(config.nWide)(Module(new WoodMIPipelineRegister(config, 1)))
  val decoders = Seq.fill(config.nWide)(Module(new Decoder(config)))
  val self     = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val allReady = io.out.map(_.ready).reduce(_ && _)

  for (j <- 0 until config.nWide) {
    decoders(j).io.in := io.in(j).bits.inst

    self(j).bits    := decoders(j).io.out
    self(j).bits.pc := io.in(j).bits.pc
    self(j).valid   := io.in(j).valid

    pRegs(j).io.valids(0) := io.in(j).valid

    pRegs(j).io.flush      := io.flush
    pRegs(j).io.setflushed := io.flush

    pRegs(j).io.in <> self(j)
    io.out(j)      <> pRegs(j).io.out
    io.in(j).ready := allReady
  }
}
