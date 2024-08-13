package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCQueueInitializer, DCRRQueue}

class FreeList(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val out = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val flistDepth  = config.prfDepth / config.nWide
  val numWrites   = config.prfDepth - config.nWide // edge case
  val initializer = Module(new DCQueueInitializer(new Tag(config))(config.nWide, numWrites, "count+1"))
  val q           = Module(new DCRRQueue(new Tag(config))(config.nWide, flistDepth, unique = true, flow = false))

  q.io.flush := 0.B

  initializer.io.in <> io.in
  q.io.in           <> initializer.io.out
  io.out            <> q.io.out

  (0 until config.nWide).foreach(j => {
    q.io.out(j).ready := io.out(j).ready
    io.out(j).valid   := q.io.out(j).valid
  })
}
