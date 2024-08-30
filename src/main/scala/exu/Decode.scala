package wood.exu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{decoder, EspressoMinimizer, TruthTable}
import wood.WoodConfig
import wood.exu.Instructions._
import wood.exu.{ALUOp, CSROp, ExEngine, IDUOp, IMUOp}
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
      LSOp.str(LSOp.lw),
      CSROp.str(CSROp.csrrc)
    )
  )

  val X = "?"
  val N = "0"
  val Y = "1"

  val T_I = "0"
  val T_F = "1"

  val IS_BRANCH_0 = "0"
  val IS_BRANCH_1 = "1"

  val IS_CSR_0 = "0"
  val IS_CSR_1 = "1"

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

  val aluOp = (j: ALUOp.Type) => op.e(ALUOp.str(j))
  val fpuOp = (j: FPUOp.Type) => op.e(FPUOp.str(j))
  val iduOp = (j: IDUOp.Type) => op.e(IDUOp.str(j))
  val imuOp = (j: IMUOp.Type) => op.e(IMUOp.str(j))
  val lsOp = (j: LSOp.Type) => op.e(LSOp.str(j))
  val csrOp = (j: CSROp.Type) => op.e(CSROp.str(j))

  val defaultDecSeq: Seq[String] = Seq(
                     lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I
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
  val isCSRIdx       = 1
  val isBranchIdx    = 2
  val isJALIdx       = 3
  val lsTypeIdx      = 4
  val wakeupIdx      = 5
  val operand3Idx    = 6
  val operand2Idx    = 7
  val operand1Idx    = 8
  val writeRfIdx     = 9
  val exEngineIdx    = 10
  val exOpIdx        = 11
  val lsOpIdx        = 12


  val miTable: TruthTable =  TruthTable(Map(
   ADD        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ADDI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AND        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.and)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ANDI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.and)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AUIPC      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_PC, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BEQ        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.beq)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   BGE        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bge)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   BGEU       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bgeu)      ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   BLT        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.blt)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   BLTU       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bltu)      ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   BNE        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bne)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_1,IS_CSR_0,T_I),
   EBREAK     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ECALL      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   FENCE      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   FENCE_TSO  -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   JAL        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.jal)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_PC, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_1,IS_BRANCH_0,IS_CSR_0,T_I),
   JALR       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.jalr)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_1,IS_BRANCH_0,IS_CSR_0,T_I),
   LB         -> Seq(lsOp(LSOp.lb),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LBU        -> Seq(lsOp(LSOp.lbu),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LH         -> Seq(lsOp(LSOp.lh),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LHU        -> Seq(lsOp(LSOp.lhu),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LUI        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_X0, OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LW         -> Seq(lsOp(LSOp.lw),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_L,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   OR         -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.or)        ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ORI        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.or)        ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   PAUSE      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SB         -> Seq(lsOp(LSOp.sb),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SBREAK     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SCALL      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SH         -> Seq(lsOp(LSOp.sh),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SLL        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sll)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SLT        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.slt)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SLTI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.slt)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SLTIU      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sltu)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SLTU       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sltu)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SRA        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sra)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SRL        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.srl)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SUB        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sub)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SW         -> Seq(lsOp(LSOp.sw),      aluOp(ALUOp.add)       ,ExEngine.str(ExEngine.alu),W_RF_0,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_S,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   XOR        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.xor)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   XORI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.xor)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

  // i32
   SLLI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sll)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SRAI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sra)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SRLI       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.srl)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   AMOADD_W   -> Seq(lsOp(LSOp.amoadd),  aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOAND_W   -> Seq(lsOp(LSOp.amoand),  aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOMAX_W   -> Seq(lsOp(LSOp.amomax),  aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOMAXU_W  -> Seq(lsOp(LSOp.amomaxu), aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOMIN_W   -> Seq(lsOp(LSOp.amomin),  aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOMINU_W  -> Seq(lsOp(LSOp.amominu), aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOOR_W    -> Seq(lsOp(LSOp.amoor),   aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOSWAP_W  -> Seq(lsOp(LSOp.amoswap), aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   AMOXOR_W   -> Seq(lsOp(LSOp.amoxor),  aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   LR_W       -> Seq(lsOp(LSOp.lrw),     aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SC_W       -> Seq(lsOp(LSOp.scw),     aluOp(ALUOp.pass)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_A,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   ANDN       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.andn)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   CLZ        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.clz)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   CPOP       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.cpop)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   CTZ        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.ctz)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MAX        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.max)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MAXU       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.maxu)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MIN        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.min)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MINU       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.minu)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ORC_B      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.orc_b)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ORN        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.orn)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ROL        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.rol)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ROR        -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.ror)       ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SEXT_B     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sext_b)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SEXT_H     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sext_h)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   XNOR       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.xnor)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   REV8_RV32  -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.rev8)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   RORI_RV32  -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.rori)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   ZEXT_H_RV32-> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.zext_h)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   BCLR       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bclr)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BEXT       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bext)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BINV       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.binv)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BSET       -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bset)      ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   BCLRI_RV32 -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bclri)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BEXTI_RV32 -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bexti)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BINVI_RV32 -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.binvi)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   BSETI_RV32 -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.bseti)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   CLMUL      -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.clmul)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   CLMULH     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.clmulh)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   CLMULR     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.clmulr)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   DIV        -> Seq(lsOp(LSOp.nop),     iduOp(IDUOp.div)       ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   DIVU       -> Seq(lsOp(LSOp.nop),     iduOp(IDUOp.divu)      ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MUL        -> Seq(lsOp(LSOp.nop),     imuOp(IMUOp.mul)       ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MULH       -> Seq(lsOp(LSOp.nop),     imuOp(IMUOp.mulh)      ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MULHSU     -> Seq(lsOp(LSOp.nop),     imuOp(IMUOp.mulhsu)    ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   MULHU      -> Seq(lsOp(LSOp.nop),     imuOp(IMUOp.mulhu)     ,ExEngine.str(ExEngine.imu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   REM        -> Seq(lsOp(LSOp.nop),     iduOp(IDUOp.rem)       ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   REMU       -> Seq(lsOp(LSOp.nop),     iduOp(IDUOp.remu)      ,ExEngine.str(ExEngine.idu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   FADD_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fadd_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FCLASS_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fclass_s)  ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FCVT_S_W   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fcvt_s_w)  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FCVT_S_WU  -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fcvt_s_wu) ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FCVT_W_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fcvt_w_s)  ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FCVT_WU_S  -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fcvt_wu_s) ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FDIV_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fdiv_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FEQ_S      -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.feq_s)     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FLE_S      -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fle_s)     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FLT_S      -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.flt_s)     ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
  //TODO: LOAD  FLW
   FMADD_S    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmadd_s)   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMAX_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmax_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMIN_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmin_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMSUB_S    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmsub_s)   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMUL_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmul_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMV_W_X    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmv_w_x)   ,ExEngine.str(ExEngine.fpu),W_RF_I,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FMV_X_W    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fmv_x_w)   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_IRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FNMADD_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fnmadd_s)  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FNMSUB_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fnmsub_s)  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_FRF,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FSGNJ_S    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fsgnj_s)   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FSGNJN_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fsgnjn_s)  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FSGNJX_S   -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fsgnjx_s)  ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FSQRT_S    -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fsqrt_s)   ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_IMM,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
   FSUB_S     -> Seq(lsOp(LSOp.nop),     fpuOp(FPUOp.fsub_s)    ,ExEngine.str(ExEngine.fpu),W_RF_F,OPSRC1_FRF,OPSRC2_FRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_F),
  //TODO: LOAD STORE FSW

   SH1ADD     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sh1add)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SH2ADD     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sh2add)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),
   SH3ADD     -> Seq(lsOp(LSOp.nop),     aluOp(ALUOp.sh3add)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_0,T_I),

   CSRRC      -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrc)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   CSRRCI     -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrci)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   CSRRS      -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrs)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   CSRRSI     -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrsi)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   CSRRW      -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrw)     ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   CSRRWI     -> Seq(lsOp(LSOp.nop),     csrOp(CSROp.csrrwi)    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_0,LS_T_N,IS_JAL_0,IS_BRANCH_0,IS_CSR_1,T_I),
   // FRCSR      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.frcsr))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FRFLAGS    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FRRM       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FSCSR      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FSFLAGS    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FSFLAGSI   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FSRM       -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // FSRMI      -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDCYCLE    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDCYCLEH   -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDINSTRET  -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDINSTRETH -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDTIME     -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
   // RDTIMEH    -> Seq(op.e(LSOp.str(LSOp.nop)),     op.e(CSROp.str(CSROp.csrrc))    ,ExEngine.str(ExEngine.alu),W_RF_I,OPSRC1_IRF,OPSRC2_IRF,OPSRC3_IMM,WAKEUP_1,LS_T_N,IS_JAL_0,IS_BRANCH_0,T_I),
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
    io.out.isCSR    -> DecodeConfig.bitRanges(DecodeConfig.isCSRIdx),
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
