package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class ExUnitSpec extends AnyFlatSpec with ChiselScalatestTester {

  "ExUnit" should "emit Verilog" in {
    val numPorts = 4
    GenerateVerilog(new ExUnit(numPorts))
  }

}
