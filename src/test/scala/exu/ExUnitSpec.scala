package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.TestGenerateVerilog

class ExUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ExUnit" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ExUnit(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
