package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.TestGenerateVerilog

class RetireSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ROBStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ROBStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "RetireStatusStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new RetireStatusStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ArchRegisterFileStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ArchRegisterFileStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "RetireWritebackStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new RetireWritebackStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
