package alu

import chisel3._
import chisel3.util._

object ALUOp extends ChiselEnum {
  val sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass = Value
  val values = IndexedSeq(sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass)
}

class ALU(val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val control = Input(ALUOp())
    val value1 = Input(UInt(dataWidth.W))
    val value2 = Input(UInt(dataWidth.W))
    val result = Output(UInt(dataWidth.W))
  })

  val shamt = if (dataWidth > 1) log2Ceil(dataWidth) - 1 else 0 // Shift amount.

  val resultAdd = io.value1 + Mux(io.control === ALUOp.sub, ~io.value2, io.value2)

  io.result := 0.U
  switch(io.control) {
    is(ALUOp.sub) { io.result := resultAdd + 1.U }
    is(ALUOp.add) { io.result := resultAdd }
    is(ALUOp.xor) { io.result := io.value1 ^ io.value2 }
    is(ALUOp.or) { io.result := io.value1 | io.value2 }
    is(ALUOp.and) { io.result := io.value1 & io.value2 }
    is(ALUOp.sll) { io.result := io.value1 << io.value2(shamt, 0) }
    is(ALUOp.srl) { io.result := io.value1 >> io.value2(shamt, 0) }
    is(ALUOp.sra) { io.result := (io.value1.asSInt >> io.value2(shamt, 0)).asUInt }
    is(ALUOp.slt) { io.result := (io.value1.asSInt < io.value2.asSInt) }
    is(ALUOp.sltu) { io.result := (io.value1 < io.value2).asUInt }
    is(ALUOp.pass) { io.result := io.value2 }
  }
}
