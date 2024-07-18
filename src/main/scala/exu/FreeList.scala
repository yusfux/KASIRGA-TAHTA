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

  val qDepth      = ((config.prfDepth + config.nWide - 1) / config.nWide)
  val initializer = Module(new DCQueueInitializer(new Tag(config))(config.nWide, (config.prfDepth - 1), "count+1"))
  val q           = Module(new DCRRQueue(new Tag(config))(config.nWide, qDepth, unique = true))

  initializer.io.in <> io.in
  q.io.in           <> initializer.io.out
  io.out            <> q.io.out
}
