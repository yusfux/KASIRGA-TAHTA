package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCPipelineRegister

class RetireStatusStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new RetireMI(config))))
    val firstPC      = Flipped(Valid(UInt(config.pcWidth.W)))
    val writebackBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val exceptionBus = Flipped(Vec(config.nWide, ValidIO(new ExceptionBus(config))))
    val commitedBus  = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out          = Vec(config.nWide, Decoupled(new RetireMI(config)))
    val bpBus        = Vec(config.nWide, ValidIO(new BranchPredictorBus(config)))
    val flush        = Output(Bool())
  })

  val pRegs                       = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new RetireMI(config))(1)))
  val retireStatusRegisterFile    = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))
  val takenStatusRegisterFile     = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))
  val exceptionStatusRegisterFile = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))
  val pcRegisterFile              = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(config.dataWidth.W)))) // TODO: remove reset

  val predictedPCs           = VecInit(io.in.tail.map(_.bits.pc) :+ io.firstPC.bits)
  val self                   = Wire(Vec(config.nWide, Decoupled(new RetireMI(config))))
  val overridenRetiredStatus = Wire(Vec(config.nWide, Decoupled(new RetireMI(config))))

  val flushVector  = Wire(UInt(config.nWide.W))
  val flushVectors = Wire(Vec(config.nWide, UInt(config.nWide.W)))

  val allRetired = Wire(Vec(config.nWide, Bool()))
  val allInValid = Wire(Vec(config.nWide, Bool()))

  val exceptionMispredSet = VecInit(Seq.fill(config.nWide)(false.B))
  val mispredIndex        = PriorityEncoder(exceptionMispredSet)

  allRetired := overridenRetiredStatus.map(_.bits.retired.asBool)
  allInValid := io.in.map(_.valid)

  (0 until config.nWide).foreach(j => {
    io.bpBus(j).bits.exception  := exceptionStatusRegisterFile(io.in(j).bits.rdTag)
    io.bpBus(j).bits.pc         := io.in(j).bits.pc
    io.bpBus(j).bits.taken      := takenStatusRegisterFile(io.in(j).bits.rdTag) & !io.in(j).bits.flushed
    io.bpBus(j).bits.mispredict := exceptionMispredSet(j)
    io.bpBus(j).bits.targetPC   := pcRegisterFile(io.in(j).bits.rdTag)
    io.bpBus(j).valid           := io.in(j).fire & !io.in(j).bits.flushed & (io.in(j).bits.isJAL | io.in(j).bits.isBranch) & !flushVector(j)
  })

  io.flush    := exceptionMispredSet.reduce(_ || _)
  flushVector := flushVectors.reduce(_ | _)

  overridenRetiredStatus <> io.in

  (0 until config.nWide).foreach(j => {
    overridenRetiredStatus(j).bits.retired := retireStatusRegisterFile(io.in(j).bits.rdTag) | io.in(j).bits.flushed | flushVector(j)

    when(io.exceptionBus(j).valid) {
      val tag = io.exceptionBus(j).bits.tag
      pcRegisterFile(tag)              := io.exceptionBus(j).bits.pc
      takenStatusRegisterFile(tag)     := io.exceptionBus(j).bits.taken
      exceptionStatusRegisterFile(tag) := io.exceptionBus(j).bits.exception
    }

    when(io.writebackBus(j).valid) {
      retireStatusRegisterFile(io.writebackBus(j).bits.tag) := 1.U
    }
    when(io.commitedBus(j).valid) {
      val tag = io.commitedBus(j).bits.tag
      retireStatusRegisterFile(tag)    := 0.U
      takenStatusRegisterFile(tag)     := 0.U
      exceptionStatusRegisterFile(tag) := 0.U
    }

    self(j).bits         := overridenRetiredStatus(j).bits
    self(j).bits.flushed := flushVector(j) | overridenRetiredStatus(j).bits.flushed

    self(j).valid := (allInValid.asUInt.andR & allRetired.asUInt.andR) | (io.in(j).bits.flushed & io.in(j).valid)

    pRegs(j).io.valids(0)       := io.in(j).valid
    pRegs(j).io.flush           := 0.U // never lose tags
    pRegs(j).io.in.bits.flushed := flushVector(j)

    overridenRetiredStatus(j).ready := self(j).ready

    pRegs(j).io.in <> self(j)
    io.out(j)      <> pRegs(j).io.out
  })

  (0 until config.nWide).foreach(j => {
    val tag             = io.in(j).bits.rdTag
    val notFlushed      = !io.in(j).bits.flushed
    val isException     = exceptionStatusRegisterFile(tag).asBool
    val isTaken         = takenStatusRegisterFile(tag).asBool
    val actualTarget    = pcRegisterFile(tag)
    val predictedTarget = predictedPCs(j)

    flushVectors(j) := 0.U
    when((isException | isTaken) & notFlushed) {
      when(predictedTarget =/= actualTarget) {
        exceptionMispredSet(j) := true.B
        flushVectors(j)        := Fill(config.nWide, 1.U) << (j + 1)
      }
    }.otherwise {
      exceptionMispredSet(j) := false.B
    }
  })

  (0 until config.nWide).foreach(j => {
    when(flushVector(j)) {
      self(j).bits.writeRf := 0.U
      self(j).bits.retired := true.B
      self(j).bits.retired := true.B
    }
  })
}
