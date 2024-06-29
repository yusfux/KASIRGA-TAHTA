package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCArbiter, DCInitializer, DCRRQueue}

class FreeList(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out = Vec(config.nWide, Decoupled(new Bus(config)))
  })
  val numWritePorts = config.nWide

  val initializer = Module(new DCInitializer(config.nWide, config.prfDepth, config.dataWidth, "addr"))
  val arbiter     = Module(new DCArbiter(new Bus(config))(config.nWide * 2, config.nWide))
  val q           = Module(new DCRRQueue(new Bus(config))(config.nWide, config.prfDepth))

  (0 until config.nWide).foreach(j => {
    arbiter.io.in(j).bits.data  := initializer.io.out(j).bits.data
    arbiter.io.in(j).bits.tag   := initializer.io.out(j).bits.addr
    arbiter.io.in(j).valid      := initializer.io.out(j).bits.enable & initializer.io.out(j).valid
    initializer.io.out(j).ready := arbiter.io.in(j).valid

    arbiter.io.in(j + config.nWide) <> io.in(j)
  })

  q.io.in  <> arbiter.io.out
  q.io.out <> io.out
}
