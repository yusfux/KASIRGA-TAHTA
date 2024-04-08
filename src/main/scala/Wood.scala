package wood

import chisel3._
import chisel3.util._

class TagBus(dataWidth: Int, tagWidth: Int) extends Bundle {
  val tag = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
}

class MicroOperation(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Bundle {
  val data1 = UInt(dataWidth.W)
  val data2 = UInt(dataWidth.W)
  val tag = UInt(tagWidth.W)
  val op = UInt(opWidth.W)
}
