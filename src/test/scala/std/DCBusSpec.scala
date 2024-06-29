package wood.std

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class DCBusSpec extends AnyFlatSpec with ChiselScalatestTester {

  val numData = 100

  val zero  = 0.U
  val one   = 1.U
  val two   = 2.U
  val zeros = Seq.fill(numData)(zero)
  val ones  = Seq.fill(numData)(one)
  val twos  = Seq.fill(numData)(two)

  "DCBus" should s"work 1f(1p) to 1f" in {
    val numPorts      = 1
    val numInterfaces = 1
    test(new DCBus(UInt(8.W))(numPorts, numInterfaces))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = Array.tabulate(numInterfaces)(n => dut.io.out(n).map(_.initSink()))

        fork {
          inSources(0).enqueueSeq(zeros)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(zeros)
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

        fork {
          inSources(0).enqueueSeq(zeros)
        }.fork {
          inSources(1).enqueueSeq(ones)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(zeros)
        }.fork {
          outSinks(0)(1).expectDequeueSeq(ones)
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

        fork {
          inSources(0).enqueueSeq(zeros)
        }.fork {
          inSources(1).enqueueSeq(ones)
        }.fork {
          inSources(2).enqueueSeq(twos)
        }.fork {
          outSinks(0)(0).expectDequeueSeq(zeros)
        }.fork {
          outSinks(0)(1).expectDequeueSeq(ones)
        }.fork {
          outSinks(0)(2).expectDequeueSeq(twos)
        }.fork {
          outSinks(1)(0).expectDequeueSeq(zeros)
        }.fork {
          outSinks(1)(1).expectDequeueSeq(ones)
        }.fork {
          outSinks(1)(2).expectDequeueSeq(twos)
        }.fork {
          outSinks(2)(0).expectDequeueSeq(zeros)
        }.fork {
          outSinks(2)(1).expectDequeueSeq(ones)
        }.fork {
          outSinks(2)(2).expectDequeueSeq(twos)
        }.joinAndStep()
      }
  }
  "DCBus" should "emit Verilog" in {
    GenerateVerilog(new DCBus(UInt(8.W))(2, 4))
  }
}
