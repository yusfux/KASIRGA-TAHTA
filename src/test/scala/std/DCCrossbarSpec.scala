package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCCrossbarSpec extends AnyFlatSpec with ChiselScalatestTester {
  val numDataPerGroup = 30

  "DCCrossbar" should s"work 1 to (1)" in {
    val numOutputs = List(1)
    val numInputs  = 1
    test(new DCCrossbar(UInt(8.W))(numInputs, numOutputs))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numOutputs.length)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.joinAndStep()
        dut.clock.step(1)

        dut.clock.step(1)
      }
  }

  "DCCrossbar" should s"work 1 to (2)" in {
    val numOutputs = List(2)
    val numInputs  = 1
    test(new DCCrossbar(UInt(8.W))(numInputs, numOutputs))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numOutputs.length)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(1).expectInvalid()
        }.joinAndStep()

        dut.clock.step(1)
      }
  }

  "DCCrossbar" should "work 1 to (1,1)" in {
    val numOutputs = List(1, 1)
    val numInputs  = 1
    test(new DCCrossbar(UInt(8.W))(numInputs, numOutputs))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numOutputs.length)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)

        // send input port 0 to output interface 0
        dut.io.sel(0).poke(0.U)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(1)(0).expectInvalid()
        }.joinAndStep()

        // send input port 0 to output interface 1
        dut.io.sel(0).poke(1.U)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(1)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(0).expectInvalid()
        }.joinAndStep()
      }
  }
  "DCCrossbar" should "work 2 to (2,2)" in {
    val numOutputs = List(2, 2)
    val numInputs  = 2
    test(new DCCrossbar(UInt(8.W))(numInputs, numOutputs)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.map(_.initSource())
      val outSinks  = Array.tabulate(numOutputs.length)(n => dut.io.out(n).map(_.initSink()))

      val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)

      // send input port 0 to output interface 0
      dut.io.sel(0).poke(0.U)
      // send input port 1 to output interface 0
      dut.io.sel(1).poke(0.U)

      fork {
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0)(1).expectDequeueSeq(groupedSeqs(1))
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
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1)(1).expectDequeueSeq(groupedSeqs(1))
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
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(1)(0).expectDequeueSeq(groupedSeqs(1))
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
        inSources(0).enqueueSeq(groupedSeqs(0))
      }.fork {
        inSources(1).enqueueSeq(groupedSeqs(1))
      }.fork {
        outSinks(1)(0).expectDequeueSeq(groupedSeqs(0))
      }.fork {
        outSinks(0)(0).expectDequeueSeq(groupedSeqs(1))
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
