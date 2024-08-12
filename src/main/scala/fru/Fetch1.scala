package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig

class Fetch1IO(config: WoodConfig) extends Bundle {
  val in = Input(new Bundle {
    val exception = new Bundle {
      val en = Bool()                 // exception happened
      val pc = UInt(config.pcWidth.W) // new pc to fetch after the exception (i.e., exception handler pc from mepc)
    }

    val mispred = new Bundle {
      val pc = UInt(config.pcWidth.W)       // pc of the mispredicted branch
      val targetpc = UInt(config.pcWidth.W) // target pc of the mispredicted branch
      val en = Bool()                       // misprediction happened
      val taken = Bool()                    // mispredicted branch was taken
    }
  })

  val out = DecoupledIO(new Bundle {
    val controller = Vec(config.nWide, new Bundle() {
      val pc = UInt(config.pcWidth.W)
      val mask = Bool()
    })
  })

  val debug = Output(new Bundle {
    val pred_en = Bool()
    val queue_ready = Bool()
  })
}

/* 
  when the PCQueue is full, we do not write the program coutner to the queue obviously
  but we also do NOT register the current program counter, so any new pc coming from
  misprediction or exception will be accepted and we will lose the current program counter.

  it may be problematic even though i can't think of any reason because we will abort the
  program counters in the queues anyway if we get an exception, don't we?
 */
class Fetch1Stage(config: WoodConfig) extends Module {
  val io = IO(new Fetch1IO(config))

  val bpred = Module(new BranchPredictor(config))
  val pcqueue = Module(new PCQueue(config))
  val baddrgen = Module(new BankAddrGen(config))

  val pc = RegInit(config.pcInitAddr.U(config.pcWidth.W))

  when(io.in.exception.en) {
    pc := io.in.exception.pc
  }.elsewhen(io.in.mispred.en) {
    pc := io.in.mispred.targetpc
  }.elsewhen(bpred.io.out.pred.en && pcqueue.io.in.ready) {
    pc := bpred.io.out.pred.fetchpc
  }.elsewhen(pcqueue.io.in.ready) {
    pc := pc + (config.nWide * 4).U
  }

  bpred.io.in.fetchpc := pc
  bpred.io.in.mispred.fetchpc := io.in.mispred.pc(config.pcWidth - 1, config.byteOffset + config.bankOffset)
  bpred.io.in.mispred.pcidx := io.in.mispred.pc(config.byteOffset + config.bankOffset - 1, config.byteOffset)
  bpred.io.in.mispred.en := io.in.mispred.en
  bpred.io.in.mispred.taken := io.in.mispred.taken
  bpred.io.in.mispred.targetpc := io.in.mispred.targetpc

  pcqueue.io.in.bits.fetchpc := pc
  pcqueue.io.in.bits.mask := Mux(bpred.io.out.pred.en, bpred.io.out.pred.mask, VecInit(Seq.fill(config.nWide)(true.B)))
  pcqueue.io.in.valid := true.B
  pcqueue.io.flush := io.in.mispred.en || io.in.exception.en
  pcqueue.io.out.ready := io.out.ready

  baddrgen.io.in.fetchpc := pcqueue.io.out.bits.fetchpc

  for(i <- 0 until config.nWide) {
    io.out.bits.controller(i).pc := baddrgen.io.out.controller(i).pc
    io.out.bits.controller(i).mask := pcqueue.io.out.bits.mask(i)
  }
  io.out.valid := pcqueue.io.out.valid

  io.debug.pred_en := bpred.io.out.pred.en
  io.debug.queue_ready := pcqueue.io.in.ready
}

