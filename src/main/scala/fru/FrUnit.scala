package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.BranchPredictorBus
import wood.std.{ReadPortI, ReadPortO}

//TODO: make this competiable with other io interfaces, remove hardware generation (i.e., DecoupledIO)
class MemPort(config: WoodConfig) extends Bundle {
  val req  = DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth))
  val resp = Flipped(DecoupledIO(new ReadPortO(UInt(config.memDataWidth.W))(config.addrWidth)))
}

//TODO: make this competiable with other io interfaces, remove hardware generation (i.e., DecoupledIO)
class CorePort(config: WoodConfig) extends Bundle {
  val req = Flipped(DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth)))
  val resp = DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth))
}

class PCQueueEntry(config: WoodConfig) extends Bundle {
  val fetchpc = UInt(config.pcWidth.W)
  val mask    = Vec(config.nWide, Bool())
}

class PCInst(config: WoodConfig) extends Bundle {
  val pc   = UInt(config.pcWidth.W)
  val inst = UInt(config.xlen.W)
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus        = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val mem          = new MemPort(config)
    val instPacket   = Vec(config.nWide, Decoupled(new PCInst(config)))
  })

  val f1stage = Module(new Fetch1Stage(config))
  val f2stage = Module(new Fetch2Stage(config))
  val flush   = io.bpBus.map(_.valid).reduce(_ | _)

  f1stage.io.bpBus <> io.bpBus
  f2stage.io.pc    <> f1stage.io.pc
  f2stage.io.mem   <> io.mem
  io.instPacket    <> f2stage.io.instPacket

  (0 until config.nWide) foreach { i =>
    f2stage.io.pc(i).valid := RegNext(f1stage.io.pc(i).valid) && ~flush
    f2stage.io.pc(i).bits  := RegNext(f1stage.io.pc(i).bits)
    f1stage.io.pc(i).ready := RegNext(f2stage.io.pc(i).ready) && ~flush
  }
}