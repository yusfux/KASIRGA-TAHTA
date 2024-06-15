package decoder

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec

class DecoderSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Decoder" should s"work on test" in {
    test(new Decoder()).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      dut.io.inst.poke(0x55555)
      dut.clock.step(1)
      if (0x55555.asUInt == dut.io.mi) {
        println("\u001b[31m" + "test" + "\u001b[0m")
      }

      dut.io.inst.poke(BitPat("b0000000??????????000?????0110011").value)
      dut.clock.step(1)
      if (0x55555.asUInt == dut.io.mi) {
        println("\u001b[31m" + "test" + "\u001b[0m")
      }

      dut.io.inst.poke(BitPat("b0000000??????????000?????0110011").value)
      dut.clock.step(1)
      if (0x55555.asUInt == dut.io.mi) {
        println("\u001b[31m" + "test" + "\u001b[0m")
      }

      dut.io.inst.poke(BitPat("b?????????????????000?????0010011").value)
      dut.clock.step(1)
      if (0x55555.asUInt == dut.io.mi) {
        println("\u001b[31m" + "test" + "\u001b[0m")
      }

    // micro_instruction.
    }
  }
}
