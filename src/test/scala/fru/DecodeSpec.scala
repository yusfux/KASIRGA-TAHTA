package wood.fru

import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec

import wood.util.GenerateVerilog
import wood.fru.{DecodeConfig, Decoder}
import wood.WoodConfig
import wood.TestConfig
import org.scalatest.ParallelTestExecution

class DecodeSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  "Decoder" should "display its config" in {
    println(s"defaultDecSeq: ${DecodeConfig.defaultDecSeq}")
    println(s"defaultDec: ${DecodeConfig.defaultDec}")
    println(s"width: ${DecodeConfig.width}")
    println(s"subWidths: ${DecodeConfig.subWidths}")
    for (range <- DecodeConfig.bitRanges) {
      println(s"bitRanges: ${range}")
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "Decoder" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new Decoder(config), path = testRunDir)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    "DecodeStage" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new DecodeStage(config), path = testRunDir)
    }
  })
}
