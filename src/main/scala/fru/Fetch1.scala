package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.BranchPredictorBus

class Fetch1Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus    = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val pcPacket = DecoupledIO(Vec(config.nWide, new PCMask(config)))
  })

  val bpred   = Module(new BranchPredictor(config))
  val pcqueue = Module(new PCQueue(config))

  val pc = RegInit(config.pcInitAddr.U(config.xlen.W))

  val mispredict = io.bpBus.map(bp => bp.valid && bp.bits.mispredict).reduce(_ || _)
  val exception  = io.bpBus.map(bp => bp.valid && bp.bits.exception).reduce(_ || _)
  val pcidx      = PriorityEncoder(io.bpBus.map(bp => bp.valid && (bp.bits.mispredict || bp.bits.exception)))

  bpred.io.bpBus <> io.bpBus
  bpred.io.pc    := pc

  pcqueue.io.in.valid        := !(mispredict || exception)
  pcqueue.io.in.bits.fetchpc := pc
  pcqueue.io.in.bits.mask    := VecInit(Seq.fill(config.nWide)(true.B))
  pcqueue.io.flush           := mispredict || exception

  pcqueue.io.out.ready := io.pcPacket.ready
  io.pcPacket.valid    := pcqueue.io.out.valid
  for (i <- 0 until config.nWide) {
    io.pcPacket.bits(i).pc    := pcqueue.io.out.bits.fetchpc + (i * 4).U
    io.pcPacket.bits(i).valid := pcqueue.io.out.bits.mask(i)
  }

  when(mispredict || exception) {
    pc := io.bpBus(pcidx).bits.targetPC
  }.elsewhen(bpred.io.pred.en && pcqueue.io.in.ready) {
    pc := bpred.io.pred.fetchpc
  }.elsewhen(pcqueue.io.in.ready) {
    pc := pc + (config.nWide * 4).U
  }
}
