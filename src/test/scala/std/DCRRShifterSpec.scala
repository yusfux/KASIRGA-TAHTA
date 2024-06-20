package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class DCRRShifterSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCRRShifter" should s"work with 1 ports" in {
    test(new DCRRShifter(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val data    = 8.U
      val numData = 100
      val inputs  = Seq.fill(numData)(data)

      fork {
        inSources(0).enqueueSeq(inputs)
      }.fork {
        outSinks(0).expectDequeueSeq(inputs)
      }.joinAndStep()
    }
  }

  "DCRRShifter" should s"work with 2 ports" in {
    test(new DCRRShifter(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // Only enqueue to port 0.
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        outSinks(0).expectDequeue(0.U)
      }.joinAndStep()

      // Now shift should be 1
      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0).expectDequeueSeq(ones)
      }.fork {
        outSinks(1).expectDequeueSeq(zeros)
      }.joinAndStep()
    }

  }

  "DCRRShifter" should s"work with 3 ports" in {
    test(new DCRRShifter(UInt(8.W))(3)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)
      val twos    = Seq.fill(numData)(2.U)

      // Only enqueue to port 0 and 1
      fork {
        inSources(0).enqueue(0.U)
      }.fork {
        inSources(1).enqueue(1.U)
      }.fork {
        outSinks(0).expectDequeue(0.U)
      }.fork {
        outSinks(1).expectDequeue(1.U)
      }.joinAndStep()

      // Now shift should be 2
      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        inSources(2).enqueueSeq(twos)
      }.fork {
        outSinks(0).expectDequeueSeq(ones)
      }.fork {
        outSinks(1).expectDequeueSeq(twos)
      }.fork {
        outSinks(2).expectDequeueSeq(zeros)
      }.joinAndStep()
    }
  }

  "DCRRShifter" should "emit Verilog" in {
    GenerateVerilog(new DCRRShifter(UInt(8.W))(4))
  }
}
