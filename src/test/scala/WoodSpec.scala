package wood

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}

class WoodSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  "Wood" should "emit for cocotb" in {
    val nWide = sys.props.getOrElse("nWide", "1").toInt
    // val prfDepth     = sys.props.getOrElse("prfDepth", "32").toInt
    val robDepth     = 16
    val rsDepth      = 2
    val miQueueDepth = sys.props.getOrElse("miQueueDepth", "1").toInt
    // val pcListDepth  = sys.props.getOrElse("pcListDepth", "32").toInt // TODO

    val config =
      new WoodConfig(nWide = nWide, robDepth = robDepth, rsDepth = rsDepth, miQueueDepth = miQueueDepth)

    val testRunDir = s"test_run_dir/Wood_should_emit_for_cocotb"
    val dir        = new java.io.File(testRunDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    GenerateVerilog(new Wood(config), path = dir.toString())
  }

  "WoodDut" should "emit for cocotb" in {
    val nWide = sys.props.getOrElse("nWide", "1").toInt
    // val prfDepth     = sys.props.getOrElse("prfDepth", "32").toInt
    val robDepth     = 16
    val rsDepth      = 2
    val miQueueDepth = sys.props.getOrElse("miQueueDepth", "1").toInt
    // val pcListDepth  = sys.props.getOrElse("pcListDepth", "32").toInt // TODO

    val config =
      new WoodConfig(nWide = nWide, robDepth = robDepth, rsDepth = rsDepth, miQueueDepth = miQueueDepth)

    val testRunDir = s"test_run_dir/WoodDut_should_emit_for_cocotb"
    val dir        = new java.io.File(testRunDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    GenerateVerilog(new WoodDut(config), path = dir.toString())
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "Wood" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new Wood(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })

}
