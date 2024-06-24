package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.DecodeConfig

object ExEngine extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}

object ExConfig {
  val dataWidth = 32
  val prfDepth  = 128
  val rsDepth   = 4 // Reservation station depth
  val tagWidth  = log2Ceil(prfDepth)
}

class TagBus extends Bundle {
  val data  = UInt(ExConfig.dataWidth.W)
  val tag   = UInt(ExConfig.tagWidth.W)
  val valid = UInt(1.W)
}

class WriteBack extends Bundle {
  val tag  = UInt(ExConfig.tagWidth.W)
  val data = UInt(ExConfig.dataWidth.W)
}

class MicroOperation extends Bundle {
  val data1 = UInt(ExConfig.dataWidth.W)
  val data2 = UInt(ExConfig.dataWidth.W)
  val tag   = UInt(ExConfig.tagWidth.W)
  val op    = UInt(DecodeConfig.op.maxWidth.W)
}
