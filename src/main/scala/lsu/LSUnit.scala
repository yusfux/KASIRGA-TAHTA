package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DataBus, DecodeConfig, MI, Retirable, TagBus}

// format: off
object LSOp extends ChiselEnum {
  val nop, lb,lh,lw,lbu,lhu,sb,sh,sw, lrw,scw,amoswap,amoadd,amoand,amoor,amoxor,amomax,amomin,amomaxu,amominu = Value

  val values = IndexedSeq(nop, lb,lh,lw,lbu,lhu,sb,sh,sw, lrw,scw,amoswap,amoadd,amoand,amoor,amoxor,amomax,amomin,amomaxu,amominu)

  def toBitpat(op: LSOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def str(op: LSOp.Type): String =
    toBitpat(op).rawString
}
// format: on

class LSINFO(config: WoodConfig) extends Retirable(config) {
  // val retired = Bool()
  val store = Bool()
  val atom  = Bool()
  val addr  = UInt(config.xlen.W)
  // val rdTag = UInt(config.tagWidth.W)
  val inst = UInt(32.W) // for testbench only
  val pc   = UInt(config.xlen.W) // for testbench only
}

class LSRSMI(config: WoodConfig) extends LSINFO(config) { // Load store reservation station micro instruction
  val rs2Data      = UInt(config.xlen.W)
  val lsOp         = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
  val operandReady = Bool()
}

class LSCMI(config: WoodConfig) extends LSINFO(config) { // Load store commit micro instruction
  val cacheData = Vec(config.numBytes, UInt(8.W))
  val sqData    = Vec(config.numBytes, UInt(8.W)) // only used for load update after read
  val wStrobe   = Vec(config.numBytes, Bool()) // Required for CAM reads only
  val lsOp      = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
}

case class LSOperandBus(config: WoodConfig) extends Bundle {
  val targetAddr = UInt(config.xlen.W)
  val rs2Data    = UInt(config.xlen.W)
  val rdTag      = UInt(config.tagWidth.W)
}

class LSUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val flush          = Input(Bool())
    val selfRetired    = Output(Vec(config.nWide, Bool()))
    val out            = Vec(1, ValidIO(new DataBus(config)))
  })

  val inOverridenOperands = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val inputOperandsOverrider = Seq.tabulate(config.nWide) { _ =>
    Module(new LSOverrideOperands(config))
  }

  inOverridenOperands <> io.in

  (0 until config.nWide).foreach(j => {
    inputOperandsOverrider(j).io.lsOperandBus  <> io.lsOperandBus
    inputOperandsOverrider(j).io.targetAddrI   := io.in(j).bits.opsrc1
    inputOperandsOverrider(j).io.rs2DataI      := io.in(j).bits.opsrc2
    inputOperandsOverrider(j).io.rdTagI        := io.in(j).bits.rdTag
    inputOperandsOverrider(j).io.operandReadyI := io.in(j).bits.operandReady

    inOverridenOperands(j).bits.operandReady := inputOperandsOverrider(j).io.operandReadyO
    inOverridenOperands(j).bits.rdData       := inputOperandsOverrider(j).io.targetAddrO
    inOverridenOperands(j).bits.rs2Data      := inputOperandsOverrider(j).io.rs2DataO
  })

  val lsscstage  = Module(new LSScheduleStage(config))
  val lssqstage  = Module(new LSSQStage(config))
  val lsducstage = Module(new LSDummyCache(config))
  val lswbstage  = Module(new LSWriteback(config))

  lsscstage.io.in          <> inOverridenOperands
  lsscstage.io.selfRetired <> io.selfRetired

  lssqstage.io.in  <> lsscstage.io.out
  lsducstage.io.in <> lssqstage.io.out
  lswbstage.io.in  <> lsducstage.io.out
  io.out           <> lswbstage.io.out

  lsscstage.io.lsOperandBus <> io.lsOperandBus

  lsscstage.io.storeRetireBus <> io.storeRetireBus
  lssqstage.io.storeRetireBus <> io.storeRetireBus

  lsscstage.io.frontRetired <> lssqstage.io.selfRetired

  lsscstage.io.flush := io.flush
  lssqstage.io.flush := io.flush
}
