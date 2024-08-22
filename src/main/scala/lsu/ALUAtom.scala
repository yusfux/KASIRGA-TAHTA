package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig

class ALUAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val rs2  = Input(UInt(config.xlen.W))
    val mem  = Input(UInt(config.xlen.W))
    val lsOp = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val out  = Output(UInt(config.xlen.W))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val data1 = io.mem
  val data2 = io.rs2

// format: off
  val result = Wire(UInt(config.xlen.W))
  result := DontCare
  switch(control) {
    is(LSOp.amoswap,LSOp.sw,LSOp.sh,LSOp.sb) { result := data2         }
    is(LSOp.amoadd)                          { result := data1 + data2 }
    is(LSOp.amoxor)                          { result := data1 ^ data2 }
    is(LSOp.amoor)                           { result := data1 | data2 }
    is(LSOp.amoand)                          { result := data1 & data2 }
    is(LSOp.amomax)                          { result := Mux((data1.asSInt > data2.asSInt),data1,data2)}
    is(LSOp.amomaxu)                         { result := Mux((data1        > data2       ),data1,data2)}
    is(LSOp.amomin)                          { result := Mux((data1.asSInt < data2.asSInt),data1,data2)}
    is(LSOp.amominu)                         { result := Mux((data1        < data2       ),data1,data2)}
  }
// format: on

  io.out := result
}
