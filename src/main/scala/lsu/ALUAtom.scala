package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class ALUAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSCMI(config)))
    val out = Decoupled(new LSCMI(config))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.in.bits.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val data1   = io.in.bits.rs2Data.asUInt
  val memData = io.in.bits.cacheLine.asUInt

  val addrOffset  = (io.in.bits.addr(log2Ceil(config.mmInterfaceWidth / 8) - 1, 2))
  val shiftAmount = addrOffset * (config.xlen).U

  // Extract the relevant xlen-bit segment from memData
  val data2 = (memData >> shiftAmount)(config.xlen, 0)

// format: off
  val result = Wire(UInt(config.xlen.W))
  result := DontCare
  switch(control) {
    is(LSOp.amoadd)  { result := data1 + data2 }
    is(LSOp.amoxor)  { result := data1 ^ data2 }
    is(LSOp.amoor)   { result := data1 | data2 }
    is(LSOp.amoand)  { result := data1 & data2 }
    is(LSOp.amoswap) { result := data2         }
    is(LSOp.amomax)  { result := Mux((data1.asSInt > data2.asSInt),data1,data2)}
    is(LSOp.amomaxu) { result := Mux((data1        > data2       ),data1,data2)}
    is(LSOp.amomin)  { result := Mux((data1.asSInt < data2.asSInt),data1,data2)}
    is(LSOp.amominu) { result := Mux((data1        < data2       ),data1,data2)}
  }
// format: on

  // Prepare the output data
  val outputData = Wire(Vec(config.mmInterfaceWidth / 8, UInt(8.W)))
  outputData := io.in.bits.cacheLine

  // Update only the relevant 32-bit segment
  val mask           = ("b" + ("1" * config.xlen)).U << shiftAmount
  val updatedMemData = (memData & ~mask) | (result.asUInt << shiftAmount)
  outputData := updatedMemData.asTypeOf(Vec(config.mmInterfaceWidth / 8, UInt(8.W)))

  io.out.bits           := io.in.bits
  io.out.bits.cacheLine := outputData
  io.out.valid          := io.in.valid
  io.in.ready           := io.out.ready
}
