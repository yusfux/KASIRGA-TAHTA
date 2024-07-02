package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GetBackendAnnotation, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}

class FreeListSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "FreeList" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new FreeList(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })

  "FreeList" should "work with 2 inputs" in {
    val config   = new WoodConfig(nWide = 2)
    val numPorts = 2
    test(new FreeList(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
    }
  }
}
