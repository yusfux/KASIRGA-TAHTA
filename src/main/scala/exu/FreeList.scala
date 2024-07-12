package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCQueueInitializer, DCRRQueue}

class FreeList(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val initializer = Module(new DCQueueInitializer(new Tag(config))(config.nWide, (config.prfDepth - 1), "count+1"))
  val q           = Module(new DCRRQueue(new Tag(config))(config.nWide, config.prfDepth, unique = true))

  (0 until config.nWide).foreach(j => {
    initializer.io.in(j).bits.tag := io.in(j).bits.tag
    initializer.io.in(j).valid    := io.in(j).valid
    // initializer.io.in(j).ready // always ready

    q.io.in(j).bits.tag         := initializer.io.out(j).bits.tag
    q.io.in(j).valid            := initializer.io.out(j).valid
    initializer.io.out(j).ready := q.io.in(j).ready
  })

  q.io.out <> io.out
}
