package wood.exu

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}
import wood.fru.PCInst

class ExUnitDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new PCInst(config))))
    val bpBus = Vec(config.nWide, ValidIO(new BranchPredictorBus(config)))
  })

  val exunit = Module(new ExUnit(config))
  val mem    = SyncReadMem(config.mmDepth, UInt(config.mmInterfaceWidth.W))

  exunit.io.bpBus <> io.bpBus
  exunit.io.in    <> io.in

  exunit.io.mem.req.ready      := true.B
  exunit.io.mem.resp.valid     := Mux(RegNext(exunit.io.mem.req.valid && exunit.io.mem.req.bits.wen), false.B, true.B)
  exunit.io.mem.resp.bits.data := mem.read(exunit.io.mem.req.bits.addr >> (config.byteOffset + config.memOffset))

  when(exunit.io.mem.req.valid && exunit.io.mem.req.bits.wen) {
    mem.write(exunit.io.mem.req.bits.addr >> (config.byteOffset + config.memOffset), exunit.io.mem.req.bits.data)
  }
}

class ExUnitSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  // "ExUnit" should "emit for cocotb" in {
  //   val nWide        = sys.props.getOrElse("nWide", "1").toInt
  //   val robDepth     = sys.props.getOrElse("robDepth", "4").toInt
  //   val rsDepth      = 2
  //   val miQueueDepth = sys.props.getOrElse("miQueueDepth", "1").toInt
  //   // val pcListDepth  = sys.props.getOrElse("pcListDepth", "32").toInt // TODO

  //   val config =
  //     new WoodConfig(nWide = nWide, robDepth = robDepth, rsDepth = rsDepth, miQueueDepth = miQueueDepth)

  //   val testRunDir = s"test_run_dir/ExUnit_should_emit_for_cocotb"
  //   val dir        = new java.io.File(testRunDir)
  //   if (!dir.exists()) {
  //     dir.mkdirs()
  //   }
  //   GenerateVerilog(new ExUnit(config), path = dir.toString())
  // }

  "ExUnitDut" should "emit for cocotb" in {
    val nWide        = sys.props.getOrElse("nWide", "1").toInt
    val robDepth     = sys.props.getOrElse("robDepth", "4").toInt
    val rsDepth      = 2
    val miQueueDepth = sys.props.getOrElse("miQueueDepth", "1").toInt
    // val pcListDepth  = sys.props.getOrElse("pcListDepth", "32").toInt // TODO

    val config =
      new WoodConfig(nWide = nWide, robDepth = robDepth, rsDepth = rsDepth, miQueueDepth = miQueueDepth)

    val testRunDir = s"test_run_dir/ExUnitDut_should_emit_for_cocotb"
    val dir        = new java.io.File(testRunDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    GenerateVerilog(new ExUnitDut(config), path = dir.toString())
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ExUnit" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ExUnit(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
