package wood.exu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DecodeConfig, DecodeStage}
import wood.fru.PCInst
import wood.lsu.{LSMI, LSUnit}

case class MI(config: WoodConfig) extends Bundle {
  val isFloat     = UInt(DecodeConfig.subWidths(DecodeConfig.isFloatIdx).W)
  val isBranch    = UInt(DecodeConfig.subWidths(DecodeConfig.isBranchIdx).W)
  val isJAL       = UInt(DecodeConfig.subWidths(DecodeConfig.isJALIdx).W)
  val lsType      = UInt(DecodeConfig.subWidths(DecodeConfig.lsTypeIdx).W)
  val wakeup      = UInt(DecodeConfig.subWidths(DecodeConfig.wakeupIdx).W)
  val operand1    = UInt(DecodeConfig.subWidths(DecodeConfig.operand1Idx).W)
  val operand2    = UInt(DecodeConfig.subWidths(DecodeConfig.operand2Idx).W)
  val operand3    = UInt(DecodeConfig.subWidths(DecodeConfig.operand3Idx).W)
  val writeRf     = UInt(DecodeConfig.subWidths(DecodeConfig.writeRfIdx).W)
  val exEngine    = UInt(DecodeConfig.subWidths(DecodeConfig.exEngineIdx).W)
  val exOp        = UInt(DecodeConfig.subWidths(DecodeConfig.exOpIdx).W)
  val exception   = Bool()
  val taken       = Bool()
  val imm         = UInt(32.W) // TODO
  val rs1         = UInt(5.W)
  val rs2         = UInt(5.W)
  val rs3         = UInt(5.W)
  val rd          = UInt(5.W)
  val rm          = UInt(3.W)
  val pc          = UInt(config.pcWidth.W)
  val targetPC    = UInt(config.pcWidth.W)
  val rs1Tag      = UInt(config.tagWidth.W)
  val rs1TagReady = Bool()
  val rs2Tag      = UInt(config.tagWidth.W)
  val rs2TagReady = Bool()
  val rs3Tag      = UInt(config.tagWidth.W)
  val rs3TagReady = Bool()
  val rdTag       = UInt(config.tagWidth.W)
  val rs1Data     = UInt(config.dataWidth.W)
  val rs2Data     = UInt(config.dataWidth.W)
  val rs3Data     = UInt(config.dataWidth.W)
  val rdData      = UInt(config.dataWidth.W)
  val retired     = Bool()
  val flushed     = Bool()
  val arfTag      = UInt(config.tagWidth.W)
  val arfValid    = Bool()
  val inst        = UInt(32.W) // for testbench only
}

case class RetireMI(config: WoodConfig) extends Bundle {
  val isBranch = UInt(DecodeConfig.subWidths(DecodeConfig.isBranchIdx).W)
  val isJAL    = UInt(DecodeConfig.subWidths(DecodeConfig.isJALIdx).W)
  val writeRf  = UInt(DecodeConfig.subWidths(DecodeConfig.writeRfIdx).W)
  val rd       = UInt(5.W)
  val pc       = UInt(config.pcWidth.W)
  val targetPC = UInt(config.pcWidth.W)
  val rdTag    = UInt(config.tagWidth.W)
  val retired  = Bool()
  val flushed  = Bool()
  val arfTag   = UInt(config.tagWidth.W)
  val arfValid = Bool()
  val inst     = UInt(32.W) // for testbench only
}

object MI { // for testbench only, set all to value except overrides
  def apply(config: WoodConfig, value: UInt, overrides: Map[String, UInt] = Map.empty): MI = {
    val mi = new MI(config).Lit(
      _.isFloat     -> overrides.getOrElse("isFloat", value),
      _.isBranch    -> overrides.getOrElse("isBranch", value),
      _.isJAL       -> overrides.getOrElse("isJAL", value),
      _.wakeup      -> overrides.getOrElse("wakeup", value),
      _.operand3    -> overrides.getOrElse("operand3", value),
      _.operand2    -> overrides.getOrElse("operand2", value),
      _.operand1    -> overrides.getOrElse("operand1", value),
      _.writeRf     -> overrides.getOrElse("writeRf", value),
      _.exEngine    -> overrides.getOrElse("exEngine", value),
      _.exOp        -> overrides.getOrElse("exOp", value),
      _.exception   -> overrides.getOrElse("exception", value),
      _.taken       -> overrides.getOrElse("taken", value),
      _.imm         -> overrides.getOrElse("imm", value),
      _.rs1         -> overrides.getOrElse("rs1", value),
      _.rs2         -> overrides.getOrElse("rs2", value),
      _.rs3         -> overrides.getOrElse("rs3", value),
      _.rd          -> overrides.getOrElse("rd", value),
      _.rm          -> overrides.getOrElse("rm", value),
      _.pc          -> overrides.getOrElse("pc", value),
      _.rs1Tag      -> overrides.getOrElse("rs1Tag", value),
      _.rs1TagReady -> overrides.getOrElse("rs1TagReady", value),
      _.rs2Tag      -> overrides.getOrElse("rs2Tag", value),
      _.rs2TagReady -> overrides.getOrElse("rs2TagReady", value),
      _.rdTag       -> overrides.getOrElse("rdTag", value),
      _.rs1Data     -> overrides.getOrElse("rs1Data", value),
      _.rs2Data     -> overrides.getOrElse("rs2Data", value),
      _.rs3Data     -> overrides.getOrElse("rs3Data", value),
      _.rdData      -> overrides.getOrElse("rdData", value),
      _.retired     -> overrides.getOrElse("retired", value),
      _.flushed     -> overrides.getOrElse("flushed", value),
      _.arfTag      -> overrides.getOrElse("arfTag", value),
      _.arfValid    -> overrides.getOrElse("arfValid", value),
      _.inst        -> overrides.getOrElse("inst", value)
    )
    mi
  }
}

