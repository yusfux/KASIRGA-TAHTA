package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCArbiter, DCRRQueue}

class FreeListInitializer(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val out = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val counter     = RegInit(0.U(config.tagWidth.W))
  val initialized = RegInit(false.B)

  val out_ready = Wire(Vec(config.nWide, Bool()))
  out_ready := io.out.map(_.ready)

  val ready     = out_ready.asUInt.andR
  val was_ready = RegNext(ready)

  when(!initialized && ready) {
    counter := counter + config.nWide.U
  }

  val stopCount = Wire(UInt(log2Ceil(config.prfDepth).W))
  stopCount := ((1.U << config.prfDepth.asUInt) - 1.U)

  when((counter === stopCount) && !initialized) {
    initialized := 1.U
  }

  (0 until config.nWide).foreach(j => {
    io.out(j).bits.tag := counter + j.asUInt
    io.out(j).valid    := !initialized
  })
}

class FreeList(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val out = Vec(config.nWide, Decoupled(new Tag(config)))
  })
  val numWritePorts = config.nWide

  val initializer = Module(new FreeListInitializer(config))
  val arbiter     = Module(new DCArbiter(new Tag(config))(config.nWide * 2, config.nWide))
  val q           = Module(new DCRRQueue(new Tag(config))(config.nWide, config.prfDepth))

  (0 until config.nWide).foreach(j => {
    arbiter.io.in(j)                <> initializer.io.out(j)
    arbiter.io.in(j + config.nWide) <> io.in(j)
  })

  q.io.in  <> arbiter.io.out
  q.io.out <> io.out
}
