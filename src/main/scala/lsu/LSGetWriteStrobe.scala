package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig

class LSGetWriteStrobe(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val lsOp = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val addr = Input(UInt(config.xlen.W))
    val out  = Output(Vec(config.numBytes, Bool()))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val wStrobeWord = Wire(UInt(config.numBytes.W))

  wStrobeWord := 0.U
  // format: off
  switch(control) {
    is(LSOp.lb,LSOp.lbu,LSOp.lh,LSOp.lhu,LSOp.lw ) { wStrobeWord := "b0000".U }
    is(LSOp.sb)                                    { wStrobeWord := "b0001".U }
    is(LSOp.sh)                                    { wStrobeWord := "b0011".U }
    is(LSOp.sw)                                    { wStrobeWord := "b1111".U }
  }
  // format: on

  io.out := (wStrobeWord << io.addr(1, 0)).asTypeOf(Vec(config.numBytes, Bool()))
}
