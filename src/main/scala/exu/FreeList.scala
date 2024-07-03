package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCInitializer, DCRRQueue}

class FreeList(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out = Vec(config.nWide, Decoupled(new Bus(config)))
  })

  val initializer = Module(new DCInitializer(config.nWide, config.prfDepth, config.dataWidth, "addr"))
  val q           = Module(new DCRRQueue(new Bus(config))(config.nWide, config.prfDepth))

  (0 until config.nWide).foreach(j => {
    initializer.io.in(j).bits.addr   := io.in(j).bits.tag
    initializer.io.in(j).bits.data   := io.in(j).bits.data
    initializer.io.in(j).bits.enable := io.in(j).valid
    initializer.io.in(j).valid       := io.in(j).valid
    io.in(j).ready                   := initializer.io.in(j).ready

    q.io.in(j).bits.data        := initializer.io.out(j).bits.data
    q.io.in(j).bits.tag         := initializer.io.out(j).bits.addr
    q.io.in(j).valid            := initializer.io.out(j).valid
    initializer.io.out(j).ready := q.io.in(j).ready
  })

  q.io.out <> io.out
}
