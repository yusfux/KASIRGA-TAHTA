package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class FreeListSpec extends AnyFlatSpec with ChiselScalatestTester {
  "FreeListInitializer" should "work with 2 inputs" in {
    val numPorts = 2
    test(new FreeListInitializer(numPorts)).withAnnotations(GetBackendAnnotation()) { dut =>
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(1) })
      step(100)
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
    }
  }

  "FreeList" should "work with 2 inputs" in {
    val numPorts = 2
    test(new FreeList(numPorts)).withAnnotations(GetBackendAnnotation()) { dut =>
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
      (0 until numPorts).foreach(j => { dut.io.out(j).ready.poke(0) })
      step(100)
    }
  }

  "FreeList" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new FreeList(numPorts))
  }

  "FreeListInitializer" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new FreeListInitializer(numPorts))
  }
}
