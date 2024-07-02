package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}
import org.scalatest.ParallelTestExecution

class DCRRShifterSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numDataPerGroup = 30

  "DCRRShifter" should s"work with 1 ports" in {
    test(new DCRRShifter(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
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

  "DCRRShifter" should s"work with 2 ports" in {
    test(new DCRRShifter(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      // Only enqueue to port 0.
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        outSinks(0).expectDequeue(0.U)
      }.joinAndStep()

      // Now shift should be 1
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

  "DCRRShifter" should s"work with 3 ports" in {
    val numPorts = 3
    test(new DCRRShifter(UInt(8.W))(numPorts)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(3, numDataPerGroup)

      // Only enqueue to port 0 and 1
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        inSources(1).enqueue(1.U)
      }.fork {
        outSinks(0).expectDequeue(0.U)
      }.fork {
        outSinks(1).expectDequeue(1.U)
      }.fork {
        outSinks(2).expectInvalid()
      }.joinAndStep()

      // Now shift should be 2
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        inSources(1).enqueue(1.U)
      }.fork {
        inSources(2).enqueue(2.U)
      }.fork {
        outSinks(0).expectDequeue(1.U)
      }.fork {
        outSinks(1).expectDequeue(2.U)
      }.fork {
        outSinks(2).expectDequeue(0.U)
      }.joinAndStep()

      // Shift should not change, still 2
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        inSources(1).enqueue(1.U)
      }.fork {
        outSinks(0).expectDequeue(1.U)
      }.fork {
        outSinks(1).expectInvalid()
      }.fork {
        outSinks(2).expectDequeue(0.U)
      }.joinAndStep()

      // Shift should be 1 now
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        inSources(1).enqueue(1.U)
      }.fork {
        outSinks(0).expectInvalid()
      }.fork {
        outSinks(1).expectDequeue(0.U)
      }.fork {
        outSinks(2).expectDequeue(1.U)
      }.joinAndStep()

    }
  }

  "DCRRShifter" should "emit Verilog" in {
    GenerateVerilog(new DCRRShifter(UInt(8.W))(4))
  }
}
