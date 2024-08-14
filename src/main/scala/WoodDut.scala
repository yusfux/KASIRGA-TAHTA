package wood

import chisel3._
import chisel3.util._
import wood.{MemPortR, MemPortW, WoodConfig}

class MainMemory(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memr = Flipped(new MemPortR(config))
    val memw = Flipped(new MemPortW(config))
  })
  val mem = SyncReadMem(config.memDepth, UInt(config.memDataWidth.W))

  when(io.memw.wreq.bits.enable & io.memw.wreq.valid) {
    mem.write(io.memw.wreq.bits.addr, io.memw.wreq.bits.data)
  }
  io.memr.req.ready  := true.B
  io.memw.wreq.ready := true.B

  io.memr.resp.bits.data := mem.read(io.memr.req.bits.addr)
  io.memr.resp.bits.addr := RegEnable(io.memr.req.bits.addr, io.memr.req.valid)
  io.memr.resp.valid     := RegNext(io.memr.req.valid)
  // io.memr.resp.ready     := true.B
}

class WoodDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memr = new MemPortR(config)
    val memw = Flipped(new MemPortW(config))
  })

  val mem  = Module(new MainMemory(config))
  val wood = Module(new Wood(config))

  io.memw            <> mem.io.memw
  io.memr.req.bits   := wood.io.memr.req.bits
  io.memr.req.valid  := wood.io.memr.req.valid
  io.memr.resp.ready := 1.B

  wood.io.memr <> mem.io.memr
}
