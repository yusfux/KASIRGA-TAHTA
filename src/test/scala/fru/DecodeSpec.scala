package wood.fru

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec

import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.fru.{DecodeConfig, DecodeStage, Decoder}

class DecodeSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Decoder" should s"work on smoketest" in {
    // riscvopcodes repo is already verified
    test(new Decoder()).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.inst.poke(0x0009c797) // auipc a5, 156
      dut.clock.step(1)
      if (0x69420.asUInt == dut.io.inst) {
        println("\u001b[31m" + "nice" + "\u001b[0m")
      }
    }
  }

  "Decoder" should "emit Verilog" in {
    println(s"defaultDecSeq: ${DecodeConfig.defaultDecSeq}")
    println(s"defaultDec: ${DecodeConfig.defaultDec}")
    println(s"width: ${DecodeConfig.width}")
    println(s"subWidths: ${DecodeConfig.subWidths}")
    for (range <- DecodeConfig.bitRanges) {
      println(s"bitRanges: ${range}")
    }
    GenerateVerilog(new Decoder())
  }
  "DecodeStage" should "emit Verilog" in {
    val numOut = 4
    GenerateVerilog(new DecodeStage(numOut))
  }
}
