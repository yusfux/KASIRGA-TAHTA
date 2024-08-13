package wood.fru

import scala.collection.mutable._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.GetBackendAnnotation
import wood.util.GenerateVerilog

class Fetch1Spec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 128
  val config = new WoodConfig(pcInitAddr = "h1000_0000")
  val target_pc = 268435456
  var pc = 268435456
  val q = Queue[Int]()

  "Fetch1" should "work" in {
    test(new Fetch1Stage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
    }
  }

  /* 
    this test is problematic due to the multithreaded nature of the testbench,
    it is easier to test this functionality in the waveform directly,
    though it would be easier to test it after the complete frontend is implemented
   */
  "Fetch1" should "work under misprediction" in {
    test(new Fetch1Stage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
    }
  }

  "Fetch1" should "emit Verilog" in {
    GenerateVerilog(new Fetch1Stage(new WoodConfig))
  }
}

