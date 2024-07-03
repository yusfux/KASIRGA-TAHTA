package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCDemuxSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numDataPerGroup = 30

  "DCDemux" should s"work 1 x 1" in {
    test(new DCDemux(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(1)(n => dut.io.out(n).map(_.initSink()))

      val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
      }.joinAndStep()
    }
  }

  "DCDemux" should s"work 1 x 1 ready only" in {
    test(new DCDemux(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(1)(n => dut.io.out(n).map(_.initSink()))

      dut.io.sel(0).poke(0.U)
      dut.io.out(0)(0).ready.poke(1)
      step(1)
      dut.io.in(0).ready.expect(1)
    }
  }

  "DCDemux" should s"work 1 x 2 ready only" in {
    test(new DCDemux(UInt(8.W))(1, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(2)(n => dut.io.out(n).map(_.initSink()))

      dut.io.sel(0).poke(0.U)
      dut.io.out(0)(0).ready.poke(1)
      step(1)
      dut.io.in(0).ready.expect(1)
    }
  }

  for (N <- 2 to 8) {
    "DCDemux" should s"work 1 x $N" in {
      test(new DCDemux(UInt(8.W))(1, N)).withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(N)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
        for (selected <- 0 until N) {
          var selectedInterface = selected
          var selectedPort      = 0
          var selectedInput     = 0
          val otherInterfaces   = (0 until N).filter(_ != selectedInterface)

          // Send selected input port to selected output interface
          dut.io.sel(selectedInput).poke(selectedInterface)
          val forks = Seq(
            fork {
              inSources(selectedInput).enqueueSeq(groupedSeqs(0))
            },
            fork {
              outSinks(selectedInterface)(selectedPort).expectDequeueSeq(groupedSeqs(0))
            }
          ) ++ otherInterfaces.map { otherInterface =>
            fork {
              outSinks(otherInterface)(selectedPort).expectInvalid()
            }
          }

          forks.foreach(_.join())
          dut.clock.step()
        }
      }
    }
  }
  "DCDemux" should "work 2 x 2" in {
    test(new DCDemux(UInt(8.W))(2, 2)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(2)(n => dut.io.out(n).map(_.initSink()))

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 1
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1)(1).expectDequeueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1)(0).expectInvalid()
      }.fork {
        outSinks(0)(1).expectInvalid()
      }.joinAndStep()

      // send input port 0 to output interface 1
      dut.io.sel(0).poke(1.U)
      // send input port 1 to output interface 0
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0)(1).expectDequeueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0)(0).expectInvalid()
      }.fork {
        outSinks(1)(1).expectInvalid()
      }.joinAndStep()
    }
  }

  "DCDemux" should "emit Verilog" in {
    GenerateVerilog(new DCDemux(UInt(8.W))(1, 2))
  }
}
