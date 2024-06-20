package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class DCShifterSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCShifter" should s"work with 1 ports" in {
    test(new DCShifter(UInt(8.W))(1)).withAnnotations(GetBackendAnnotation()) { dut =>
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

  "DCShifter" should s"work with 2 ports" in {
    test(new DCShifter(UInt(8.W))(2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      dut.io.shamt.poke(0.U)
      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()

      dut.io.shamt.poke(1.U)
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

  "DCShifter" should s"work with 3 ports" in {
    test(new DCShifter(UInt(8.W))(3)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)
      val twos    = Seq.fill(numData)(2.U)

      dut.io.shamt.poke(2.U)
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

  "DCShifter" should "emit Verilog" in {
    GenerateVerilog(new DCShifter(UInt(8.W))(4))
  }
}
