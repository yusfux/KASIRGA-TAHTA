package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.PCQueueEntry

class PCQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(DecoupledIO(new PCQueueEntry(config)))
    val flush = Input(Bool())
    val out   = DecoupledIO(new PCQueueEntry(config))
  })

  val queue = Module(new Queue(io.in.bits.cloneType, config.pcQueueDepth, flow=true, hasFlush=true))

  queue.io.flush.get := io.flush

  queue.io.enq <> io.in
  io.out <> queue.io.deq
}

