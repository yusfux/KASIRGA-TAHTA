package wood.exu

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.fru.MI
import wood.{TestConfig, WoodConfig}
import wood.util.{GetBackendAnnotation, TestGenerateVerilog}
import wood.fru.DecodeConfig

class RenameStageSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  "RenameStage" should "work (1 wide)" in {
    val config = new WoodConfig(nWide = 1, prfDepth = 16)
    test(new RenameStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inMIs = dut.io.in.map(_.initSource())
      // val inFlistRetire = dut.io.in.map(_.initSource())
      val outSinks = dut.io.out.map(_.initSink())

      // format: off
      val inst1    = MI(config, 0.U, Map("rs1" -> 0.U, "operand" -> DecodeConfig.OPERAND_IMM.toInt.U,"rs2" -> 0.U, "rd" -> 1.U, "writeRf" -> DecodeConfig.WRITE_RF_1.toInt.U, "imm" -> 5.U))
      val inst2    = MI(config, 0.U, Map("rs1" -> 0.U, "operand" -> DecodeConfig.OPERAND_IMM.toInt.U,"rs2" -> 0.U, "rd" -> 2.U, "writeRf" -> DecodeConfig.WRITE_RF_1.toInt.U, "imm" -> 6.U))
      val inst3    = MI(config, 0.U, Map("rs1" -> 1.U, "operand" -> DecodeConfig.OPERAND_REG.toInt.U,"rs2" -> 2.U, "rd" -> 3.U, "writeRf" -> DecodeConfig.WRITE_RF_0.toInt.U, "imm" -> 7.U))
      val finalOut = MI(config, 0.U, Map("rs1" -> 1.U, "operand" -> DecodeConfig.OPERAND_REG.toInt.U,"rs2" -> 2.U, "rd" -> 3.U, "writeRf" -> DecodeConfig.WRITE_RF_0.toInt.U, "imm" -> 7.U))
      // format: on

      fork {
        inMIs(0).enqueue(inst1)
        inMIs(0).enqueue(inst2)
        inMIs(0).enqueue(inst3)
      }.fork {
        dut.io.out(0).ready.poke(1)
        // outSinks(0).expectDequeue(inst1)
        step(100)
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "RenameStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new RenameStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
