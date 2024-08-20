package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.ALUOp

class ALUAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSMI(config)))
    val out = Decoupled(new LSMI(config))
  })

  val rawOp = Wire(UInt(ALUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, valid) = ALUOp.safe(rawOp)

  val data1   = io.in.bits.rs2Data.asUInt
  val memData = io.in.bits.memDataRead.asUInt

  val addrOffset  = (io.in.bits.addr(log2Ceil(config.memDataWidth / 8) - 1, 2))
  val shiftAmount = addrOffset * (config.xlen).U

  // Extract the relevant xlen-bit segment from memData
  val data2 = (memData >> shiftAmount)(config.xlen, 0)

// format: off
  val result = Wire(UInt(config.xlen.W))
  result := DontCare
  switch(control) {
    is(ALUOp.add)  { result := data1 + data2 }
    is(ALUOp.xor)  { result := data1 ^ data2 }
    is(ALUOp.or)   { result := data1 | data2 }
    is(ALUOp.and)  { result := data1 & data2 }
    is(ALUOp.pass) { result := data2         }
  }

  // Prepare the output data
  val outputData = Wire(Vec(config.memDataWidth / 8, UInt(8.W)))
  outputData := io.in.bits.memDataRead

  // Update only the relevant 32-bit segment
  val mask = ("b" + ("1" * config.xlen)).U << shiftAmount
  val updatedMemData = (memData & ~mask) | (result.asUInt << shiftAmount)
  outputData := updatedMemData.asTypeOf(Vec(config.memDataWidth / 8, UInt(8.W)))

  io.out.bits              := io.in.bits
  io.out.bits.memDataWrite := outputData
  io.out.valid             := io.in.valid
  io.in.ready              := io.out.ready
// format: on
}
