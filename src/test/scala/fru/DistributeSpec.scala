package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GetBackendAnnotation, GetGroupedSequences, TestGenerateVerilog}
import wood.{TestConfig, WoodConfig}
import wood.fru.DecodeConfig.{TYPE_FLOAT, TYPE_INT}
import wood.fru.MI

class DistributeStageSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val config = new WoodConfig(nWide = 2)

  val numDataPerGroup = 30
  val TOINT           = TYPE_INT.toInt.U
  val TOFLOAT         = TYPE_FLOAT.toInt.U
  val sequences       = GetGroupedSequences(config.nWide, numDataPerGroup)

  val miIntSequences: List[List[MI]] =
    sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data, "isFloat" -> TOINT))))
  val miFloatSequences: List[List[MI]] =
    sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data, "isFloat" -> TOFLOAT))))

  "DistributeStage" should "work 2 to (2,2) (int,int)" in {
    test(new DistributeStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(miIntSequences(0))
      }.fork {
        inSources(1).enqueueSeq(miIntSequences(1))
      }.fork {
        outFloatSinks(0).expectInvalid()
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(miIntSequences(0))
      }.fork {
        outIntSinks(1).expectDequeueSeq(miIntSequences(1))
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (int,float)" in {
    test(new DistributeStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(miFloatSequences(1))
      }.fork {
        inSources(1).enqueueSeq(miIntSequences(0))
      }.fork {
        outFloatSinks(0).expectDequeueSeq(miFloatSequences(1))
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(miIntSequences(0))
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (float,float)" in {
    test(new DistributeStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(miFloatSequences(0))
      }.fork {
        inSources(1).enqueueSeq(miFloatSequences(1))
      }.fork {
        outFloatSinks(0).expectDequeueSeq(miFloatSequences(0))
      }.fork {
        outFloatSinks(1).expectDequeueSeq(miFloatSequences(1))
      }.fork {
        outIntSinks(0).expectInvalid()
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DistributeStage" should "work 2 to (2,2) (float,int)" in {
    test(new DistributeStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources     = dut.io.in.map(_.initSource())
      val outFloatSinks = dut.io.toFloat.map(_.initSink())
      val outIntSinks   = dut.io.toInt.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(miIntSequences(0))
      }.fork {
        inSources(1).enqueueSeq(miFloatSequences(1))
      }.fork {
        outFloatSinks(0).expectDequeueSeq(miFloatSequences(1))
      }.fork {
        outFloatSinks(1).expectInvalid()
      }.fork {
        outIntSinks(0).expectDequeueSeq(miIntSequences(0))
      }.fork {
        outIntSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "DistributeStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new DistributeStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
