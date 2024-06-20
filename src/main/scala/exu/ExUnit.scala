package wood.exu

import chisel3._
import chisel3.util._

object ExEngine extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExEngine.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExEngine.Type): String =
    toBitpat(op).rawString
}
