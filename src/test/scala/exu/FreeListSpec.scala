package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.GetBackendAnnotation
import wood.WoodConfig
import wood.TestConfig
import wood.util.GenerateVerilog

class FreeListSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig(nWide = 2)

  "FreeListInitializer" should "work with 2 inputs" in {
    val numPorts = 2
    test(new FreeListInitializer(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(1) })
      step(100)
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
    }
  }

  "FreeList" should "work with 2 inputs" in {
    val numPorts = 2
    test(new FreeList(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "FreeList" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new FreeList(config), path = testRunDir)
    }
  })

  (1 to tconfig.maxWidth).foreach(j => {
    "FreeListInitializer" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new FreeListInitializer(config), path = testRunDir)
    }
  })

}
