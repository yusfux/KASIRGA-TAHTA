package wood.exu

import chisel3._
import chiseltest._
import wood.fru.MI
import wood.WoodConfig

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.GetBackendAnnotation
import wood.util.GenerateVerilog
import wood.TestConfig
import org.scalatest.ParallelTestExecution

class RenameStageSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val config = new WoodConfig(nWide = 2)

  "RenameStage" should "work with 2 inputs" in {
    test(new RenameStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inMIs         = dut.io.in.map(_.initSource())
      val inFlistRetire = dut.io.in.map(_.initSource())
      val outSinks      = dut.io.out.map(_.initSink())

      val inst1 = MI(config, 0.U, Map("rs1" -> 1.U, "rs2" -> 2.U))
      val inst2 = MI(config, 0.U, Map("rs1" -> 1.U, "rs2" -> 2.U))

      fork {
        inMIs(0).enqueue(inst1)
      }.fork {
        inMIs(1).enqueue(inst2)
      }.fork {
        // outSinks(0).expectDequeue(inst1)
        step(100)
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "RenameStage" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new RenameStage(config), path = testRunDir)
    }
  })
}
