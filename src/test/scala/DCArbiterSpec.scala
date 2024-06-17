package dcarbiter

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.GenerateVerilog

class DCArbiterSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCArbiter" should s"work 1 to 1" in {
    test(new DCArbiter(UInt(8.W))(1, 1)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
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
      dut.clock.step(1)

      dut.clock.step(1)
    }
  }

  "DCArbiter" should "work 2 to 1" in {
    test(new DCArbiter(UInt(8.W))(2, 1)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      // Create a sequence of zeros and ones
      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // Enqueue the zeros to the 0th input port and the ones to the 1st input port
      fork { inSources(0).enqueueSeq(zeros) }.fork { inSources(1).enqueueSeq(ones) }

      // Expect the output to be a concatenation of the zeros and ones
      outSinks.head.expectDequeueSeq(zeros ++ ones)

      dut.clock.step(1)
    }
  }
  for (N <- 3 to 8) {
    "DCArbiter" should s"work ${N} to 1" in {
      test(new DCArbiter(UInt(8.W))(N, 1)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = dut.io.out.map(_.initSink())

        // Create sequences for each input
        val numData   = 100
        val inputSeqs = Seq.tabulate(N)(i => Seq.fill(numData)(i.U))

        // Enqueue the sequences to the corresponding input ports
        val producerForks = inSources.zip(inputSeqs).map {
          case (source, inputSeq) =>
            fork { source.enqueueSeq(inputSeq) }
        }

        // Expect the output to be a concatenation of the input sequences
        outSinks.head.expectDequeueSeq(inputSeqs.flatten)

        dut.clock.step(1)
      }
    }
  }
  "DCArbiter" should "work 2 to 2" in {
    test(new DCArbiter(UInt(8.W))(2, 2)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      // Create a sequence of zeros and ones
      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()
      dut.clock.step(1)
    }
  }
  "DCArbiter" should "work 3 to 2" in {
    test(new DCArbiter(UInt(8.W))(3, 2)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      // Create a sequence of zeros and ones
      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)
      val twos    = Seq.fill(numData)(2.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        inSources(2).enqueueSeq(twos)
      }.fork {
        outSinks(0).expectDequeueSeq(zeros ++ twos)
      }.fork {
        outSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()
      dut.clock.step(1)
    }
  }

  "DCArbiter" should "emit Verilog" in {
    GenerateVerilog(new DCArbiter(UInt(8.W))(4, 3))
  }
}
