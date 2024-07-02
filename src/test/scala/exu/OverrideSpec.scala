package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.TestGenerateVerilog

class OverrideSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "OverrideFromBus" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new OverrideFromBus(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "OverrideFromBuses" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new OverrideFromBuses(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
