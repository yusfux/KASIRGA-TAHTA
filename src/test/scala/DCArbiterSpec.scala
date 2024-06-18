package dcarbiter

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.{GenerateVerilog, GetBackendAnnotation}

class DCArbiterSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCArbiter" should s"work 1 to 1" in {
    test(new DCArbiter(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
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
  "DCArbiter" should s"work 1 to 2" in {
    test(new DCArbiter(UInt(8.W))(1, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val data    = 8.U
      val numData = 100
      val inputs  = Seq.fill(numData)(data)

      fork {
        inSources(0).enqueueSeq(inputs)
      }.fork {
        outSinks(0).expectDequeueSeq(inputs)
      }.fork {
        // port 2 is never used
        outSinks(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DCArbiter" should "work 2 to 1" in {
    test(new DCArbiter(UInt(8.W))(2, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // all input ports are valid
      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }

      // input port 0 has priority
      outSinks.head.expectDequeueSeq(zeros ++ ones)

      dut.clock.step(1)
    }
  }
  for (N <- 3 to 8) {
    "DCArbiter" should s"work ${N} to 1" in {
      test(new DCArbiter(UInt(8.W))(N, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = dut.io.out.map(_.initSink())

        val numData   = 100
        val inputSeqs = Seq.tabulate(N)(i => Seq.fill(numData)(i.U))

        // all input ports are valid
        val producerForks = inSources.zip(inputSeqs).map {
          case (source, inputSeq) =>
            fork { source.enqueueSeq(inputSeq) }
        }

        // input ports has priority from low to high
        outSinks.head.expectDequeueSeq(inputSeqs.flatten)

        dut.clock.step(1)
      }
    }
  }
  "DCArbiter" should "work 2 to 2" in {
    test(new DCArbiter(UInt(8.W))(2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

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
    }
  }
  "DCArbiter" should "work 3 to 2" in {
    test(new DCArbiter(UInt(8.W))(3, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

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
        // Input port 0 and 1 will connect to output port 0 and 1 respectively. After they are done, input port 2 will connect to output port 0.
        outSinks(0).expectDequeueSeq(zeros ++ twos)
      }.fork {
        outSinks(1).expectDequeueSeq(ones)
      }.joinAndStep()
    }
  }

  "DCArbiter" should "emit Verilog" in {
    GenerateVerilog(new DCArbiter(UInt(8.W))(4, 3))
  }
}
