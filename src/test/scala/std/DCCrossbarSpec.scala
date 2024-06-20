package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class DCCrossbarSpec extends AnyFlatSpec with ChiselScalatestTester {

  "DCCrossbar" should s"work 1 to (1)" in {
    test(new DCCrossbar(UInt(8.W))(1, List(1)))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(1)(n => dut.io.out(n).map(_.initSink()))

        val data    = 8.U
        val numData = 100
        val inputs  = Seq.fill(numData)(data)

        fork {
          inSources(0).enqueueSeq(inputs)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(inputs)
        }.joinAndStep()
        dut.clock.step(1)

        dut.clock.step(1)
      }
  }

  "DCCrossbar" should s"work 1 to (2)" in {
    test(new DCCrossbar(UInt(8.W))(1, List(2)))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(1)(n => dut.io.out(n).map(_.initSink()))

        val data    = 8.U
        val numData = 100
        val inputs  = Seq.fill(numData)(data)

        fork {
          inSources(0).enqueueSeq(inputs)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(inputs)
        }.fork {
          outSinks(0)(1).expectInvalid()
        }.joinAndStep()

        dut.clock.step(1)
      }
  }

  "DCCrossbar" should "work 1 to (1,1)" in {
    test(new DCCrossbar(UInt(8.W))(1, List(1, 1)))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(2)(n => dut.io.out(n).map(_.initSink()))

        val numData = 100
        val data    = Seq.fill(numData)(8.U)

        // send input port 0 to output interface 0
        dut.io.sel(0).poke(0.U)
        fork {
          inSources(0).enqueueSeq(data)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(data)
        }.fork {
          outSinks(1)(0).expectInvalid()
        }.joinAndStep()

        // send input port 0 to output interface 1
        dut.io.sel(0).poke(1.U)
        fork {
          inSources(0).enqueueSeq(data)
        }.fork {
          outSinks(1)(0).expectDequeueSeq(data)
        }.fork {
          outSinks(0)(0).expectInvalid()
        }.joinAndStep()
      }
  }
  "DCCrossbar" should "work 2 to (2,2)" in {
    test(new DCCrossbar(UInt(8.W))(2, List(2, 2))).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(2)(n => dut.io.out(n).map(_.initSink()))

      // Create a sequence of zeros and ones
      val numData = 100
      val zeros   = Seq.fill(numData)(0.U)
      val ones    = Seq.fill(numData)(1.U)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 0
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(0)(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(0)(1).expectDequeueSeq(ones)
      }.fork {
        outSinks(1)(0).expectInvalid()
      }.fork {
        outSinks(1)(1).expectInvalid()
      }.joinAndStep()

      // send input port 0 to output interface 1
      dut.io.sel(0).poke(1.U)
      // send input port 1 to output interface 1
      dut.io.sel(1).poke(1.U)

      fork {
        inSources(0).enqueueSeq(zeros)
      }.fork {
        inSources(1).enqueueSeq(ones)
      }.fork {
        outSinks(1)(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(1)(1).expectDequeueSeq(ones)
      }.fork {
        outSinks(0)(0).expectInvalid()
      }.fork {
        outSinks(0)(1).expectInvalid()
      }.joinAndStep()

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
        outSinks(1)(0).expectDequeueSeq(ones)
      }.fork {
        outSinks(1)(1).expectInvalid()
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
        outSinks(1)(0).expectDequeueSeq(zeros)
      }.fork {
        outSinks(0)(0).expectDequeueSeq(ones)
      }.fork {
        outSinks(1)(1).expectInvalid()
      }.fork {
        outSinks(0)(1).expectInvalid()
      }.joinAndStep()
    }
  }
  "DCCrossbar" should "emit Verilog" in {
    GenerateVerilog(new DCCrossbar(UInt(8.W))(3, List(1, 2, 3, 4)))
  }
}
