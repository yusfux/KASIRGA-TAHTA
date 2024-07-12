package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCArbiter, DCPipelineRegister, DCRRQueue}

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
    val previousRetiredStatus = Flipped(Vec(config.nWide, ValidIO(new MI(config))))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val overrideWriteBack         = Module(new OverrideFromBuses(config))
  val pReg                      = Module(new DCPipelineRegister(new MI(config))(config.nWide))
  val retiredStatusRegisterFile = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))
  val retryArbiters = Seq.tabulate(config.nWide) { _ =>
    Module(new DCArbiter(new MI(config))(2, 1))
  }

  val overridenRetiredStatusData = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  (0 until config.nWide).foreach(j => {
    retryArbiters(j).io.in(1)       <> io.in(j)
    retryArbiters(j).io.in(0).bits  := io.previousRetiredStatus(j).bits
    retryArbiters(j).io.in(0).valid := io.previousRetiredStatus(j).valid & (!io.previousRetiredStatus(j).bits.retired)
    overridenRetiredStatusData(j)   <> retryArbiters(j).io.out(0)
  })

  val allPreviouslyRetired = Wire(Vec(config.nWide, Bool()))
  val allPreviouslyValid   = Wire(Vec(config.nWide, Bool()))
  val allOutReady          = Wire(Vec(config.nWide, Bool()))
  allPreviouslyRetired := io.previousRetiredStatus.map(_.bits.retired.asBool)
  allPreviouslyValid   := io.previousRetiredStatus.map(_.valid)
  allOutReady          := io.out.map(_.ready)

  (0 until config.nWide).foreach(j => {
    overridenRetiredStatusData(j).bits.retired := retiredStatusRegisterFile(io.in(j).bits.rdTag)
  })

  (0 until config.nWide).foreach(j => {

    when(io.writebackBus(j).valid) {
      retiredStatusRegisterFile(io.writebackBus(j).bits.tag) := 1.U
    }
    when(io.previousRetiredStatus(j).valid) {
      retiredStatusRegisterFile(io.previousRetiredStatus(j).bits.rdTag) := 0.U
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
    overrideWriteBack.io.out(j).ready := MuxCase(
      0.U,
      Array(
        (allPreviouslyRetired.asUInt.andR & allPreviouslyValid.asUInt.asBool)  -> 1.U,
        (!allPreviouslyRetired.asUInt.andR & allPreviouslyValid.asUInt.asBool) -> 0.U,
        (!allPreviouslyValid.asUInt.asBool)                                    -> 1.U
      ).toIndexedSeq
    )
  })
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in                    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val previousRetiredStatus = Vec(config.nWide, ValidIO(new MI(config)))
    val commitedBus           = Vec(config.nWide, ValidIO(new TagBus(config)))
  })

  val archRegisterFile = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))

  val archRegisterFileValid = RegInit(VecInit(Seq.fill(32)(0.U(1.W))))

  (0 until config.nWide).foreach(j => {
    io.previousRetiredStatus(j).bits  := io.in(j).bits
    io.previousRetiredStatus(j).valid := io.in(j).valid
    io.in(j).ready                    := 1.U // no reason to stall

    when(io.in(j).bits.writeRf.asBool) {
      archRegisterFile(io.in(j).bits.rd)      := io.in(j).bits.rdTag
      archRegisterFileValid(io.in(j).bits.rd) := 1.U
    }
  })

  (0 until config.nWide).foreach(j => {
    io.commitedBus(j).bits.tag := archRegisterFile(io.in(j).bits.rd) // TODO RegEnable
    io.commitedBus(j).valid    := archRegisterFileValid(io.in(j).bits.rd) // TODO RegEnable
    dontTouch(io.in(j).bits.inst) // for testbench only
  })
}
