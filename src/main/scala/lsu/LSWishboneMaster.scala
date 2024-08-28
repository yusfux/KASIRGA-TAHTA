package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class LSWishboneMaster(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSCMI(config)))
    val out = Decoupled(new LSCMI(config))

    // Wishbone master interface
    val wb_adr   = Output(UInt(4.W))
    val wb_dat_o = Output(UInt(32.W))
    val wb_we    = Output(Bool())
    val wb_stb   = Output(Bool())
    val wb_sel   = Output(UInt(4.W))
    val wb_cyc   = Output(Bool())
    val wb_ack   = Input(Bool())
    val wb_dat_i = Input(UInt(32.W))
  })

  // State machine
  val sIdle :: sRequest :: sWaitAck :: Nil = Enum(3)
  val state                                = RegInit(sIdle)

  // Default values
  io.wb_adr   := 0.U
  io.wb_dat_o := 0.U
  io.wb_we    := false.B
  io.wb_stb   := false.B
  io.wb_sel   := 0.U
  io.wb_cyc   := false.B

  io.in.ready  := false.B
  io.out.valid := false.B

  // Latch input for preserving data
  val latchedInputNext = Wire(new LSCMI(config))
  val latchedInput     = RegEnable(latchedInputNext, 0.U.asTypeOf(new LSCMI(config)), 1.B)

  // Output assignment (preserving all fields)
  io.out.bits := latchedInput

  latchedInputNext := latchedInput

  switch(state) {
    is(sIdle) {
      io.in.ready := true.B
      when(io.in.fire) {
        latchedInputNext := io.in.bits
        io.wb_adr        := io.in.bits.addr(3, 0)
        io.wb_dat_o      := io.in.bits.cacheData.asUInt
        io.wb_we         := io.in.bits.store
        io.wb_stb        := true.B
        io.wb_sel        := io.in.bits.cacheData.asUInt
        io.wb_cyc        := true.B
        state            := sRequest
      }.otherwise {
        latchedInputNext := latchedInput
      }
    }

    is(sRequest) {
      io.wb_stb := true.B
      io.wb_cyc := true.B
      when(io.wb_ack) {
        state := sWaitAck
      }
    }

    is(sWaitAck) {
      io.out.valid          := true.B
      io.out.bits.cacheData := io.wb_dat_i.asTypeOf(Vec(config.numBytes, UInt(8.W)))
      when(io.out.fire) {
        state := sIdle
      }
    }
  }
}
