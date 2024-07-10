package wood.std

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.{GenerateVerilog, GetBackendAnnotation, GetGroupedSequences}

class DCQueueInitializerSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val numPorts  = 1
  val depth     = 32
  val dataWidth = 8

  def getTestSeq(depth: Int, dataPattern: String = "zero"): List[UInt] = {
    val stopVal = ((1 << log2Ceil(depth)) - 1)
    var expected: List[Int] = (0 to stopVal).toList

    dataPattern match {
      case "count" => expected = expected
      case "one"   => expected = expected.map(_ => 1)
      case _       => expected = expected.map(_ => 0)
    }

    println("stopVal: ", stopVal)
    println("Expected: ", expected)
    val testWriteSeq = expected.map(i => i.U)
    testWriteSeq
  }

  "DCQueueInitializer" should s"work 1 port 32 depth and count pattern" in {
    val pattern = "count"
    test(new DCQueueInitializer(UInt(dataWidth.W))(numPorts, depth, pattern))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val inSources = dut.io.in.map(_.initSource())
        val outSinks  = dut.io.out.map(_.initSink())

        val groupedSeqs = GetGroupedSequences(1, depth)
        fork {
          inSources(0).enqueueSeq(groupedSeqs(0))
        }.fork {
          outSinks(0).expectDequeueSeq(getTestSeq(depth, "count"))
          outSinks(0).expectDequeueSeq(groupedSeqs(0))
        }.joinAndStep()
      }
  }

  "DCQueueInitializer" should "emit Verilog in count pattern" in {
    GenerateVerilog(new DCQueueInitializer(UInt(dataWidth.W))(4, 35, "count"))
  }
  "DCQueueInitializer" should "emit Verilog in zero pattern" in {
    GenerateVerilog(new DCQueueInitializer(UInt(dataWidth.W))(4, 35, "zero"))
  }
  "DCQueueInitializer" should "emit Verilog in one pattern" in {
    GenerateVerilog(new DCQueueInitializer(UInt(dataWidth.W))(4, 35, "one"))
  }
}
