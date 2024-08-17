package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{FreeList, TagBus}
import wood.std.DCRRQueue
import wood.util.WoodMIPipelineRegister

class MIStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val flush       = Input(Bool())
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new TagBus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val flist      = Module(new FreeList(config))
  val miq        = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))
  val pRegs      = Seq.fill(config.nWide)(Module(new WoodMIPipelineRegister(config, 2)))
  val self       = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val allInValid = Wire(Vec(config.nWide, Bool())).suggestName("allInValid")
  val allReady   = io.out.map(_.ready).reduce(_ && _)

  miq.io.in   <> io.in
  flist.io.in <> io.commitedBus

  for (j <- 0 until config.nWide) {
    self(j).bits         := miq.io.out(j).bits
    self(j).bits.rdTag   := flist.io.out(j).bits.tag
    self(j).bits.flushed := io.flush

    allInValid(j) := miq.io.out(j).valid & flist.io.out(j).valid // output is valid only if all inputs are valid
    self(j).valid := allInValid.asUInt.andR

    pRegs(j).io.valids(0) := miq.io.out(j).valid
    pRegs(j).io.valids(1) := flist.io.out(j).valid

    // io.in(j).ready        := self(j).ready // Decoupled from the rest via miqueue
    flist.io.out(j).ready := allInValid.asUInt.andR && self(j).ready
    miq.io.out(j).ready   := allInValid.asUInt.andR && self(j).ready

    pRegs(j).io.flush      := 0.B // never lose tags
    pRegs(j).io.setflushed := io.flush
    miq.io.flush           := io.flush

    pRegs(j).io.in        <> self(j)
    io.out(j)             <> pRegs(j).io.out
    pRegs(j).io.out.ready := allReady
  }
}
