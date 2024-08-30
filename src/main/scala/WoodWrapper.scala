package wood

import chisel3._
import chisel3.util._
import wood.WoodConfig

class WoodWrapper(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val mem     = new MemPort(config)
    val uart_rx = Input(Bool())
    val uart_tx = Output(Bool())
  })

  val wood = Module(new Wood(config))

  wood.io.uart_rx := io.uart_rx
  io.uart_tx      := wood.io.uart_tx

  val idle :: icache :: dcache :: Nil = Enum(3)
  val state                           = RegInit(idle)

  io.mem.req.bits.addr := 0.U
  io.mem.req.bits.data := wood.io.dcachemem.req.bits.data
  io.mem.req.bits.wen  := wood.io.dcachemem.req.bits.wen
  io.mem.req.valid     := false.B
  io.mem.resp.ready    := false.B

  wood.io.icachemem.req.ready      := false.B
  wood.io.icachemem.resp.valid     := false.B
  wood.io.icachemem.resp.bits.data := 0.U
  wood.io.dcachemem.req.ready      := false.B
  wood.io.dcachemem.resp.valid     := false.B
  wood.io.dcachemem.resp.bits.data := 0.U
  switch(state) {
    is(idle) {
      when(wood.io.icachemem.req.valid) {
        state := icache
      }.elsewhen(wood.io.dcachemem.req.valid) {
        state := dcache
      }
    }
    is(icache) {
      io.mem.req.bits.addr        := wood.io.icachemem.req.bits.addr
      io.mem.req.valid            := wood.io.icachemem.req.valid
      wood.io.icachemem.req.ready := io.mem.req.ready
      io.mem.resp                 <> wood.io.icachemem.resp
      when(wood.io.icachemem.resp.fire) {
        state := idle
      }
    }
    is(dcache) {
      io.mem.req  <> wood.io.dcachemem.req
      io.mem.resp <> wood.io.dcachemem.resp
      when(wood.io.dcachemem.resp.fire) {
        state := idle
      }
    }
  }
}
