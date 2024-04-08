package alu

import circt.stage.ChiselStage
import chisel3._
import chisel3.util._

import wood._

object ALUOp extends ChiselEnum {
  val sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass = Value
  val values = IndexedSeq(sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass)
}

class ALU(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Module {
  val io = IO(new Bundle {
    val microOp = Flipped(Decoupled(new MicroOperation(dataWidth, tagWidth, opWidth)))
    val tagBus = Decoupled(new TagBus(dataWidth, tagWidth))
  })

  val shamt = if (dataWidth > 1) log2Ceil(dataWidth) - 1 else 0 // Shift amount.

  val (control, valid) = ALUOp.safe(io.microOp.bits.op)
  assert(valid, "Enum state must be valid, got %d!", io.microOp.bits.op)

  val data1 = io.microOp.bits.data1
  val data2 = io.microOp.bits.data2

  val arithmeticData1 = Mux(control === ALUOp.sub, Cat(data1, 1.U(1.W)), Cat(data1, 0.U(1.W)))
  val arithmeticData2 = Mux(control === ALUOp.sub, Cat(~data2, 1.U(1.W)), Cat(data2, 0.U(1.W)))
  val resultAdd = arithmeticData1 + arithmeticData2

  val result = Wire(UInt(dataWidth.W))
  result := 0.U
  switch(control) {
    is(ALUOp.sub, ALUOp.add) { result := resultAdd(dataWidth, 1) }
    is(ALUOp.xor) { result := data1 ^ data2 }
    is(ALUOp.or) { result := data1 | data2 }
    is(ALUOp.and) { result := data1 & data2 }
    is(ALUOp.sll) { result := data1 << data2(shamt, 0) }
    is(ALUOp.srl) { result := data1 >> data2(shamt, 0) }
    is(ALUOp.sra) { result := (data1.asSInt >> data2(shamt, 0)).asUInt }
    is(ALUOp.slt) { result := (data1.asSInt < data2.asSInt) }
    is(ALUOp.sltu) { result := (data1 < data2).asUInt }
    is(ALUOp.pass) { result := data2 }
  }
  io.tagBus.bits.data := result
  io.tagBus.bits.tag := io.microOp.bits.tag
  io.tagBus.valid := io.microOp.valid
  io.microOp.ready := 1.U(1.W)
}

object ALUMain extends App {
  ChiselStage.emitSystemVerilogFile(gen = new ALU(32, 5, 4), args = Array("-td", "generated"))
}
