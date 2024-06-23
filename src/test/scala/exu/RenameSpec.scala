package wood.exu

import chisel3._
import chiseltest._
import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class RenameStageSpec extends AnyFlatSpec with ChiselScalatestTester {

  "RenameStage" should "work with 2 inputs" in {
    test(new RenameStage(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inMIs         = dut.io.in.map(_.initSource())
      val inFlistRetire = dut.io.in.map(_.initSource())
      val outSinks      = dut.io.out.map(_.initSink())

      val inst1 = MI(0.U, Map("rs1" -> 1.U, "rs2" -> 2.U))
      val inst2 = MI(0.U, Map("rs1" -> 1.U, "rs2" -> 2.U))

      fork {
        inMIs(0).enqueue(inst1)
      }.fork {
        inMIs(1).enqueue(inst2)
      }.fork {
        // outSinks(0).expectDequeue(inst1)
        step(100)
      }.joinAndStep()
    }
  }

  "RenameStage" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new RenameStage(numPorts))
  }
}
