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
  val cacheLine  = Vec(config.numDCacheLineBytes, UInt(8.W))
  val wStrobe    = Vec(config.numDCacheLineBytes, Bool()) // Required for CAM reads only
  val lsOp       = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
  val commitable = Bool() // cache line has been read and is from store queue
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
  val lsdc0stage = Module(new LSDCache0Stage(config))
  val lsdc1stage = Module(new LSDCache1Stage(config))
  val lsducstage = Module(new LSDummyCache(config))
  val lsatstage  = Module(new LSAtom(config))

  lsscstage.io.in          <> inOverridenOperands
  lsscstage.io.selfRetired <> io.selfRetired

  lsdc0stage.io.in <> lsscstage.io.out
  lsducstage.io.in <> lsdc0stage.io.outCache

  // lsdc1stage.io.in <> lsdc0stage.io.outCache //TODO connect to cache
  lsdc1stage.io.in     <> lsdc0stage.io.outPass
  lsatstage.io.inPass  <> lsdc1stage.io.out
  lsatstage.io.inCache <> lsducstage.io.out

  io.out                  <> lsatstage.io.outRF
  lsdc0stage.io.lsAtomBus <> lsatstage.io.outSQ

  lsscstage.io.lsOperandBus <> io.lsOperandBus

  lsscstage.io.storeRetireBus  <> io.storeRetireBus
  lsdc0stage.io.storeRetireBus <> io.storeRetireBus
  lsdc1stage.io.storeRetireBus <> io.storeRetireBus
  lsatstage.io.storeRetireBus  <> io.storeRetireBus

  lsscstage.io.frontRetired  <> lsdc0stage.io.selfRetired
  lsdc0stage.io.frontRetired <> lsdc1stage.io.selfRetired
  lsdc1stage.io.frontRetired <> lsatstage.io.selfRetired

  lsdc0stage.io.lsDCache1Bus <> lsdc1stage.io.lsDCache1Bus

  lsdc1stage.io.lsAtomBus.bits  := lsatstage.io.outSQ.bits
  lsdc1stage.io.lsAtomBus.valid := lsatstage.io.outSQ.valid

  lsscstage.io.flush  := io.flush
  lsdc0stage.io.flush := io.flush
  lsdc1stage.io.flush := io.flush
}
