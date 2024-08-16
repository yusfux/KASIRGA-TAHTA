package wood

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}
import wood.util.GetBackendAnnotation
import scala.io.Source

class WoodSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  val mainmem = Source.fromFile(s"${os.pwd}/src/test/c/build/main.hex").getLines().toList
  val config = new WoodConfig(nWide = 4)

  val TEST_SIZE = 1024

  "WoodDut" should "work" in {
    test(new WoodDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.clock.setTimeout(0)
      dut.io.wreset.poke(true.B)
      for(i <- 0 until config.memDepth) {
        dut.io.memw.req.bits.data.poke(s"h${mainmem(i * 4 + 3)}_${mainmem(i * 4 + 2)}_${mainmem(i * 4 + 1)}_${mainmem(i * 4)}".U)
        dut.io.memw.req.bits.addr.poke((i).U)
        dut.io.memw.req.valid.poke(true.B)
        while(!dut.io.memw.req.ready.peekBoolean()) {
          step()
        }
        step()
      }
      dut.io.wreset.poke(false.B)

      step(10000)
    }
  }

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