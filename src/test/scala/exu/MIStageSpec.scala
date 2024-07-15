package wood.exu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.{TestConfig, WoodConfig}
import wood.util.{GetBackendAnnotation, GetGroupedSequences, TestGenerateVerilog}
import wood.fru.MI

class MIStageSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  "MIStage" should "print dummy waveform" in {
    val numDataPerGroup = 30
    val nWide           = 1
    val config          = new WoodConfig(nWide = nWide, miQueueDepth = 16)

    val sequences = GetGroupedSequences(nWide, numDataPerGroup)
    val miSequences: List[List[MI]] = sequences.map(_.map(data => MI(config, 0.U, Map("inst" -> data))))

    test(new MIStage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in     = dut.io.in.map(_.initSource())
      val comBus = dut.io.commitedBus.map(_.initSource())
      val out    = dut.io.out.map(_.initSink())

      dut.io.out(0).ready.poke(1)
      fork {
        in(0).enqueueSeq(miSequences(0))
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
