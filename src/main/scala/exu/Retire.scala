package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAMParams, DCArbiter, DCRRQueue, DecoupledBlockRAM}

class ROBStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val q = Module(new DCRRQueue(new MI(config))(config.nWide, config.prfDepth))

  val out_ready = Wire(Vec(config.nWide, Bool()))
  val out_valid = Wire(Vec(config.nWide, Bool()))
  out_ready := io.out.map(_.ready)
  out_valid := io.in.map(_.valid)
  val valid = out_valid.asUInt.andR
  val ready = out_ready.asUInt.andR
  val stall = !(valid && ready)

  (0 until config.nWide).foreach(j => {
    io.out(j).bits    := RegEnable(q.io.out(j).bits, 0.U.asTypeOf(new MI(config)), !stall)
    io.out(j).valid   := RegEnable(q.io.out(j).valid, 0.B, !stall)
    q.io.out(j).ready := io.out(j).ready
  })
  q.io.in <> io.in
}

class RetiredStatusStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in                    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val tagBuses              = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val previousRetiredStatus = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

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
    readyForTag(j)       := retiredStatusRegisterFile.io.wp(j).ready & arbiters(j).io.in(1).ready
    io.tagBuses(j).ready := readyForTag(j)

    readyForCommit(j)                 := retiredStatusRegisterFile.io.wp(j + config.nWide).ready
    io.previousRetiredStatus(j).ready := readyForCommit(j)
  })

  (0 until config.nWide).foreach(j => {

    retiredStatusRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rd_tag
    retiredStatusRegisterFile.io.rip(j).valid     := io.in(j).valid
    io.in(j).ready                                := retiredStatusRegisterFile.io.rip(j).ready
    arbiters(j).io.in(0).bits                     := retiredStatusRegisterFile.io.rop(j).bits.data
    arbiters(j).io.in(0).valid                    := retiredStatusRegisterFile.io.rop(j).valid
    retiredStatusRegisterFile.io.rop(j).ready     := arbiters(j).io.in(0).ready

    arbiters(j).io.in(1).bits  := 1.U
    arbiters(j).io.in(1).valid := io.tagBuses(j).bits.tag === io.in(j).bits.rd_tag

    arbiters(j).io.out(0).ready := io.out(j).valid
  })

  (0 until config.nWide).foreach(j => {
    retiredStatusRegisterFile.io.wp(j).bits.addr   := io.tagBuses(j).bits.tag
    retiredStatusRegisterFile.io.wp(j).valid       := io.tagBuses(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.enable := io.tagBuses(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.data   := 1.U

    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.addr   := io.previousRetiredStatus(j).bits.rd_tag
    retiredStatusRegisterFile.io.wp(j + config.nWide).valid       := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.enable := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + config.nWide).bits.data   := 0.U

    io.tagBuses(j).ready := retiredStatusRegisterFile.io.wp(j).ready
  })

  val out_ready = Wire(Vec(config.nWide, Bool()))
  val out_valid = Wire(Vec(config.nWide, Bool()))
  out_ready := io.out.map(_.ready)
  out_valid := io.in.map(_.valid)
  val valid = out_valid.asUInt.andR
  val ready = out_ready.asUInt.andR
  val stall = !(valid && ready)

  val outNext = Wire(Vec(config.nWide, new MI(config)))

  (0 until config.nWide).foreach(j => {
    outNext(j)         := io.in(j).bits
    outNext(j).retired := arbiters(j).io.out(0).bits

    io.out(j).bits  := RegEnable(outNext(j), 0.U.asTypeOf(new MI(config)), !stall)
    io.out(j).valid := RegEnable(io.in(j).valid, 0.B, !stall)
    io.in(j).ready  := io.out(j).ready
  })
}

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(config.nWide, Decoupled(new MI(config))))

    val previousRetiredStatus = Vec(config.nWide, Decoupled(new MI(config)))
    val retiredBus            = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val arfDepth = 32
  val archRegisterFile = Module(
    new DecoupledBlockRAM(new Tag(config))(
      BlockRAMParams(arfDepth, config.nWide, config.nWide)
    )
  )

  (0 until config.nWide).foreach(j => {
    io.previousRetiredStatus(j).bits  := io.in(j).bits
    io.previousRetiredStatus(j).valid := io.in(j).valid

    archRegisterFile.io.wp(j).bits.addr     := io.in(j).bits.rd
    archRegisterFile.io.wp(j).valid         := io.in(j).valid
    archRegisterFile.io.wp(j).bits.enable   := io.in(j).valid
    archRegisterFile.io.wp(j).bits.data.tag := io.in(j).bits.rd_tag

    archRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rd
    archRegisterFile.io.rip(j).valid     := io.in(j).valid
    io.in(j).ready                       := archRegisterFile.io.rip(j).ready
  })

  val out_ready = Wire(Vec(config.nWide, Bool()))
  val out_valid = Wire(Vec(config.nWide, Bool()))
  out_valid := io.retiredBus.map(_.valid)
  out_ready := io.retiredBus.map(_.ready)
  val valid = out_valid.asUInt.andR
  val ready = out_ready.asUInt.andR
  val stall = !(valid && ready)

  val retiredBusNext = Wire(Vec(config.nWide, new Tag(config)))
  (0 until config.nWide).foreach(j => {
    retiredBusNext(j).tag := archRegisterFile.io.rop(j).bits.data.tag

    io.retiredBus(j).bits            := RegEnable(retiredBusNext(j), 0.U.asTypeOf(new Tag(config)), !stall)
    io.retiredBus(j).valid           := RegEnable(archRegisterFile.io.rop(j).valid, 0.B, !stall)
    archRegisterFile.io.rop(j).ready := io.retiredBus(j).ready
  })

}
