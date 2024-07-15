package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCPipelineRegisterSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val defaultDepth    = 13
  val numDataPerGroup = 30
  val defaultVal      = 0.U

  "DCPipelineRegister" should "work 1 valid" in {
    test(new DCPipelineRegister(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in  = dut.io.in.initSource()
      val out = dut.io.out.initSink()

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
      dut.io.valids(0).poke(1)
      fork {
        in.enqueueSeq(groupedSeqs(0))
      }.fork {
        out.expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCPipelineRegister" should "work 2 valid" in {
    test(new DCPipelineRegister(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val in  = dut.io.in.initSource()
      val out = dut.io.out.initSink()

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
      dut.io.valids(0).poke(1)
      dut.io.valids(1).poke(1)
      fork {
        in.enqueueSeq(groupedSeqs(0))
      }.fork {
        out.expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCPipelineRegister" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new DCPipelineRegister(UInt(8.W))(numPorts))
  }
}
