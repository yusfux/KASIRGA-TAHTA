package wood.fru

// import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.TestGenerateVerilog

class FrUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "FrUnit" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new FrUnit(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
