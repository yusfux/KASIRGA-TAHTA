package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig

class LSExtendWriteStrobe(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val lsOp = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val addr = Input(UInt(config.xlen.W))
    val out  = Output(Vec(config.numDCacheLineBytes, Bool()))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val wStrobeWord      = Wire(UInt(config.numDCacheLineBytes.W))
  val wStrobeCacheLine = Wire(UInt(config.numDCacheLineBytes.W))
  val padSize          = config.numDCacheLineBytes - 4

  wStrobeWord := 0.U
  // format: off
  switch(control) {
    is(LSOp.lb,LSOp.lbu,LSOp.lh,LSOp.lhu,LSOp.lw ) { wStrobeWord := Cat(Fill(padSize, 0.B),"b0000".U) }
    is(LSOp.sb)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b0001".U) }
    is(LSOp.sh)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b0011".U) }
    is(LSOp.sw)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b1111".U) }
  }
  // format: on

  val shiftAmount = io.addr(log2Ceil(config.numDCacheLineBytes) - 1, 0)

  wStrobeCacheLine := (wStrobeWord << shiftAmount)(config.numDCacheLineBytes - 1, 0)

  io.out := wStrobeCacheLine.asBools
}
