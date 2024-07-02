package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}
import org.scalatest.ParallelTestExecution

class DCBusSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numDataPerGroup = 30

  "DCBus" should s"work 1f(1p) to 1f" in {
    val numPorts      = 1
    val numInterfaces = 1
    test(new DCBus(UInt(8.W))(numPorts, numInterfaces))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numInterfaces)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(1, numDataPerGroup)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.joinAndStep()
      }
  }
  "DCBus" should s"work 1f(2p) to 1f" in {
    val numPorts      = 2
    val numInterfaces = 1
    test(new DCBus(UInt(8.W))(numPorts, numInterfaces))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numInterfaces)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(2, numDataPerGroup)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          inSources(1).enqueueSeq(groupedSeqs(1))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(1).expectDequeueSeq(groupedSeqs(1))
        }.joinAndStep()
      }
  }
  "DCBus" should s"work 1f(3p) to 3f" in {
    val numPorts      = 3
    val numInterfaces = 3
    test(new DCBus(UInt(8.W))(numPorts, numInterfaces))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numInterfaces)(n => dut.io.out(n).map(_.initSink()))

        val groupedSeqs = GetGroupedSequences(3, numDataPerGroup)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          inSources(1).enqueueSeq(groupedSeqs(1))
        }.fork {
          inSources(2).enqueueSeq(groupedSeqs(2))
        }.fork {
          outSinks(0)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0)(1).expectDequeueSeq(groupedSeqs(1))
        }.fork {
          outSinks(0)(2).expectDequeueSeq(groupedSeqs(2))
        }.fork {
          outSinks(1)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(1)(1).expectDequeueSeq(groupedSeqs(1))
        }.fork {
          outSinks(1)(2).expectDequeueSeq(groupedSeqs(2))
        }.fork {
          outSinks(2)(0).expectDequeueSeq(groupedSeqs(0))
        }.fork {
          outSinks(2)(1).expectDequeueSeq(groupedSeqs(1))
        }.fork {
          outSinks(2)(2).expectDequeueSeq(groupedSeqs(2))
        }.joinAndStep()
      }
  }
  "DCBus" should "emit Verilog" in {
    GenerateVerilog(new DCBus(UInt(8.W))(2, 4))
  }
}
