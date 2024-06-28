package wood.fru

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.TestConfig

class MIStageSpec extends AnyFlatSpec with ChiselScalatestTester {
  val numData = 100

  "MIStage" should "work width 2 all valid" in {
    val config = new WoodConfig(nWide = 2)

    val zero = MI(config, 0.U)
    val one  = MI(config, 1.U)

    val zeros = Seq.fill(numData)(zero)
    val ones  = Seq.fill(numData)(one)
    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
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
    val config = new WoodConfig(nWide = 2)

    val zero  = MI(config, 0.U)
    val zeros = Seq.fill(numData)(zero)

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
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
    val config = new WoodConfig(nWide = 3)

    val zero = MI(config, 0.U)
    val one  = MI(config, 1.U)
    val two  = MI(config, 2.U)

    val zeros = Seq.fill(numData)(zero)
    val ones  = Seq.fill(numData)(one)
    val twos  = Seq.fill(numData)(two)

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
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

  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    "MIStage" should s"emit Verilog ${j} wide" in {
      val config          = new WoodConfig(nWide = j)
      val currentTestName = testNames.toList.map(_.replaceAll(" ", "_"))(j - 1)
      val testRunDir      = s"test_run_dir/$currentTestName"
      val dir             = new java.io.File(testRunDir)

      if (!dir.exists()) {
        dir.mkdirs()
      }

      GenerateVerilog(new MIStage(config), path = testRunDir)
    }
  })
}
