package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}

class ExUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  "ExUnit" should "emit for cocotb" in {
    val nWide        = sys.props.getOrElse("nWide", "1").toInt
    val robDepth     = sys.props.getOrElse("robDepth", "2").toInt
    val rsDepth      = 2
    val miQueueDepth = sys.props.getOrElse("miQueueDepth", "1").toInt
    // val pcListDepth  = sys.props.getOrElse("pcListDepth", "32").toInt // TODO

    val config =
      new WoodConfig(nWide = nWide, robDepth = robDepth, rsDepth = rsDepth, miQueueDepth = miQueueDepth)

    val testRunDir = s"test_run_dir/ExUnit_should_emit_for_cocotb"
    val dir        = new java.io.File(testRunDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    GenerateVerilog(new ExUnit(config), path = dir.toString())
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ExUnit" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ExUnit(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
