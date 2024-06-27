package wood.util

import _root_.circt.stage.ChiselStage
import chisel3._
import chiseltest.simulator.VerilatorFlags
import chiseltest.{IcarusBackendAnnotation, TreadleBackendAnnotation, VerilatorBackendAnnotation, WriteVcdAnnotation}
import treadle2.MemoryToVCD

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
    val threads = sys.props.getOrElse("threads", "1")
    val wave    = sys.props.get("wave").isDefined
    println(s"Chosen simulator: $backend")
    println(s"Num threads: ${threads}")
    println(if (wave) "Waveforms enabled" else "Waveforms disabled")

    val baseAnnotations = backend match {
      case "verilator"   => Seq(VerilatorBackendAnnotation, VerilatorFlags(Seq("--threads", s"${threads}")))
      case "treadle"     => Seq(TreadleBackendAnnotation)
      case "treadle_mem" => Seq(TreadleBackendAnnotation, MemoryToVCD("all"))
      case "iverilog"    => Seq(IcarusBackendAnnotation)
      // Add other simulators here
      case _ => throw new IllegalArgumentException(s"Unknown simulator: $backend")
    }

    if (wave) baseAnnotations :+ WriteVcdAnnotation else baseAnnotations

  }
}
