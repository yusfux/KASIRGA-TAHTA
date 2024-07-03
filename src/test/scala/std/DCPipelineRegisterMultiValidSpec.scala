package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}
// import wood.util.{GenerateVerilog}

class DCPipelineRegisterMultiValidSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val defaultDepth    = 13
  val numDataPerGroup = 30
  val defaultVal      = 0.U

  "DCPipelineRegisterMultiValid" should "work width 1p + 1(1p) extra valid" in {
    test(new DCPipelineRegisterMultiValid(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)

      dut.io.valids(0)(0).poke(1)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }
  "DCPipelineRegisterMultiValid" should "work width 2p + 1(2p) extra valid" in {
    test(new DCPipelineRegisterMultiValid(UInt(8.W))(2, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      dut.io.valids(0)(0).poke(1)
      dut.io.valids(0)(1).poke(1)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.joinAndStep()
    }
  }
  "DCPipelineRegisterMultiValid" should "work width 2p + 1(2p) extra valid stall" in {
    test(new DCPipelineRegisterMultiValid(UInt(8.W))(2, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      dut.io.valids(0)(0).poke(0)
      dut.io.valids(0)(1).poke(1)
      fork {
        dut.io.in(0).valid.poke(1)
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.joinAndStep()
    }
  }
  "DCPipelineRegisterMultiValid" should "work width 3p + 2(3p) extra valid" in {
    test(new DCPipelineRegisterMultiValid(UInt(8.W))(3, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(3, numDataPerGroup)

      dut.io.valids(0)(0).poke(1)
      dut.io.valids(0)(1).poke(1)
      dut.io.valids(0)(2).poke(1)
      dut.io.valids(1)(0).poke(1)
      dut.io.valids(1)(1).poke(1)
      dut.io.valids(1)(2).poke(1)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        inSources(2).enqueueSeq(groupedSeqs(2))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.fork {
        outSinks(2).expectDequeueSeq(groupedSeqs(2))
      }.joinAndStep()
    }
  }

  "DCPipelineRegisterMultiValid" should "emit Verilog" in {
    val numPorts       = 2
    val numExtraValids = 3
    GenerateVerilog(new DCPipelineRegisterMultiValid(UInt(8.W))(numPorts, numExtraValids))
  }
}
