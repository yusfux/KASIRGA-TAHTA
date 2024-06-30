package wood.std

import chisel3._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCRRQueueSpec extends AnyFlatSpec with ChiselScalatestTester {
  val defaultDepth    = 13
  val numDataPerGroup = 30

  "DCRRQueue" should "work width 1 all valid" in {
    test(new DCRRQueue(UInt(8.W))(1, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
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

  "DCRRQueue" should "work width 2 all valid" in {
    test(new DCRRQueue(UInt(8.W))(2, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)
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

  "DCRRQueue" should "work width 2 only 1 valid" in {
    test(new DCRRQueue(UInt(8.W))(2, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(1.U)
        inSources(0).enqueue(2.U)
        inSources(0).enqueue(3.U)
        inSources(0).enqueue(4.U)
      }.fork {
        outSinks(0).expectDequeue(1.U)
        outSinks(0).expectInvalid()
        outSinks(0).expectDequeue(3.U)
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(2.U)
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(4.U)
      }.joinAndStep()
    }
  }

  "DCRRQueue" should "work width 3 only 2 valid" in {
    test(new DCRRQueue(UInt(8.W))(3, defaultDepth)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      fork {
        inSources(0).enqueue(1.U)
        inSources(0).enqueue(3.U)
        inSources(0).enqueue(5.U)
      }.fork {
        inSources(1).enqueue(2.U)
        inSources(1).enqueue(4.U)
        inSources(1).enqueue(6.U)
      }.fork {
        outSinks(0).expectDequeue(1.U)
        outSinks(0).expectDequeue(4.U)
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeue(2.U)
        outSinks(1).expectInvalid()
        outSinks(1).expectDequeue(5.U)
      }.fork {
        outSinks(2).expectInvalid()
        outSinks(2).expectDequeue(3.U)
        outSinks(2).expectDequeue(6.U)
      }.joinAndStep()
    }
  }

  "DCRRQueue" should "emit Verilog" in {
    val numPorts   = 2
    val queueDepth = 122
    GenerateVerilog(new DCRRQueue(UInt(8.W))(numPorts, queueDepth))
  }
}
