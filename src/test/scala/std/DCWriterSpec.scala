package wood.std

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import chisel3.experimental.BundleLiterals._
import org.scalatest.ParallelTestExecution

class DCWriterSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {

  "DCWriter" should s"work 1 port 32 depth and zero pattern" in {
    val numPorts  = 1
    val depth     = 32
    val dataWidth = 8
    val pattern   = "zero"
    test(new DCWriter(numPorts, depth, dataWidth, pattern)).withAnnotations(GetBackendAnnotation()) { dut =>
      val outSinks = dut.io.out.map(_.initSink())

      val stopVal  = ((1 << log2Ceil(depth)) - 1)
      val expected = (0 to stopVal).toList
      println("stopVal: ", stopVal)
      println("Expected: ", expected)
      val testWriteSeq = expected.map(i =>
        new WritePortI(UInt(8.W))(log2Ceil(depth)).Lit(
          _.addr   -> i.U,
          _.data   -> 0.U,
          _.enable -> true.B
        )
      )

      fork {
        outSinks(0).expectDequeueSeq(testWriteSeq)
      }.joinAndStep()
    }
  }
  "DCWriter" should s"work 1 port 32 depth and one pattern" in {
    val numPorts  = 1
    val depth     = 32
    val dataWidth = 8
    val pattern   = "one"
    test(new DCWriter(numPorts, depth, dataWidth, pattern)).withAnnotations(GetBackendAnnotation()) { dut =>
      val outSinks = dut.io.out.map(_.initSink())

      val stopVal  = ((1 << log2Ceil(depth)) - 1)
      val expected = (0 to stopVal).toList
      println("stopVal: ", stopVal)
      println("Expected: ", expected)
      val testWriteSeq = expected.map(i =>
        new WritePortI(UInt(8.W))(log2Ceil(depth)).Lit(
          _.addr   -> i.U,
          _.data   -> 1.U,
          _.enable -> true.B
        )
      )
      fork {
        outSinks(0).expectDequeueSeq(testWriteSeq)
      }.joinAndStep()
    }
  }

  "DCWriter" should s"work 1 port 32 depth and addr pattern" in {
    val numPorts  = 1
    val depth     = 32
    val dataWidth = 8
    val pattern   = "addr"
    test(new DCWriter(numPorts, depth, dataWidth, pattern)).withAnnotations(GetBackendAnnotation()) { dut =>
      val outSinks = dut.io.out.map(_.initSink())

      val stopVal  = ((1 << log2Ceil(depth)) - 1)
      val expected = (0 to stopVal).toList
      println("stopVal: ", stopVal)
      println("Expected: ", expected)
      val testWriteSeq = expected.map(i =>
        new WritePortI(UInt(8.W))(log2Ceil(depth)).Lit(
          _.addr   -> i.U,
          _.data   -> i.U,
          _.enable -> true.B
        )
      )
      fork {
        outSinks(0).expectDequeueSeq(testWriteSeq)
      }.joinAndStep()
    }
  }

  "DCWriter" should "emit Verilog in addr pattern" in {
    GenerateVerilog(new DCWriter(4, 35, 88, "addr"))
  }
  "DCWriter" should "emit Verilog in zero pattern" in {
    GenerateVerilog(new DCWriter(4, 35, 88, "zero"))
  }
  "DCWriter" should "emit Verilog in one pattern" in {
    GenerateVerilog(new DCWriter(4, 35, 88, "one"))
  }
}
