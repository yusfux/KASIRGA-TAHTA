package add

import chisel3._
import chisel3.util._

class Add extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val c = Output(UInt(8.W))
  })

  val reg = RegInit(0.U(8.W))
  reg := io.a + io.b

  io.c := reg
}

object AddMain extends App {
  println("Generating the adder hardware")
  emitVerilog(new Add(), Array("--target-dir", "generated"))
}
