package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class RegisterReadSpec extends AnyFlatSpec with ChiselScalatestTester {

  "RegisterReadStage" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new RegisterReadStage(numPorts))
  }

}
