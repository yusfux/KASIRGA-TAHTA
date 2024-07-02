package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.TestGenerateVerilog
import wood.{TestConfig, WoodConfig}

class WriteBackSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "WriteBackStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new WriteBackStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
