package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DataBus, DecodeConfig, MI, TagBus}

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

class LSINFO(config: WoodConfig) extends Bundle {
  val retired = Bool()
  val store   = Bool()
  val addr    = UInt(config.xlen.W)
  val rdTag   = UInt(config.tagWidth.W)
  val inst    = UInt(32.W) // for testbench only
  val pc      = UInt(config.xlen.W) // for testbench only
}

class LSRSMI(config: WoodConfig) extends LSINFO(config) { // Load store reservation station micro instruction
  val rs2Data = UInt(config.xlen.W)
  val lsOp    = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
}

class LSCMI(config: WoodConfig) extends LSRSMI(config) { // Load store load micro instruction
  val cacheLine = Vec(config.numDCacheLineBytes, UInt(8.W))
  val wStrobe   = Vec(config.numDCacheLineBytes, Bool()) // Required for CAM reads only
}

case class LSOperandBus(config: WoodConfig) extends Bundle {
  val targetAddr = UInt(config.xlen.W)
  val rs2Data    = UInt(config.xlen.W)
  val rdTag      = UInt(config.tagWidth.W)
}

class LSOverrideRetire[T <: LSINFO](gen: T)(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(gen.cloneType))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = Decoupled(gen.cloneType)
  })

  io.out <> io.in

  val retireBusMatches = Wire(Vec(config.nWide, Bool()))

  for (j <- 0 until config.nWide) {
    retireBusMatches(j) := io.storeRetireBus(j).valid & (io.in.bits.rdTag === io.storeRetireBus(j).bits.tag)
  }

  io.out.bits.retired := retireBusMatches.asUInt.orR
}

class LSUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val flush          = Input(Bool())
    val out            = Vec(1, ValidIO(new DataBus(config)))
  })

  val lsscstage  = Module(new LSScheduleStage(config))
  val lsdc0stage = Module(new LSDCache0Stage(config))
  val lsdc1stage = Module(new LSDCache1Stage(config))
  val lsducstage = Module(new LSDummyCache(config))
  val lsatstage  = Module(new LSAtom(config))

  lsscstage.io.in <> io.in

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

  lsdc0stage.io.lsDCache1Bus <> lsdc1stage.io.lsDCache1Bus

  lsdc1stage.io.lsAtomBus.bits  := lsatstage.io.outSQ.bits
  lsdc1stage.io.lsAtomBus.valid := lsatstage.io.outSQ.valid

  lsscstage.io.flush  := io.flush
  lsdc0stage.io.flush := io.flush
  lsdc1stage.io.flush := io.flush
}
