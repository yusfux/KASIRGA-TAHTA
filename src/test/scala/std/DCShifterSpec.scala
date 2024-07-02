package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCShifterSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numDataPerGroup = 30

  "DCShifter" should s"work with 1 ports" in {
    test(new DCShifter(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCShifter" should s"work with 2 ports" in {
    test(new DCShifter(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      dut.io.shamt.poke(0.U)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.joinAndStep()

      dut.io.shamt.poke(1.U)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }

  }

  "DCShifter" should s"work with 3 ports" in {
    test(new DCShifter(UInt(8.W))(3)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(3, numDataPerGroup)

      dut.io.shamt.poke(2.U)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        inSources(2).enqueueSeq(groupedSeqs(2))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(2))
      }.fork {
        outSinks(2).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCShifter" should "emit Verilog" in {
    GenerateVerilog(new DCShifter(UInt(8.W))(4))
  }
}
