package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCPipelineRegister, DCRRQueue}

class ROBStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val q    = Module(new DCRRQueue(new MI(config))(config.nWide, config.prfDepth))
  val pReg = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  q.io.in    <> io.in
  pReg.io.in <> q.io.out
  io.out     <> pReg.io.out
}

class RetiredStatusStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in                    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus          = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val previousRetiredStatus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val overrideWriteBack         = Module(new OverrideFromBuses(config))
  val pReg                      = Module(new DCPipelineRegister(new MI(config))(config.nWide))
  val retiredStatusRegisterFile = Mem(config.prfDepth, UInt(1.W))

  val overridenRetiredStatusData = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRetiredStatusData <> io.in

  val allRetired = Wire(Vec(config.nWide, Bool()))
  val allReady   = Wire(Vec(config.nWide, Bool()))
  allRetired := io.previousRetiredStatus.map(_.valid)
  allReady   := io.out.map(_.ready)

  (0 until config.nWide).foreach(j => {
    overridenRetiredStatusData(j).bits.retired := retiredStatusRegisterFile.read(io.in(j).bits.rdTag)
  })

  (0 until config.nWide).foreach(j => {

    when(io.writebackBus(j).valid) {
      retiredStatusRegisterFile.write(io.writebackBus(j).bits.tag, 1.U)
    }
    when(io.previousRetiredStatus(j).valid) {
      retiredStatusRegisterFile.write(io.previousRetiredStatus(j).bits.tag, 0.U)
    }
  })

  overrideWriteBack.io.inBus := io.writebackBus.map { bus =>
    val dBus = Wire(ValidIO(new DataBus(config)))
    dBus.bits.tag  := bus.bits.tag
    dBus.valid     := bus.valid
    dBus.bits.data := DontCare
    dBus
  }

  overrideWriteBack.io.in <> overridenRetiredStatusData
  pReg.io.in              <> overrideWriteBack.io.out
  io.out                  <> pReg.io.out

  (0 until config.nWide).foreach(j => {
    pReg.io.out(j).ready := allRetired.asUInt.andR && allReady.asUInt.asBool
  })
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in                    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val previousRetiredStatus = Vec(config.nWide, ValidIO(new TagBus(config)))
    val commitedBus           = Vec(config.nWide, ValidIO(new TagBus(config)))
  })

  val arf = Mem(55, UInt(8.W))

  val archRegisterFile = Mem(32, UInt(config.tagWidth.W))

  val archRegisterFileValid = RegInit(VecInit(Seq.fill(32)(0.U(1.W))))

  (0 until config.nWide).foreach(j => {
    io.previousRetiredStatus(j).bits.tag := io.in(j).bits.rdTag
    io.previousRetiredStatus(j).valid    := io.in(j).bits.retired
    io.in(j).ready                       := 1.U // no reason to stall

    when(io.in(j).bits.writeRf.asBool) {
      archRegisterFile.write(io.in(j).bits.rd, io.in(j).bits.rdTag)
      archRegisterFileValid(io.in(j).bits.rd) := 1.U
    }
  })

  (0 until config.nWide).foreach(j => {
    io.commitedBus(j).bits.tag := archRegisterFile.read(io.in(j).bits.rd) // TODO RegEnable
    io.commitedBus(j).valid    := archRegisterFileValid(io.in(j).bits.rd) // TODO RegEnable
    dontTouch(io.in(j).bits.inst) // for testbench only
  })
}
