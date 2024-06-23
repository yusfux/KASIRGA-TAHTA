package wood.exu

import chisel3._
import chisel3.util._
import wood.std.{DCArbiter, DCRRQueue}

class FreeListInitializer(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val out = Vec(numPorts, Decoupled(UInt(ExConfig.tagWidth.W)))
  })

  val counter     = RegInit(0.U(ExConfig.tagWidth.W))
  val initialized = RegInit(false.B)

  val out_ready = Wire(Vec(numPorts, Bool()))
  out_ready := io.out.map(_.ready)

  val ready     = out_ready.asUInt.andR
  val was_ready = RegNext(ready)

  when(!initialized && ready) {
    counter := counter + numPorts.U
  }

  when(!ready && was_ready && !initialized) {
    initialized := 1.U
  }

  (0 until numPorts).foreach(j => {
    io.out(j).bits  := counter + j.asUInt
    io.out(j).valid := !initialized
  })
}

class FreeList(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(UInt(ExConfig.tagWidth.W))))
    val out = Vec(numPorts, Decoupled(UInt(ExConfig.tagWidth.W)))
  })
  val numWritePorts = numPorts

  val initializer = Module(new FreeListInitializer(numPorts))
  val arbiter     = Module(new DCArbiter(UInt(ExConfig.tagWidth.W))(numPorts * 2, numPorts))
  val q           = Module(new DCRRQueue(UInt(ExConfig.tagWidth.W))(numPorts, ExConfig.prfDepth))

  (0 until numPorts).foreach(j => {
    arbiter.io.in(j)            <> initializer.io.out(j)
    arbiter.io.in(j + numPorts) <> io.in(j)
  })

  q.io.in  <> arbiter.io.out
  q.io.out <> io.out
}