object ExEngine extends ChiselEnum {
  val alu, imu, idu, lsu, fpu, none = Value
  val values                        = IndexedSeq(alu, imu, idu, lsu, fpu, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}

class Tag(config: WoodConfig) extends Bundle {
  val tag = UInt(config.tagWidth.W)
}

class TagBus(config: WoodConfig) extends Tag(config) {}

class DataBus(config: WoodConfig) extends TagBus(config) {
  val data = UInt(config.dataWidth.W)
}

class ExceptionBus(config: WoodConfig) extends TagBus(config) {
  val pc        = UInt(config.pcWidth.W)
  val taken     = Bool()
  val exception = Bool()
}

class BranchPredictorBus(config: WoodConfig) extends Bundle {
  val pc         = UInt(config.pcWidth.W)
  val mispredict = Bool()
  val taken      = Bool()
  val exception  = Bool()
  val targetPC   = UInt(config.pcWidth.W)
}

class ARFBus(config: WoodConfig) extends TagBus(config) {
  val rd = UInt(5.W)
}

class ExUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new PCInst(config))))
    val bpBus = Vec(config.nWide, ValidIO(new BranchPredictorBus(config)))
  })

  val lsunit = Module(new LSUnit(config))
  val lsOuts = Wire(Vec(1, ValidIO(new LSMI(config)))) // TODO: only 1 lsu
  lsOuts(0) <> lsunit.io.out

  val destage = Module(new DecodeStage(config))
  val mistage = Module(new MIStage(config))
  val restage = Module(new RenameStage(config))
  val scstage = Module(new ScheduleStage(config))
  val rrstage = Module(new RegisterReadStage(config))
  val exstage = Module(new ExecuteStage(config))

  val wbstage = Module(new WritebackStage(config))

  val rbstage = Module(new ROBStage(config))
  val rsstage = Module(new RetireStatusStage(config))
  val arstage = Module(new ArchRegisterFileStage(config))
  val rwstage = Module(new RetireWritebackStage(config))

  destage.io.in <> io.in
  mistage.io.in <> destage.io.out
  restage.io.in <> mistage.io.out
  rbstage.io.in <> restage.io.out0
  scstage.io.in <> restage.io.out1
  lsunit.io.in  <> restage.io.out2

  rsstage.io.lsuIn         <> lsOuts
  rrstage.io.lsuIn         <> lsOuts
  lsunit.io.storeRetireBus <> rsstage.io.storeRetireBus
  lsunit.io.lsOperandBus   <> wbstage.io.lsOperandBus

  restage.io.archRF <> arstage.io.archRF

  rrstage.io.in    <> scstage.io.out
  exstage.io.aluIn <> rrstage.io.aluOut
  exstage.io.imuIn <> rrstage.io.imuOut
  exstage.io.iduIn <> rrstage.io.iduOut
  wbstage.io.in    <> exstage.io.out

  rsstage.io.exceptionBus <> wbstage.io.exceptionBus

  rrstage.io.writebackBus <> wbstage.io.writebackBus
  rsstage.io.writebackBus <> wbstage.io.writebackBus.map { bus =>
    val tBus = Wire(ValidIO(new TagBus(config)))
    tBus.bits.tag := bus.bits.tag
    tBus.valid    := bus.valid
    tBus
  }

  rsstage.io.in <> rbstage.io.out
  arstage.io.in <> rsstage.io.out
  rwstage.io.in <> arstage.io.out

  arstage.io.arfBus <> rwstage.io.arfBus

  rsstage.io.firstPC <> rbstage.io.firstPC

  destage.io.flush <> rsstage.io.flush
  restage.io.flush <> rsstage.io.flush
  scstage.io.flush <> rsstage.io.flush
  rrstage.io.flush <> rsstage.io.flush
  exstage.io.flush <> rsstage.io.flush
  mistage.io.flush <> rsstage.io.flush
  rbstage.io.flush <> rsstage.io.flush
  lsunit.io.flush  <> rsstage.io.flush

  mistage.io.commitedBus <> rwstage.io.commitedBus
  (0 until config.nWide).foreach(j => {
    scstage.io.commitedBus(j).bits.tag := rwstage.io.commitedBus(j).bits.tag
    scstage.io.commitedBus(j).valid    := rwstage.io.commitedBus(j).valid

    rsstage.io.commitedBus(j).bits.tag := rwstage.io.commitedBus(j).bits.tag
    rsstage.io.commitedBus(j).valid    := rwstage.io.commitedBus(j).valid
  })

  rrstage.io.forwardBus <> exstage.io.forwardBus
  scstage.io.forwardBus <> exstage.io.forwardBus.map { bus =>
    val tBus = Wire(ValidIO(new TagBus(config)))
    tBus.bits.tag := bus.bits.tag
    tBus.valid    := bus.valid
    tBus
  }

  scstage.io.wakeupBus               <> rrstage.io.wakeupBus
  scstage.io.lsWakeupBus(0).bits.tag := lsunit.io.out.bits.rdTag
  scstage.io.lsWakeupBus(0).valid    := lsunit.io.out.valid

  io.bpBus <> rsstage.io.bpBus
}
