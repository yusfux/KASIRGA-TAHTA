package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAMParams, DCArbiter, DCBus, DCInitializer, DCPipelineRegister, DCRRQueue, DecoupledBlockRAM}

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
    val writeBackBus          = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val previousRetiredStatus = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pReg = Module(new DCPipelineRegister(new MI(config))(config.nWide))
  val arbiters = Seq.tabulate(config.nWide) { _ =>
    Module(new DCArbiter(UInt(1.W))(2, 1))
  }

  val retiredStatusRegisterFile = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(config.prfDepth, config.nWide, (config.nWide * 2))
    )
  )

  val readyForTag    = Wire(Vec(config.nWide, Bool()))
  val readyForCommit = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach(j => {
    readyForTag(j)           := retiredStatusRegisterFile.io.wp(j).ready & arbiters(j).io.in(1).ready
    io.writeBackBus(j).ready := readyForTag(j)

    readyForCommit(j)                 := retiredStatusRegisterFile.io.wp(j + config.nWide).ready
    io.previousRetiredStatus(j).ready := readyForCommit(j)
  })

  (0 until config.nWide).foreach(j => {

    retiredStatusRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rdTag
    retiredStatusRegisterFile.io.rip(j).valid     := io.in(j).valid
    io.in(j).ready                                := retiredStatusRegisterFile.io.rip(j).ready
    arbiters(j).io.in(0).bits                     := retiredStatusRegisterFile.io.rop(j).bits.data
    arbiters(j).io.in(0).valid                    := retiredStatusRegisterFile.io.rop(j).valid
    retiredStatusRegisterFile.io.rop(j).ready     := arbiters(j).io.in(0).ready

    arbiters(j).io.in(1).bits  := 1.U
    arbiters(j).io.in(1).valid := io.writeBackBus(j).bits.tag === io.in(j).bits.rdTag

    arbiters(j).io.out(0).ready := io.out(j).valid
  })

  (0 until config.nWide).foreach(j => {
    retiredStatusRegisterFile.io.wp(j).bits.addr   := io.writeBackBus(j).bits.tag
    retiredStatusRegisterFile.io.wp(j).valid       := io.writeBackBus(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.enable := io.writeBackBus(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.data   := 1.U

    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.addr   := io.previousRetiredStatus(j).bits.rdTag
    retiredStatusRegisterFile.io.wp(j + config.nWide).valid       := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.enable := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.data   := 0.U

    io.writeBackBus(j).ready := retiredStatusRegisterFile.io.wp(j).ready
  })

  val outNext = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  (0 until config.nWide).foreach(j => {
    outNext(j).bits         := io.in(j).bits
    outNext(j).bits.retired := arbiters(j).io.out(0).bits
    outNext(j).valid        := io.in(j).valid
    io.in(j).ready          := outNext(j).ready
  })

  pReg.io.in <> outNext
  io.out     <> pReg.io.out
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val previousRetiredStatus = Vec(config.nWide, Decoupled(new MI(config)))
    val commitedBus           = Vec(config.nWide, Decoupled(new Bus(config)))
  })

  val pReg           = Module(new DCPipelineRegister(new Bus(config))(config.nWide))
  val initializer    = Module(new DCInitializer(config.nWide, 32, 1, "zero"))
  val inBus          = Module(new DCBus(new MI(config))(config.nWide, 4))
  val inArchWp       = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val inArchRip      = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val inArchValidWp  = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val inArchValidRip = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  inBus.io.in    <> io.in
  inArchWp       <> inBus.io.out(0)
  inArchRip      <> inBus.io.out(1)
  inArchValidWp  <> inBus.io.out(2)
  inArchValidRip <> inBus.io.out(3)

  val archRegisterFile = Module(
    new DecoupledBlockRAM(new Bus(config))(
      BlockRAMParams(32, config.nWide, config.nWide)
    )
  )
  val archRegisterFileValid = Module(
    new DecoupledBlockRAM(Bool())(
      BlockRAMParams(32, config.nWide, config.nWide)
    )
  )

  (0 until config.nWide).foreach(j => {
    io.previousRetiredStatus(j).bits  := io.in(j).bits
    io.previousRetiredStatus(j).valid := io.in(j).valid

    archRegisterFile.io.wp(j).bits.addr      := inArchWp(j).bits.rd
    archRegisterFile.io.wp(j).bits.enable    := inArchWp(j).bits.writeRf
    archRegisterFile.io.wp(j).bits.data.tag  := inArchWp(j).bits.rdTag
    archRegisterFile.io.wp(j).bits.data.data := DontCare
    archRegisterFile.io.wp(j).valid          := inArchWp(j).valid
    inArchWp(j).ready                        := archRegisterFile.io.wp(j).ready

    initializer.io.in(j).bits.addr   := inArchValidWp(j).bits.rd
    initializer.io.in(j).bits.enable := inArchValidWp(j).bits.writeRf
    initializer.io.in(j).bits.data   := inArchValidWp(j).valid
    initializer.io.in(j).valid       := inArchValidWp(j).valid
    inArchValidWp(j).ready           := initializer.io.in(j).ready

    archRegisterFileValid.io.wp(j) <> initializer.io.out(j)

    archRegisterFile.io.rip(j).bits.addr := inArchRip(j).bits.rd
    archRegisterFile.io.rip(j).valid     := inArchRip(j).valid
    inArchRip(j).ready                   := archRegisterFile.io.rip(j).ready

    archRegisterFileValid.io.rip(j).bits.addr := inArchValidRip(j).bits.rd
    archRegisterFileValid.io.rip(j).valid     := inArchValidRip(j).valid
    inArchValidRip(j).ready                   := archRegisterFileValid.io.rip(j).ready
  })

  val commitedBusNext = Wire(Vec(config.nWide, Decoupled(new Bus(config))))
  (0 until config.nWide).foreach(j => {
    commitedBusNext(j).bits.tag           := archRegisterFile.io.rop(j).bits.data.tag
    commitedBusNext(j).bits.data          := archRegisterFile.io.rop(j).bits.data.data
    commitedBusNext(j).valid              := archRegisterFile.io.rop(j).valid
    archRegisterFile.io.rop(j).ready      := commitedBusNext(j).ready | (!archRegisterFileValid.io.rop(j).bits.data)
    archRegisterFileValid.io.rop(j).ready := commitedBusNext(j).ready | (!archRegisterFileValid.io.rop(j).bits.data)

    dontTouch(io.in(j).bits.inst) // for testbench only
  })

  pReg.io.in     <> commitedBusNext
  io.commitedBus <> pReg.io.out
}
