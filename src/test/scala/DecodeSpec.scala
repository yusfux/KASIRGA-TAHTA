package decode

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec

import wood.{Fetch, GenerateVerilog}

class DecodeSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Decoder" should s"work on smoketest" in {
    // riscvopcodes repo which is already verified
    test(new Decoder()).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      dut.io.inst.poke(0x0009c797) // auipc a5, 156
      dut.clock.step(1)
      if (0x69420.asUInt == dut.io.inst) {
        println("\u001b[31m" + "nice" + "\u001b[0m")
      }
    }
  }

  "Decoder" should "emit Verilog" in {
    GenerateVerilog(new Decoder())
  }
  "DecodeStage" should "emit Verilog" in {
    val numOut = 4
    GenerateVerilog(new DecodeStage(numOut, Fetch.pcIndexWidth))
  }
}
