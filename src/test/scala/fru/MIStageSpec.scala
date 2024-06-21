package wood.fru

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class MIStageSpec extends AnyFlatSpec with ChiselScalatestTester {
  val defaultDepth = 55
  val numData      = 100

  val zero = new MI().Lit(
    _.isFloat  -> 0.U,
    _.operand  -> 0.U,
    _.write_rf -> 0.U,
    _.exEngine -> 0.U,
    _.exOp     -> 0.U,
    _.imm      -> 0.U,
    _.rs1      -> 0.U,
    _.rs2      -> 0.U,
    _.rd       -> 0.U,
    _.pc_idx   -> 0.U
  )

  val one = new MI().Lit(
    _.isFloat  -> 1.U,
    _.operand  -> 1.U,
    _.write_rf -> 1.U,
    _.exEngine -> 1.U,
    _.exOp     -> 1.U,
    _.imm      -> 1.U,
    _.rs1      -> 1.U,
    _.rs2      -> 1.U,
    _.rd       -> 1.U,
    _.pc_idx   -> 1.U
  )

  val two = new MI().Lit(
    _.isFloat  -> 2.U,
    _.operand  -> 2.U,
    _.write_rf -> 2.U,
    _.exEngine -> 2.U,
    _.exOp     -> 2.U,
    _.imm      -> 2.U,
    _.rs1      -> 2.U,
    _.rs2      -> 2.U,
    _.rd       -> 2.U,
    _.pc_idx   -> 2.U
  )

  val zeros = Seq.fill(numData)(zero)
  val ones  = Seq.fill(numData)(one)
  val twos  = Seq.fill(numData)(two)

  "MIStage" should "work width 2 all valid" in {
    test(new MIStage(2, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()
    }
  }

  "MIStage" should "work width 2 only 1 valid" in {
    test(new MIStage(2, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(zero)
        inSources(0).enqueue(zero)
        inSources(0).enqueue(zero)
        inSources(0).enqueue(zero)
      }.fork {
        outSinks(0).expectDequeue(zero)
        outSinks(0).expectInvalid()
        outSinks(0).expectDequeue(zero)
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(zero)
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(zero)
      }.joinAndStep()
    }
  }

  "MIStage" should "work width 3 only 2 valid" in {
    test(new MIStage(3, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(zero)
        inSources(0).enqueue(zero)
        inSources(0).enqueue(zero)
      }.fork {
        inSources(1).enqueue(one)
        inSources(1).enqueue(one)
        inSources(1).enqueue(one)
      }.fork {
        outSinks(0).expectDequeue(zero)
        outSinks(0).expectDequeue(one)
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeue(one)
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(zero)
      }.fork {
        outSinks(2).expectInvalid()
        outSinks(2).expectDequeue(zero)
        outSinks(2).expectDequeue(one)
      }.joinAndStep()
    }
  }

  "MIStage" should "emit Verilog" in {
    val numPorts   = 2
    val queueDepth = 122
    GenerateVerilog(new MIStage(numPorts, queueDepth))
  }
}
