package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.{GetBackendAnnotation, GetGroupedSequences, TestGenerateVerilog}

class MIStageSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numDataPerGroup = 30

  "MIStage" should "work width 2 all valid" in {
    val config    = new WoodConfig(nWide = 2, miQueueDepth = 16)
    val numValid  = 2
    val defaultMI = MI(config, 0.U)

    val sequences = GetGroupedSequences(numValid, numDataPerGroup)
    val miSequences: List[List[MI]] = sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data))))

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueueSeq(miSequences(0))
      }.fork {
        inSources(1).enqueueSeq(miSequences(1))
      }.fork {
        outSinks(0).expectDequeue(defaultMI)
        outSinks(0).expectDequeueSeq(miSequences(0))
      }.fork {
        outSinks(1).expectDequeue(defaultMI)
        outSinks(1).expectDequeueSeq(miSequences(1))
      }.joinAndStep()
    }
  }

  "MIStage" should "work width 2 only 1 valid" in {
    val config    = new WoodConfig(nWide = 2, miQueueDepth = 16)
    val numValid  = 1
    val defaultMI = MI(config, 0.U)

    val sequences = GetGroupedSequences(numValid, numDataPerGroup)
    val miSequences: List[List[MI]] = sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data))))

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(miSequences(0)(0))
        inSources(0).enqueue(miSequences(0)(1))
        inSources(0).enqueue(miSequences(0)(2))
        inSources(0).enqueue(miSequences(0)(3))
      }.fork {
        outSinks(0).expectDequeue(defaultMI)
        outSinks(0).expectDequeue(miSequences(0)(0))
        outSinks(0).expectInvalid()
        outSinks(0).expectDequeue(miSequences(0)(2))
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeue(defaultMI)
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(miSequences(0)(1))
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(miSequences(0)(3))
      }.joinAndStep()
    }
  }

  "MIStage" should "work width 3 only 2 valid" in {
    val config    = new WoodConfig(nWide = 3, miQueueDepth = 16)
    val numValid  = 2
    val defaultMI = MI(config, 0.U)

    val sequences = GetGroupedSequences(numValid, numDataPerGroup)
    val miSequences: List[List[MI]] = sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data))))

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(miSequences(0)(0))
        inSources(0).enqueue(miSequences(0)(1))
        inSources(0).enqueue(miSequences(0)(2))
      }.fork {
        inSources(1).enqueue(miSequences(1)(0))
        inSources(1).enqueue(miSequences(1)(1))
        inSources(1).enqueue(miSequences(1)(2))
      }.fork {
        outSinks(0).expectDequeue(defaultMI)
        outSinks(0).expectDequeue(miSequences(0)(0))
        outSinks(0).expectDequeue(miSequences(1)(1))
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeue(defaultMI)
        outSinks(1).expectDequeue(miSequences(1)(0))
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(miSequences(0)(2))
      }.fork {
        outSinks(2).expectDequeue(defaultMI)
        outSinks(2).expectInvalid()
        outSinks(2).expectDequeue(miSequences(0)(1))
        outSinks(2).expectDequeue(miSequences(1)(2))
      }.joinAndStep()
    }
  }

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "MIStage" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new MIStage(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
