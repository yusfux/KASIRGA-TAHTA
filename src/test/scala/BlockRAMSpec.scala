package blockram

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.{GenerateVerilog, GetBackendAnnotation}

class BlockRAMSpec extends AnyFlatSpec with ChiselScalatestTester {

  val numWritePorts = 2
  val numReadPorts  = 2
  val depth         = 128
  val dataWidth     = 32

  "BlockRAM" should s"work ${numReadPorts}r ${numWritePorts}w" in {
    test(new BlockRAM(BlockRAMParams(dataWidth, depth, numReadPorts, numWritePorts)))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val numTests = 32
        for (testData <- 0 until numTests) {
          for (testAdr <- 0 until depth) {
            for (w <- 0 until numWritePorts) {
              dut.io.wp(w).enable.poke(true.B)
              dut.io.wp(w).addr.poke(testAdr.U)
              dut.io.wp(w).data.poke(testData.U)
              dut.clock.step(1)

              for (r <- 0 until numReadPorts) {
                dut.io.rip(r).addr.poke(testAdr.U)
                if (r == testAdr) {
                  dut.io.rop(r).data.expect(testData.U)
                }
              }
            }
          }
        }
      }
  }

  "BlockRAM" should "emit Verilog" in {
    GenerateVerilog(new BlockRAM(BlockRAMParams(dataWidth, depth, numReadPorts, numWritePorts)))
  }

  "DecoupledBlockRAM" should s"work ${numReadPorts}r ${numWritePorts}w" in {
    test(new DecoupledBlockRAM(BlockRAMParams(dataWidth, depth, numReadPorts, numWritePorts)))
      .withAnnotations(GetBackendAnnotation()) { dut =>
        val numTests = 32

        // Initialize the sources and sinks
        val rips = dut.io.rip.map(_.initSource())
        val rops = dut.io.rop.map(_.initSink())
        val wps  = dut.io.wp.map(_.initSource())

        // Create a sequence of testData
        val numData = 100
        val numbers = 1 to numData
        val testReadISeq = numbers.map(i =>
          new ReadPortI(dataWidth, log2Ceil(depth)).Lit(
            _.addr -> i.U
          )
        )
        val testReadOSeq = numbers.map(i =>
          new ReadPortO(dataWidth, log2Ceil(depth)).Lit(
            _.data -> i.U,
            _.addr -> i.U
          )
        )

        val testWriteSeq = numbers.map(i =>
          new WritePortI(dataWidth, log2Ceil(depth)).Lit(
            _.addr   -> i.U,
            _.data   -> i.U,
            _.enable -> true.B
          )
        )

        fork {
          wps(0).enqueueSeq(testWriteSeq)
        }.fork {
          wps(1).enqueueSeq(testWriteSeq)
        }.joinAndStep()

        fork {
          rips(0).enqueueSeq(testReadISeq)
        }.fork {
          rops(0).expectDequeueSeq(testReadOSeq)
        }.joinAndStep()

        dut.clock.step(1)
      }
  }

  "DecoupledBlockRAM" should "emit Verilog" in {
    GenerateVerilog(new DecoupledBlockRAM(BlockRAMParams(dataWidth, depth, numReadPorts, numWritePorts)))
  }
}
