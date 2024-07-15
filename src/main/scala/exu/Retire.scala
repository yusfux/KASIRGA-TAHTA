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

  val q     = Module(new DCRRQueue(new MI(config))(config.nWide, config.prfDepth))
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
  val writebackOverriders       = Seq.fill(config.nWide)(Module(new OverrideRdFromBus(config)))
  val retiredStatusRegisterFile = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))

  val self                       = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val overridenRetiredStatusData = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  val allRetired = Wire(Vec(config.nWide, Bool()))
  val allInValid = Wire(Vec(config.nWide, Bool()))
  allRetired := writebackOverriders.map(_.io.out.bits.retired.asBool)
  allInValid := io.in.map(_.valid)

  overridenRetiredStatusData <> io.in

  (0 until config.nWide).foreach(j => {
    writebackOverriders(j).io.inBus := io.writebackBus.map { bus =>
      val dBus = Wire(ValidIO(new DataBus(config)))
      dBus.bits.tag  := bus.bits.tag
      dBus.valid     := bus.valid
      dBus.bits.data := DontCare
      dBus
    }
    overridenRetiredStatusData(j).bits.retired := retiredStatusRegisterFile(io.in(j).bits.rdTag)
    writebackOverriders(j).io.in               <> overridenRetiredStatusData(j)

    when(io.writebackBus(j).valid) {
      retiredStatusRegisterFile(io.writebackBus(j).bits.tag) := 1.U
    }
    when(io.commitedBus(j).valid) {
      retiredStatusRegisterFile(io.commitedBus(j).bits.tag) := 0.U
    }

    self(j).bits  := writebackOverriders(j).io.out.bits
    self(j).valid := allInValid.asUInt.andR & allRetired.asUInt.andR

    pRegs(j).io.valids(0) := io.in(j).valid

    writebackOverriders(j).io.out.ready := self(j).ready
    io.in(j).ready                      := self(j).ready

    pRegs(j).io.in <> self(j) //overrideWriteBack.io.out(j)
    io.out(j)      <> pRegs(j).io.out
  })
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val commitedBus = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val archRegisterFile      = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))
  val archRegisterFileValid = RegInit(VecInit(Seq.fill(32)(0.U(1.W))))
  val pRegs                 = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new Tag(config))(1)))

  val self = Wire(Vec(config.nWide, Decoupled(new Tag(config))))

  (0 until config.nWide).foreach(j => {
    val validWrite = io.in(j).bits.writeRf.asBool & io.in(j).valid

    when(validWrite) {
      archRegisterFile(io.in(j).bits.rd)      := io.in(j).bits.rdTag
      archRegisterFileValid(io.in(j).bits.rd) := 1.U
    }

    self(j).bits.tag := Mux(validWrite, archRegisterFile(io.in(j).bits.rd), io.in(j).bits.rd)
    self(j).valid    := io.in(j).valid & Mux(validWrite, archRegisterFileValid(io.in(j).bits.rd), io.in(j).valid)

    pRegs(j).io.valids(0) := io.in(j).valid

    io.in(j).ready := self(j).ready

    pRegs(j).io.in    <> self(j)
    io.commitedBus(j) <> pRegs(j).io.out

    dontTouch(io.in(j).bits.inst) // for testbench only
  })
}
