package wood.exu

import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.TestGenerateVerilog

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
    val config = new WoodConfig(nWide = j)
    "Decoder" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new Decoder(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "DecodeStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new DecodeStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
