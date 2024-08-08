package wood.exu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{WoodConfig}
import wood.util.GenerateVerilog
import wood.util.GetBackendAnnotation

class IDUSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val config = new WoodConfig(nWide = 1)

  "IDU" should s"divide" in {
    test(new IDU(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in  = dut.io.in.initSource()
      val out = dut.io.out.initSink()

      val inst1 = MI(
        config,
        0.U,
        Map(
          "rs1Data"     -> 50.U,
          "rs2Data"     -> 2.U,
          "rs1TagReady" -> 1.B,
          "rs2TagReady" -> 1.B,
          "retired"     -> 1.B,
          "arfValid"    -> 1.B,
          "operand1"    -> Integer.parseInt(DecodeConfig.OPERAND1_REG, 2).U,
          "operand2"    -> Integer.parseInt(DecodeConfig.OPERAND2_REG, 2).U,
          "exOp"        -> IDUOp.div.litValue.U
        )
      )

      fork {
        in.enqueue(inst1)
      }.fork {
        dut.io.out.ready.poke(1)
        step(36)
      }.joinAndStep()
    }
  }

  "IDU" should "emit Verilog" in {
    GenerateVerilog(new IDU(config))
  }
}
