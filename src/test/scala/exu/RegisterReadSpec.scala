package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.TestGenerateVerilog
import wood.{TestConfig, WoodConfig}

class RegisterReadSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "RegisterReadStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new RegisterReadStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
