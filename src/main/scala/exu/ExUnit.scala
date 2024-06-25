package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.{DecodeStage, DistributeStage, FetchConfig, MIStage}

object ExEngine extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}

object ExConfig {
  val dataWidth     = 32
  val prfDepth      = 128
  val rsDepth       = 4 // Reservation station depth
  val tagWidth      = log2Ceil(prfDepth)
  val numALUs       = 4
  val numPortsFloat = 4
  val numPortsInt   = 4
}

class Tag extends Bundle {
  val tag = UInt(ExConfig.tagWidth.W)
}

class ForwardBus extends Tag {
  val data = UInt(ExConfig.dataWidth.W)
}

class WriteBack extends Bundle {
  val tag  = UInt(ExConfig.tagWidth.W)
  val data = UInt(ExConfig.dataWidth.W)
}

class ExUnit(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val inst  = Flipped(Vec(numPorts, Decoupled(UInt(32.W))))
    val pcIdx = Flipped(Decoupled(UInt(FetchConfig.pcIndexWidth.W)))

    val forwardBuses = Vec(numPorts, Decoupled(new ForwardBus()))
  })

  val destage = Module(new DecodeStage(numPorts))
  val distage = Module(new DistributeStage(numPorts))
  val mistage = Module(new MIStage(numPorts))
  val restage = Module(new RenameStage(numPorts))
  val scstage = Module(new ScheduleStage(numPorts))
  val rrstage = Module(new RegisterReadStage(numPorts))
  val exstage = Module(new ExecuteStage(numPorts))

  val wbstage = Module(new WriteBackStage(numPorts))

  val rbstage = Module(new ROBStage(numPorts))
  val rsstage = Module(new RetiredStatusStage(numPorts))
  val arstage = Module(new ArchRegisterFileStage(numPorts))

  destage.io.inst  <> io.inst
  destage.io.pcIdx <> io.pcIdx
  destage.io.out   <> distage.io.in
  distage.io.toInt <> mistage.io.in
  mistage.io.out   <> restage.io.in
  restage.io.out   <> scstage.io.in
  scstage.io.out   <> rrstage.io.in
  rrstage.io.out   <> exstage.io.in

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

  val tagsReady = Wire(Vec(numPorts, Bool()))

  (0 until numPorts).foreach(j => {
    tagsReady(j)                 := exstage.io.forwardBuses(j).ready
    wbstage.io.tagBuses(j).ready := tagsReady.asUInt.andR

    distage.io.toFloat(j).ready := 1.U
  })

  // exstage.io.out   <>
}
