package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class RetireSpec extends AnyFlatSpec with ChiselScalatestTester {

  "ROBStage" should "emit Verilog" in {
    val numPorts = 2
    val robDepth = 24
    GenerateVerilog(new ROBStage(numPorts))
  }
  "RetiredStatusStage" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new RetiredStatusStage(numPorts))
  }
  "ArchRegisterFileStage" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new ArchRegisterFileStage(numPorts))
  }

}
