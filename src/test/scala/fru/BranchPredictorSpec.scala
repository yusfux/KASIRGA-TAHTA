package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class BranchPredictorSpec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 1024
  val config = new WoodConfig()
  val PC = "h1000_0000".U
  val PCXD = 268435456
  var pc = 268435456

  /* 
    TODO: only the main functionality is tested, more comprehensive tests should be added asap
   */
  "BranchPredictor" should "work" in {
    test(new BranchPredictor(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
    }
  }

  "BranchPredictor" should "emit Verilog" in {
    GenerateVerilog(new BranchPredictor(new WoodConfig))
  }
}