package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.BranchPredictorBus

class Fetch1Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val pc    = Vec(config.nWide, Decoupled(UInt(config.pcWidth.W)))
  })

  val bpred   = Module(new BranchPredictor(config))
  val pcqueue = Module(new PCQueue(config))

  val brvalid = io.bpBus.map(_.valid).reduce(_ | _)
  val bridx   = PriorityEncoder(io.bpBus.map(_.valid))

  val pc = RegInit(config.pcInitAddr.U(config.pcWidth.W))

  when(brvalid) {
    pc := io.bpBus(bridx).bits.targetPC
  }.elsewhen(bpred.io.pred.en && pcqueue.io.in.ready) {
    pc := bpred.io.pred.fetchpc
  }.elsewhen(pcqueue.io.in.ready) {
    pc := pc + (config.nWide * 4).U
  }

  bpred.io.bpBus <> io.bpBus

  pcqueue.io.in.bits.fetchpc := pc
  pcqueue.io.in.bits.mask    := Mux(bpred.io.pred.en, bpred.io.pred.mask, VecInit(Seq.fill(config.nWide)(true.B)))
  pcqueue.io.in.valid        := true.B
  pcqueue.io.flush           := brvalid
  pcqueue.io.out.ready       := io.pc.map(_.ready).reduce(_ & _)


  (0 until config.nWide) foreach { i => io.pc(i).bits  := pcqueue.io.out.bits.fetchpc + (i * 4).U }
  (0 until config.nWide) foreach { i => io.pc(i).valid := pcqueue.io.out.bits.mask(i) && pcqueue.io.out.valid }
  
}

