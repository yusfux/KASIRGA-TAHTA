package wood.fru

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.fru.MI

class DistributeStageSpec extends AnyFlatSpec with ChiselScalatestTester {
  val numData = 100

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

  val zeros = Seq.fill(numData)(zero)
  val ones  = Seq.fill(numData)(one)

  "DistributeStage" should "work 2 to (2,2) (int,int)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      // send input port 0 to output interface 0 (int)
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 0 (int)
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outFloatSinks(0).expectInvalid()
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outIntSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (int,float)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      // send input port 0 to output interface 1 (float)
      dut.io.sel(0).poke(1.U)
      // send input port 1 to output interface 0 (int)
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(ones)
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (float,float)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      // send input port 0 to output interface 1 (float)
      dut.io.sel(0).poke(1.U)
      // send input port 1 to output interface 1 (float)
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outFloatSinks(1).expectDequeueSeq(ones)
      }.fork {
        outIntSinks(0).expectInvalid()
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }
  "DistributeStage" should "work 2 to (2,2) (float,int)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      // send input port 0 to output interface 0 (int)
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 1 (float)
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(ones)
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DistributeStage" should "emit Verilog" in {
    val numInputs   = 4
    val numOutInt   = 4
    val numOutFloat = 4
    GenerateVerilog(new DistributeStage(numInputs, numOutInt, numOutFloat))
  }
}
