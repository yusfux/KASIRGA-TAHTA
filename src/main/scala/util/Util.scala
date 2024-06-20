package wood.util

import _root_.circt.stage.ChiselStage
import chisel3._
import chiseltest.{IcarusBackendAnnotation, TreadleBackendAnnotation, VerilatorBackendAnnotation, WriteVcdAnnotation}

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

object GetBackendAnnotation {
  def apply() = {
    val backend = sys.props.getOrElse("backend", "verilator")
    val wave    = sys.props.get("wave").isDefined
    println(s"Chosen simulator: $backend")
    println(if (wave) "Waveforms enabled" else "Waveforms disabled")

    val baseAnnotations = backend match {
      case "verilator" => Seq(VerilatorBackendAnnotation)
      case "treadle"   => Seq(TreadleBackendAnnotation)
      case "iverilog"  => Seq(IcarusBackendAnnotation)
      // Add other simulators here
      case _ => throw new IllegalArgumentException(s"Unknown simulator: $backend")
    }

    if (wave) baseAnnotations :+ WriteVcdAnnotation else baseAnnotations
  }
}

object Fetch {
  val pcIndexWidth: Int = 6
}
