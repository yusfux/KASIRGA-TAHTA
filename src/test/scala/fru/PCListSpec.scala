package wood.fru

//import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.GetBackendAnnotation
import wood.util.GenerateVerilog

class PCListSpec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 1024

  "PCList" should "work" in {
    test(new PCList(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      for (i <- 0 until TEST_SIZE) {
        step()
      }
    }
  }

  "PCList" should "emit Verilog" in {
    GenerateVerilog(new PCList(new WoodConfig))
  }
}
