package wood

import chisel3._
import chisel3.util._
import wood.WoodConfig

class MemPort(config: WoodConfig) extends Bundle {
  val req = DecoupledIO(new Bundle {
    val addr = UInt(32.W)
    val data = UInt(config.mmInterfaceWidth.W)
    val wen  = Bool()
  })
  val resp = Flipped(DecoupledIO(new Bundle {
    val data = UInt(config.mmInterfaceWidth.W)
  }))
}

class MainMemory(config: WoodConfig) extends Module {
  val io = IO(Flipped(new MemPort(config)))

  val mem = SyncReadMem(config.mmDepth, UInt(config.mmInterfaceWidth.W))

  when(io.req.fire && io.req.bits.wen) {
    mem.write(io.req.bits.addr >> (config.memOffset + config.byteOffset), io.req.bits.data)
  }

  val addr = RegEnable(io.req.bits.addr, 0.U, io.req.fire)
  io.resp.bits.data := mem.read(Mux(io.req.fire, io.req.bits.addr, addr) >> (config.memOffset + config.byteOffset))

  val respValid = RegInit(false.B)
  val reqReady  = RegInit(true.B)
  when(io.req.fire) {
    respValid := true.B
    reqReady  := false.B
  }
  when(io.resp.fire) {
    respValid := false.B
    reqReady  := true.B
  }

  io.resp.valid := respValid
  io.req.ready  := reqReady
}

class WoodDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val wreset  = Input(Bool())
    val uart_rx = Input(Bool())
    val uart_tx = Output(Bool())
  })

  val mem  = Module(new MainMemory(config))
  val wood = Module(new Wood(config))

  wood.reset      := io.wreset
  wood.io.uart_rx := io.uart_rx
  io.uart_tx      := wood.io.uart_tx

  val idle :: icache :: dcache :: Nil = Enum(3)
  val state                           = RegInit(idle)

  mem.io.req.bits.addr := 0.U
  mem.io.req.bits.data := wood.io.dcachemem.req.bits.data
  mem.io.req.bits.wen  := wood.io.dcachemem.req.bits.wen
  mem.io.req.valid     := false.B
  mem.io.resp.ready    := false.B

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
      mem.io.req.bits.addr        := wood.io.icachemem.req.bits.addr
      mem.io.req.valid            := wood.io.icachemem.req.valid
      wood.io.icachemem.req.ready := mem.io.req.ready
      mem.io.resp                 <> wood.io.icachemem.resp
      when(wood.io.icachemem.resp.fire) {
        state := idle
      }
    }
    is(dcache) {
      mem.io.req  <> wood.io.dcachemem.req
      mem.io.resp <> wood.io.dcachemem.resp
      when(wood.io.dcachemem.resp.fire) {
        state := idle
      }
    }
  }
}
