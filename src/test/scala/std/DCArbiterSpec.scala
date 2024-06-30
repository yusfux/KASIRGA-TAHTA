package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCArbiterSpec extends AnyFlatSpec with ChiselScalatestTester {
  val numDataPerGroup = 30

  "DCArbiter" should s"work 1 to 1" in {
    test(new DCArbiter(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
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
  "DCArbiter" should s"work 1 to 2" in {
    test(new DCArbiter(UInt(8.W))(1, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0).expectDequeueSeq(groupedSeqs(0))
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

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }

      // input port 0 has priority
      outSinks.head.expectDequeueSeq(groupedSeqs(0) ++ groupedSeqs(1))

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
  "DCArbiter" should "work 3 to 2" in {
    test(new DCArbiter(UInt(8.W))(3, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out.map(_.initSink())

      val groupedSeqs = GetGroupedSequences(3, numDataPerGroup)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        inSources(2).enqueueSeq(groupedSeqs(2))
      }.fork {
        // Input port 0 and 1 will connect to output port 0 and 1 respectively. After they are done, input port 2 will connect to output port 0.
        outSinks(0).expectDequeueSeq(groupedSeqs(0) ++ groupedSeqs(2))
      }.fork {
        outSinks(1).expectDequeueSeq(groupedSeqs(1))
      }.joinAndStep()
    }
  }

  "DCArbiter" should "emit Verilog" in {
    GenerateVerilog(new DCArbiter(UInt(8.W))(4, 3))
  }
}
