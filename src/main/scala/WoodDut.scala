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
    val uart_rx = Input(Bool())
    val uart_tx = Output(Bool())
  })

  val mem  = Module(new MainMemory(config))
  val wood = Module(new WoodWrapper(config))

  wood.io.uart_rx := io.uart_rx
  io.uart_tx      := wood.io.uart_tx

  mem.io <> wood.io.mem
}
