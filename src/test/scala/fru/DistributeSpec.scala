package wood.fru

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.fru.MI
import wood.fru.DecodeConfig.{TYPE_FLOAT, TYPE_INT}

class DistributeStageSpec extends AnyFlatSpec with ChiselScalatestTester {
  val numData = 100
  val TOINT   = TYPE_INT.toInt.U
  val TOFLOAT = TYPE_FLOAT.toInt.U

  val zero_to_int   = MI(0.U, Map("isFloat" -> 0.U))
  val zero_to_float = MI(0.U, Map("isFloat" -> 1.U))
  val one_to_int    = MI(1.U, Map("isFloat" -> 0.U))
  val one_to_float  = MI(1.U, Map("isFloat" -> 1.U))

  val zero_to_ints   = Seq.fill(numData)(zero_to_int)
  val zero_to_floats = Seq.fill(numData)(zero_to_float)
  val one_to_ints    = Seq.fill(numData)(one_to_int)
  val one_to_floats  = Seq.fill(numData)(one_to_float)

  "DistributeStage" should "work 2 to (2,2) (int,int)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(zero_to_ints)
      }.fork {
        inSources(1).enqueueSeq(one_to_ints)
      }.fork {
        outFloatSinks(0).expectInvalid()
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(zero_to_ints)
      }.fork {
        outIntSinks(1).expectDequeueSeq(one_to_ints)
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (int,float)" in {
    test(new DistributeStage(2, 2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(zero_to_floats)
      }.fork {
        inSources(1).enqueueSeq(one_to_ints)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(zero_to_floats)
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(one_to_ints)
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

      fork {
        inSources(0).enqueueSeq(zero_to_floats)
      }.fork {
        inSources(1).enqueueSeq(one_to_floats)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(zero_to_floats)
      }.fork {
        outFloatSinks(1).expectDequeueSeq(one_to_floats)
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

      fork {
        inSources(0).enqueueSeq(zero_to_ints)
      }.fork {
        inSources(1).enqueueSeq(one_to_floats)
      }.fork {
        outFloatSinks(0).expectDequeueSeq(one_to_floats)
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(zero_to_ints)
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
