package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.TestGenerateVerilog
import wood.{TestConfig, WoodConfig}
// import wood.util.GenerateVerilog

class RegisterReadSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()

  // "RegisterReadStage" should s"emit Verilog for cocotb" in {
  //   val config = new WoodConfig(nWide = 2)
  //   GenerateVerilog(new RegisterReadStage(config))
  // }

  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "RegisterReadStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new RegisterReadStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
