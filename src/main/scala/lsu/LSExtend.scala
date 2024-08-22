package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig

class LSExtend(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inData = Input(UInt(config.xlen.W))
    val lsOp   = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val out    = Output(UInt(config.xlen.W))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val result = Wire(UInt(config.xlen.W))

  result := DontCare
// format: off
  switch(control) {
    is(LSOp.lb)  { result := Cat(Fill(24,io.inData( 7)),io.inData( 7,0)) }
    is(LSOp.lh)  { result := Cat(Fill(16,io.inData(15)),io.inData(15,0)) }
    is(LSOp.lw)  { result :=                            io.inData        }
    is(LSOp.lbu) { result := Cat(Fill(24,          0.U),io.inData( 7,0)) }
    is(LSOp.lhu) { result := Cat(Fill(16,          0.U),io.inData(15,0)) }
  }
// format: on
  io.out := result
}
