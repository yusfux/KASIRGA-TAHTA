package wood

import chisel3._
import wood.{MemPortR, MemPortW, WoodConfig}

class MainMemory(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memr = Flipped(new MemPortR(config))
    val memw = new MemPortW(config)
  })
  val mem = SyncReadMem(config.mmDepth, UInt(config.mmInterfaceWidth.W))

  when(io.memw.req.valid) {
    mem.write(io.memw.req.bits.addr >> (config.byteOffset + config.memOffset), io.memw.req.bits.data)
  }
  io.memr.req.ready := true.B
  io.memw.req.ready := true.B

  io.memr.resp.bits.data := mem.read(io.memr.req.bits.addr >> (config.byteOffset + config.memOffset))
  io.memr.resp.valid     := true.B
}

class WoodDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memw   = new MemPortW(config)
    val wreset = Input(Bool())
  })

  val mem  = Module(new MainMemory(config))
  val wood = Module(new Wood(config))

  wood.reset := io.wreset

  io.memw <> mem.io.memw

  mem.io.memr.req.bits   := wood.io.memr.req.bits
  mem.io.memr.req.valid  := wood.io.memr.req.valid
  wood.io.memr.req.ready := mem.io.memr.req.ready

  mem.io.memr.resp.ready  := 1.B
  wood.io.memr.resp.bits  := mem.io.memr.resp.bits
  wood.io.memr.resp.valid := mem.io.memr.resp.valid
}
