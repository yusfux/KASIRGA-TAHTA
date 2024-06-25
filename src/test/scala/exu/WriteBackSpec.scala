package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class WriteBackSpec extends AnyFlatSpec with ChiselScalatestTester {

  "WriteBackStage" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new WriteBackStage(numPorts))
  }

}
