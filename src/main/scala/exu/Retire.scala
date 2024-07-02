package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DCPipelineRegister, MI}
import wood.std.{BlockRAMParams, DCArbiter, DCRRQueue, DecoupledBlockRAM}

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
  val arbiters = Seq.tabulate(config.nWide) { j =>
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

  val pReg = Module(new DCPipelineRegister(new Bus(config))(config.nWide))
  val archRegisterFile = Module(
    new DecoupledBlockRAM(new Bus(config))(
      BlockRAMParams(32, config.nWide, config.nWide)
    )
  )

  (0 until config.nWide).foreach(j => {
    io.previousRetiredStatus(j).bits  := io.in(j).bits
    io.previousRetiredStatus(j).valid := io.in(j).valid

    archRegisterFile.io.wp(j).bits.addr      := io.in(j).bits.rd
    archRegisterFile.io.wp(j).valid          := io.in(j).valid
    archRegisterFile.io.wp(j).bits.enable    := io.in(j).valid
    archRegisterFile.io.wp(j).bits.data.tag  := io.in(j).bits.rdTag
    archRegisterFile.io.wp(j).bits.data.data := io.in(j).bits.rdData

    archRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rd
    archRegisterFile.io.rip(j).valid     := io.in(j).valid
    io.in(j).ready                       := archRegisterFile.io.rip(j).ready
  })

  val commitedBusNext = Wire(Vec(config.nWide, Decoupled(new Bus(config))))
  (0 until config.nWide).foreach(j => {
    commitedBusNext(j).bits.tag      := archRegisterFile.io.rop(j).bits.data.tag
    commitedBusNext(j).bits.data     := archRegisterFile.io.rop(j).bits.data.data
    commitedBusNext(j).valid         := archRegisterFile.io.rop(j).valid
    archRegisterFile.io.rop(j).ready := commitedBusNext(j).ready

    dontTouch(io.in(j).bits.inst) // for testbench only
  })

  pReg.io.in     <> commitedBusNext
  io.commitedBus <> pReg.io.out
}
