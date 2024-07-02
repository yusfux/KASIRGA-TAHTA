package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}
// import wood.util.{GenerateVerilog}

class DCPipelineRegisterSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val defaultDepth    = 13
  val numDataPerGroup = 30
  val defaultVal      = 0.U

  "DCPipelineRegister" should "work width 1 all valid" in {
    test(new DCPipelineRegister(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0).expectDequeue(defaultVal)
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCPipelineRegister" should "work width 2 all valid" in {
    test(new DCPipelineRegister(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0).expectDequeue(defaultVal)
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1).expectDequeue(defaultVal)
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.joinAndStep()
    }
  }

  "DCPipelineRegister" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new DCPipelineRegister(UInt(8.W))(numPorts))
  }
}
