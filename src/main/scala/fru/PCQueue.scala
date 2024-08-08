package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig

class PCQueueIO(config: WoodConfig) extends Bundle {
  val in = Flipped(DecoupledIO(new Bundle {
    val fetchpc = UInt(config.pcWidth.W)
    val mask = Vec(config.nWide, Bool())
  }))

  val flush = Input(Bool())

  val out = DecoupledIO(new Bundle {
    val fetchpc = UInt(config.pcWidth.W)
    val mask = Vec(config.nWide, Bool())
  })
}

/* 
 TODO: check if we can enqueue in the same cycle we flush
 */
class PCQueue(config: WoodConfig) extends Module {
  val io = IO(new PCQueueIO(config))

  val queue = Module(new Queue(io.in.bits.cloneType, config.pcQueueDepth, flow=true, hasFlush=true))

  queue.io.flush.get := io.flush

  // queue.io.enq <> io.in will not work since we do not want to enqueue in with the flush signal
  //queue.io.enq.bits := io.in.bits
  //queue.io.enq.valid := io.in.valid && ~io.flush
  //io.in.ready := queue.io.enq.ready

  queue.io.enq <> io.in
  io.out <> queue.io.deq
}

