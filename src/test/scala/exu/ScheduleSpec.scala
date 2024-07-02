package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.TestGenerateVerilog
import wood.{TestConfig, WoodConfig}

class ScheduleSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ScheduleStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ScheduleStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "OverrideTagValid" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new OverrideFromBus(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ValidList" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ValidList(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
