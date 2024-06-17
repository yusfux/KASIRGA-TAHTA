package wood

import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._

class TagBus(dataWidth: Int, tagWidth: Int) extends Bundle {
  val tag  = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
}

class WriteBack(dataWidth: Int, tagWidth: Int) extends Bundle {
  val tag  = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
}

class MicroOperation(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Bundle {
  val data1 = UInt(dataWidth.W)
  val data2 = UInt(dataWidth.W)
  val tag   = UInt(tagWidth.W)
  val op    = UInt(opWidth.W)
}

class MicroInstruction(dataWidth: Int, tagWidth: Int, opWidth: Int) extends Bundle {
  val readtag1 = UInt(tagWidth.W)
  val readtag2 = UInt(tagWidth.W)
  val writetag = UInt(tagWidth.W)
  val data     = UInt(dataWidth.W)
  val tag      = UInt(tagWidth.W)
  val op       = UInt(opWidth.W)
}

object GenerateVerilog {
  def apply(gen: => RawModule, path: String = ""): Unit = {
    val projectDir = System.getProperty("user.dir")

    val verilogDir = s"$projectDir/build"
    val file_path = if (path.trim().nonEmpty) {
      path
    } else {
      verilogDir + '/' + "test.sv"
    }

    val x = ChiselStage.emitSystemVerilog(
      gen = gen,
      firtoolOpts = Array(
        "--disable-all-randomization",
        "--strip-debug-info",
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--split-verilog",
        "--lowering-options=disallowLocalVariables",
        "--lower-memories",
        // "--ignore-read-enable-mem",
        "-o=" + file_path,
        "-O=release"
      )
    )
  }
}

object ExUnit extends ChiselEnum {
  val alu, lsu, float, none = Value
  val values                = IndexedSeq(alu, lsu, float, none)

  def toBitpat(op: ExUnit.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def toString(op: ExUnit.Type): String =
    toBitpat(op).rawString
}

object Fetch {
  val pcIndexWidth: Int = 6
}
