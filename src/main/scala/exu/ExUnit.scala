package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

object ExEngine extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}

class Tag(config: WoodConfig) extends Bundle {
  val tag = UInt(config.tagWidth.W)
}

class ForwardBus(config: WoodConfig) extends Tag(config) {
  val data = UInt(config.dataWidth.W)
}

class WriteBack(config: WoodConfig) extends Bundle {
  val tag  = UInt(config.tagWidth.W)
  val data = UInt(config.dataWidth.W)
}

class ExUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val forwardBuses = Vec(config.nWide, Decoupled(new ForwardBus(config)))
  })

  val restage = Module(new RenameStage(config))
  val scstage = Module(new ScheduleStage(config))
  val rrstage = Module(new RegisterReadStage(config))
  val exstage = Module(new ExecuteStage(config))

  val wbstage = Module(new WriteBackStage(config))

  val rbstage = Module(new ROBStage(config))
  val rsstage = Module(new RetiredStatusStage(config))
  val arstage = Module(new ArchRegisterFileStage(config))

  io.in          <> restage.io.in
  restage.io.out <> scstage.io.in
  scstage.io.out <> rrstage.io.in
  rrstage.io.out <> exstage.io.in

  exstage.io.out <> wbstage.io.in

  exstage.io.out <> rbstage.io.in
  rbstage.io.out <> rsstage.io.in
  rsstage.io.out <> arstage.io.in

  exstage.io.forwardBuses          <> io.forwardBuses
  arstage.io.previousRetiredStatus <> rsstage.io.previousRetiredStatus

  restage.io.retiredBus <> arstage.io.retiredBus
  scstage.io.retiredBus <> arstage.io.retiredBus
  rsstage.io.tagBuses   <> wbstage.io.tagBuses
  scstage.io.tagBuses   <> wbstage.io.tagBuses
  rrstage.io.tagBuses   <> wbstage.io.tagBuses

  rrstage.io.stall := 0.U // TODO
  scstage.io.stall := 0.U // TODO

  val tagsReady = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach(j => {
    tagsReady(j)                 := exstage.io.forwardBuses(j).ready
    wbstage.io.tagBuses(j).ready := tagsReady.asUInt.andR
  })

  // exstage.io.out   <>
}
