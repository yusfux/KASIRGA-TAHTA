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

class TagBus(config: WoodConfig) extends Tag(config) {}

class DataBus(config: WoodConfig) extends TagBus(config) {
  val data = UInt(config.dataWidth.W)
}

class ARFBus(config: WoodConfig) extends TagBus(config) {
  val rd = UInt(5.W)
}

class ExUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus = Vec(config.nWide, ValidIO(new DataBus(config)))
  })

  val mistage = Module(new MIStage(config))
  val restage = Module(new RenameStage(config))
  val scstage = Module(new ScheduleStage(config))
  val rrstage = Module(new RegisterReadStage(config))
  val exstage = Module(new ExecuteStage(config))

  val wbstage = Module(new WritebackStage(config))

  val rbstage = Module(new ROBStage(config))
  val rsstage = Module(new RetiredStatusStage(config))
  val arstage = Module(new ArchRegisterFileStage(config))
  val rwstage = Module(new RetireWritebackStage(config))

  mistage.io.in <> io.in
  restage.io.in <> mistage.io.out

  (0 until config.nWide).foreach(j => {
    rbstage.io.in(j).bits  := restage.io.out(j).bits
    rbstage.io.in(j).valid := restage.io.out(j).valid

    scstage.io.in(j).bits  := restage.io.out(j).bits
    scstage.io.in(j).valid := restage.io.out(j).valid

    restage.io.out(j).ready := rbstage.io.in(j).ready & scstage.io.in(j).ready
  })

  rrstage.io.in    <> scstage.io.out
  exstage.io.aluIn <> rrstage.io.aluOut
  wbstage.io.in    <> exstage.io.out

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

  mistage.io.commitedBus <> rwstage.io.commitedBus
  (0 until config.nWide).foreach(j => {
    scstage.io.commitedBus(j).bits.tag := rwstage.io.commitedBus(j).bits.tag
    scstage.io.commitedBus(j).valid    := rwstage.io.commitedBus(j).valid

    rsstage.io.commitedBus(j).bits.tag := rwstage.io.commitedBus(j).bits.tag
    rsstage.io.commitedBus(j).valid    := rwstage.io.commitedBus(j).valid
  })

  rrstage.io.forwardBus <> exstage.io.forwardBus
  io.forwardBus         <> exstage.io.forwardBus
  scstage.io.forwardBus <> exstage.io.forwardBus.map { bus =>
    val tBus = Wire(ValidIO(new TagBus(config)))
    tBus.bits.tag := bus.bits.tag
    tBus.valid    := bus.valid
    tBus
  }

  scstage.io.wakeupBus <> rrstage.io.wakeupBus

  scstage.io.stall := 0.U // TODO
}
