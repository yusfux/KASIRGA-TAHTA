package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.{BlockRAMParams, DCArbiter, DCRRQueue, DecoupledBlockRAM}

class ROBStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(new MI())))
    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val q = Module(new DCRRQueue(new MI())(numPorts, ExConfig.prfDepth))
  q.io.in  <> io.in
  q.io.out <> io.out
}

class RetiredStatusStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in                    = Flipped(Vec(numPorts, Decoupled(new MI())))
    val tagBuses              = Flipped(Vec(numPorts, Decoupled(new Tag())))
    val previousRetiredStatus = Flipped(Vec(numPorts, Decoupled(new MI())))

    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val arbiters = Seq.tabulate(numPorts) { j =>
    Module(new DCArbiter(UInt(1.W))(2, 1))
  }

  val retiredStatusRegisterFile = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(ExConfig.prfDepth, numPorts, (numPorts * 2))
    )
  )

  val readyForTag    = Wire(Vec(numPorts, Bool()))
  val readyForCommit = Wire(Vec(numPorts, Bool()))

  (0 until numPorts).foreach(j => {
    readyForTag(j)       := retiredStatusRegisterFile.io.wp(j).ready & arbiters(j).io.in(1).ready
    io.tagBuses(j).ready := readyForTag(j)

    readyForCommit(j)                 := retiredStatusRegisterFile.io.wp(j + numPorts).ready
    io.previousRetiredStatus(j).ready := readyForCommit(j)
  })

  (0 until numPorts).foreach(j => {
    io.out(j).bits         := io.in(j).bits
    io.out(j).valid        := io.in(j).valid
    io.out(j).bits.retired := arbiters(j).io.out(0).bits

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

  (0 until numPorts).foreach(j => {
    retiredStatusRegisterFile.io.wp(j).bits.addr   := io.tagBuses(j).bits.tag
    retiredStatusRegisterFile.io.wp(j).valid       := io.tagBuses(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.enable := io.tagBuses(j).valid
    retiredStatusRegisterFile.io.wp(j).bits.data   := 1.U

    retiredStatusRegisterFile.io.wp(j + numPorts).bits.addr   := io.previousRetiredStatus(j).bits.rd_tag
    retiredStatusRegisterFile.io.wp(j + numPorts).valid       := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + numPorts).bits.enable := io.previousRetiredStatus(j).valid
    retiredStatusRegisterFile.io.wp(j + numPorts).bits.data   := 0.U

    io.tagBuses(j).ready := retiredStatusRegisterFile.io.wp(j).ready
  })
}

class ArchRegisterFileStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(numPorts, Decoupled(new MI())))

    val previousRetiredStatus = Vec(numPorts, Decoupled(new MI()))
    val retiredBus            = Vec(numPorts, Decoupled(new Tag()))
  })

  val arfDepth = 32
  val archRegisterFile = Module(
    new DecoupledBlockRAM(new Tag())(
      BlockRAMParams(arfDepth, numPorts, numPorts)
    )
  )

  (0 until numPorts).foreach(j => {
    io.previousRetiredStatus(j).bits  := io.in(j).bits
    io.previousRetiredStatus(j).valid := io.in(j).valid

    archRegisterFile.io.wp(j).bits.addr     := io.in(j).bits.rd
    archRegisterFile.io.wp(j).valid         := io.in(j).valid
    archRegisterFile.io.wp(j).bits.enable   := io.in(j).valid
    archRegisterFile.io.wp(j).bits.data.tag := io.in(j).bits.rd_tag

    archRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rd
    archRegisterFile.io.rip(j).valid     := io.in(j).valid
    io.in(j).ready                       := archRegisterFile.io.rip(j).ready

    io.retiredBus(j).bits.tag        := archRegisterFile.io.rop(j).bits.data.tag
    io.retiredBus(j).valid           := archRegisterFile.io.rop(j).valid
    archRegisterFile.io.rop(j).ready := io.retiredBus(j).ready
  })
}
