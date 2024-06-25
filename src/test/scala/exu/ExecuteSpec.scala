package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class ExecuteSpec extends AnyFlatSpec with ChiselScalatestTester {

  "ExecuteStage" should "emit Verilog" in {
    val numPorts = 4
    GenerateVerilog(new ExecuteStage(numPorts))
  }

}
