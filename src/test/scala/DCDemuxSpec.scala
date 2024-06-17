package dcdemux

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.GenerateVerilog

class DCDemuxSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCDemux" should s"work 1 x 1" in {
    test(new DCDemux(UInt(8.W))(1, 1)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = dut.io.out(0).map(_.initSink())

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
  "DCDemux" should "work 1 x 2" in {
    test(new DCDemux(UInt(8.W))(1, 2)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSource  = dut.io.in.head.initSource()
      val outSink_0 = dut.io.out(0).head.initSink()
      val outSink_1 = dut.io.out(1).head.initSink()

      // Create a sequence of data
      val numData = 100
      val data    = Seq.fill(numData)(8.U)

      // Set sel to 0 and expect the data on the first output
      dut.io.sel.head.poke(0.U)
      fork {
        // Enqueue the data to the input port
        inSource.enqueueSeq(data)
      }.fork {
        outSink_0.expectDequeueSeq(data)
      }.joinAndStep()

      dut.io.sel.head.poke(1.U)
      fork {
        // Enqueue the data to the input port
        inSource.enqueueSeq(data)
      }.fork {
        outSink_1.expectDequeueSeq(data)
      }.joinAndStep()
      dut.clock.step(2)
    }
  }
  for (N <- 3 to 8) {
    "DCDemux" should s"work 1 x $N" in {
      test(new DCDemux(UInt(8.W))(1, N)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        // Initialize the source and sinks
        val inSource = dut.io.in.head.initSource()
        val outSinks = Seq.tabulate(N)(n => dut.io.out(n).head.initSink())

        // Create a sequence of data
        val numData = 100
        val data    = Seq.fill(numData)(8.U)

        // Test each output by setting sel and expecting the data
        for (n <- 0 until N) {
          dut.io.sel.head.poke(n.U)
          fork {
            inSource.enqueueSeq(data)
          }.fork {
            outSinks(n).expectDequeueSeq(data)
          }.joinAndStep()
        }
        dut.clock.step(2)
      }
    }
  }
  "DCDemux" should "work 2 x 2" in {
    test(new DCDemux(UInt(8.W))(2, 2)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      // Initialize the sources and sinks
      val inSources  = dut.io.in.map(_.initSource())
      val outSinks_0 = dut.io.out(0).map(_.initSink())
      val outSinks_1 = dut.io.out(1).map(_.initSink())

      // Create a sequence of zeros and ones
      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // Set sel signals for both demuxes to 0
      dut.io.sel(0).poke(0.U)
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks_0(0).expectDequeueSeq(zeros) // Expect zeros on first output of first demux
      }.fork {
        outSinks_0(1).expectDequeueSeq(ones) // Expect ones on second output of first demux
      }.joinAndStep()

      // Set sel signals for both demuxes to 1
      dut.io.sel(0).poke(1.U)
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks_1(0).expectDequeueSeq(zeros) // Expect zeros on first output of second demux
      }.fork {
        outSinks_1(1).expectDequeueSeq(ones) // Expect ones on second output of second demux
      }.joinAndStep()

      dut.clock.step(2)
    }
  }
  for (N <- 3 to 8) {
    "DCDemux" should s"work 2 x $N" in {
      test(new DCDemux(UInt(8.W))(2, N)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
        // Initialize the sources and sinks
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(N)(n => dut.io.out(n).map(_.initSink()))

        // Create a sequence of zeros and ones
        val numData = 100
        val zeros   = Seq.fill(numData)(0.U)
        val ones    = Seq.fill(numData)(1.U)

        // Test each combination of select signals
        for (sel0 <- 0 until N) {
          for (sel1 <- 0 until N) {
            // Set sel signals
            dut.io.sel(0).poke(sel0.U)
            dut.io.sel(1).poke(sel1.U)

            fork {
              inSources(0).enqueueSeq(zeros)
            }.fork {
              inSources(1).enqueueSeq(ones)
            }.fork {
              outSinks(sel0)(0).expectDequeueSeq(zeros) // Expect zeros on the selected output of the first demux
            }.fork {
              outSinks(sel1)(1).expectDequeueSeq(ones) // Expect ones on the selected output of the second demux
            }.joinAndStep()

            // Step the clock to observe changes
            dut.clock.step(2)
          }
        }
      }
    }
  }

  "DCDemux" should "emit Verilog" in {
    GenerateVerilog(new DCDemux(UInt(8.W))(1, 2))
  }
}
