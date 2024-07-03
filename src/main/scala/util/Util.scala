package wood.util

import _root_.circt.stage.ChiselStage
import chisel3._
import chiseltest.simulator.VerilatorFlags
import chiseltest.{IcarusBackendAnnotation, TreadleBackendAnnotation, VerilatorBackendAnnotation, WriteVcdAnnotation}
import treadle2.MemoryToVCD

object GetGroupedSequences { // It is easier to follow sequences in a waveform
  def apply(numGroups: Int, numDataPerGroup: Int): List[List[UInt]] = {
    // In round robin fashion
    val numData      = numDataPerGroup * numGroups
    val datas        = (0 until numData).toList.map(_.U)
    val groupedLines = Array.fill(numGroups)(List[UInt]())

    for ((data, index) <- datas.zipWithIndex) {
      groupedLines(index % numGroups) = groupedLines(index % numGroups) :+ data
    }

    groupedLines.map(_.padTo(numDataPerGroup, 0.U)).toList
  }
}

object TestGenerateVerilog {
  def apply(gen: => RawModule, testNames: Set[String], testIndex: Int): Unit = {
    val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(testIndex - 1)
    val testRunDir      = s"test_run_dir/$currentTestName"
    val dir             = new java.io.File(testRunDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    GenerateVerilog(gen, path = testRunDir)
  }
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

    ChiselStage.emitSystemVerilog(
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
