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

  val q     = Module(new DCRRQueue(new MI(config))(config.nWide, config.robDepth))
  val pRegs = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  q.io.in <> io.in

  (0 until config.nWide).foreach(j => {
    pRegs(j).io.valids(0) := io.out(j).valid

    pRegs(j).io.in <> q.io.out(j)
    io.out(j)      <> pRegs(j).io.out
  })
}

class RetiredStatusStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val commitedBus  = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out          = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pRegs                     = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))
  val retiredStatusRegisterFile = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))

  val self                   = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val overridenRetiredStatus = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  val allRetired = Wire(Vec(config.nWide, Bool()))
  val allInValid = Wire(Vec(config.nWide, Bool()))
  allRetired := overridenRetiredStatus.map(_.bits.retired.asBool)
  allInValid := io.in.map(_.valid)

  overridenRetiredStatus <> io.in

  (0 until config.nWide).foreach(j => {
    overridenRetiredStatus(j).bits.retired := retiredStatusRegisterFile(io.in(j).bits.rdTag)

    when(io.writebackBus(j).valid) {
      retiredStatusRegisterFile(io.writebackBus(j).bits.tag) := 1.U
    }
    when(io.commitedBus(j).valid) {
      retiredStatusRegisterFile(io.commitedBus(j).bits.tag) := 0.U
    }

    self(j).bits  := overridenRetiredStatus(j).bits
    self(j).valid := allInValid.asUInt.andR & allRetired.asUInt.andR

    pRegs(j).io.valids(0) := io.in(j).valid

    overridenRetiredStatus(j).ready := self(j).ready

    pRegs(j).io.in <> self(j)
    io.out(j)      <> pRegs(j).io.out
  })
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in     = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val arfBus = Input(Vec(config.nWide, ValidIO(new ARFBus(config))))
    val out    = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val archRegisterFile      = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))
  val archRegisterFileValid = RegInit(VecInit(Seq.fill(32)(0.U(1.W))))
  val arfOverriders         = Seq.fill(config.nWide)(Module(new OverrideArfTagFromBus(config)))
  val pRegs                 = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  val overridenRF = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  (0 until config.nWide).foreach(j => {
    overridenRF(j)               <> io.in(j)
    overridenRF(j).bits.arfTag   := archRegisterFile(io.in(j).bits.rd)
    overridenRF(j).bits.arfValid := archRegisterFileValid(io.in(j).bits.rd)
    overridenRF(j).valid         := io.in(j).valid

    arfOverriders(j).io.inBus <> io.arfBus
    arfOverriders(j).io.in    <> overridenRF(j)

    when(io.arfBus(j).valid) {
      archRegisterFile(io.arfBus(j).bits.rd)      := io.arfBus(j).bits.tag
      archRegisterFileValid(io.arfBus(j).bits.rd) := 1.U
    }

    pRegs(j).io.valids(0) := io.in(j).valid // READ ARCH RF VALID

    io.in(j).ready := arfOverriders(j).io.out.ready

    pRegs(j).io.in <> arfOverriders(j).io.out
    io.out(j)      <> pRegs(j).io.out
  })
}

class RetireWritebackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val arfBus      = Vec(config.nWide, ValidIO(new ARFBus(config)))
    val commitedBus = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val allReady = Wire(Vec(config.nWide, Bool())).suggestName("allInValid")
  allReady := io.commitedBus.map(_.ready)

  val rdOverriden = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach(j => {
    io.arfBus(j).bits.rd  := io.in(j).bits.rd
    io.arfBus(j).bits.tag := io.in(j).bits.rdTag

    io.in(j).ready := allReady.asUInt.andR

    rdOverriden(j) := ((j + 1) until config.nWide).foldRight(false.B) { (k, acc) =>
      val rdM = (io.in(j).bits.rd === io.in(k).bits.rd) & io.in(k).bits.writeRf.asBool
      acc || rdM
    }

    val attemptArfWrite = (io.in(j).valid & io.in(j).bits.writeRf.asBool)
    val arfWrite        = !rdOverriden(j) & attemptArfWrite
    io.arfBus(j).valid := arfWrite

    val arfTagToCommitBus = io.in(j).bits.arfValid & arfWrite

    io.commitedBus(j).bits.tag := Mux(arfTagToCommitBus, io.in(j).bits.arfTag, io.in(j).bits.rdTag)
    io.commitedBus(j).valid    := Mux(arfTagToCommitBus, io.in(j).bits.arfValid, attemptArfWrite & io.in(j).bits.arfValid)

    dontTouch(io.in(j).bits.inst) // for testbench only
    dontTouch(io.in(j).bits.pcIdx) // for testbench only
  })
}
