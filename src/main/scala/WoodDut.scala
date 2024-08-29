package wood

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCArbiter

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
  val io  = IO(Flipped(new MemPort(config)))
  val mem = SyncReadMem(config.mmDepth, UInt(config.mmInterfaceWidth.W))

  when(io.req.valid && io.req.bits.wen) {
    mem.write(io.req.bits.addr >> (config.byteOffset + config.memOffset), io.req.bits.data)
  }
  io.req.ready := true.B

  io.resp.bits.data := mem.read(io.req.bits.addr >> (config.byteOffset + config.memOffset))
  io.resp.valid     := true.B
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

  val arbiter = Module(new DCArbiter(mem.io.req.bits.cloneType)(2, 1))

  arbiter.io.in(0).valid      := wood.io.icachemem.req.valid
  arbiter.io.in(0).bits.addr  := wood.io.icachemem.req.bits.addr
  arbiter.io.in(0).bits.data  := 0.U
  arbiter.io.in(0).bits.wen   := false.B
  wood.io.icachemem.req.ready := arbiter.io.in(0).ready

  arbiter.io.in(1) <> wood.io.dcachemem.req

  mem.io.req <> arbiter.io.out(0)

  wood.io.icachemem.resp.bits.data := mem.io.resp.bits.data
  wood.io.icachemem.resp.valid     := mem.io.resp.valid
  wood.io.dcachemem.resp.bits.data := mem.io.resp.bits.data
  wood.io.dcachemem.resp.valid     := mem.io.resp.valid
  mem.io.resp.ready                := true.B
}
