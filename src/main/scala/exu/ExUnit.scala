package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.DCBus

object ExEngine extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}

class Bus(config: WoodConfig) extends Bundle {
  val tag  = UInt(config.tagWidth.W)
  val data = UInt(config.dataWidth.W)
}

class ExUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val forwardBus = Vec(config.nWide, Decoupled(new Bus(config)))
  })

  val frontEndBus  = Module(new DCBus(new MI(config))(config.nWide, 2))
  val writeBackBus = Module(new DCBus(new Bus(config))(config.nWide, 2))
  val commitedBus  = Module(new DCBus(new Bus(config))(config.nWide, 2))
  val forwardBus   = Module(new DCBus(new Bus(config))(config.nWide, 2))

  val restage = Module(new RenameStage(config))
  val scstage = Module(new ScheduleStage(config))
  val rrstage = Module(new RegisterReadStage(config))
  val exstage = Module(new ExecuteStage(config))

  val wbstage = Module(new WriteBackStage(config))

  val rbstage = Module(new ROBStage(config))
  val rsstage = Module(new RetiredStatusStage(config))
  val arstage = Module(new ArchRegisterFileStage(config))

  frontEndBus.io.in     <> io.in
  frontEndBus.io.out(0) <> restage.io.in
  frontEndBus.io.out(1) <> rbstage.io.in

  restage.io.out          <> scstage.io.in
  scstage.io.out          <> rrstage.io.in
  rrstage.io.out          <> exstage.io.in
  exstage.io.out          <> wbstage.io.in
  wbstage.io.writeBackBus <> writeBackBus.io.in
  writeBackBus.io.out(0)  <> rsstage.io.writeBackBus
  writeBackBus.io.out(1)  <> rrstage.io.writeBackBus

  rbstage.io.out         <> rsstage.io.in
  rsstage.io.out         <> arstage.io.in
  arstage.io.commitedBus <> commitedBus.io.in
  commitedBus.io.out(0)  <> restage.io.commitedBus
  commitedBus.io.out(1)  <> scstage.io.commitedBus

  arstage.io.previousRetiredStatus <> rsstage.io.previousRetiredStatus

  exstage.io.forwardBus <> forwardBus.io.in
  forwardBus.io.out(0)  <> scstage.io.forwardBus
  forwardBus.io.out(1)  <> io.forwardBus

  rrstage.io.stall := 0.U // TODO
  scstage.io.stall := 0.U // TODO

}
