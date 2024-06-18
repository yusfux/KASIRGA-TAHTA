package dcdemux

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.{GenerateVerilog, GetBackendAnnotation}

class DCDemuxSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCDemux" should s"work 1 x 1" in {
    test(new DCDemux(UInt(8.W))(1, 1)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(1)(n => dut.io.out(n).map(_.initSink()))

      val data    = 8.U
      val numData = 100
      val inputs  = Seq.fill(numData)(data)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      fork {
        inSources(0).enqueueSeq(inputs)
      }.fork {
        outSinks(0)(0).expectDequeueSeq(inputs)
      }.joinAndStep()
    }
  }
  for (N <- 2 to 8) {
    "DCDemux" should s"work 1 x $N" in {
      test(new DCDemux(UInt(8.W))(1, N)).withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(N)(n => dut.io.out(n).map(_.initSink()))

        val numData = 100
        val data    = Seq.fill(numData)(8.U)

        for (selected <- 0 until N) {
          var selectedInterface = selected
          var selectedPort      = 0
          var selectedInput     = 0
          val otherInterfaces   = (0 until N).filter(_ != selectedInterface)

          // Send selected input port to selected output interface
          dut.io.sel(selectedInput).poke(selectedInterface)
          val forks = Seq(
            fork {
              inSources(selectedInput).enqueueSeq(data)
            },
            fork {
              outSinks(selectedInterface)(selectedPort).expectDequeueSeq(data)
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

      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 1
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0)(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(1)(1).expectDequeueSeq(ones)
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
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0)(1).expectDequeueSeq(ones)
      }.fork {
        outSinks(1)(0).expectDequeueSeq(zeros)
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
