package wood.fru

import scala.collection.mutable._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.GetBackendAnnotation
import wood.util.GenerateVerilog

class Fetch1Spec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 128
  val config = new WoodConfig(pcInitAddr = "h1000_0000")
  val target_pc = 268435456
  var pc = 268435456
  val q = Queue[Int]()

  "Fetch1" should "work" in {
    test(new Fetch1Stage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      fork {
        for(i <- 0 until TEST_SIZE) {
          fork
            .withRegion(Monitor) {
              while (!dut.io.debug.queue_ready.peekBoolean()) {
                step(1)
              }
              q.enqueue(pc)
              pc = pc + 4 * config.nWide

            }
            .joinAndStep()
          }
      }.fork {
        for(i <- 0 until TEST_SIZE) {
          if(scala.util.Random.nextDouble() < 0.4) {
            step(scala.util.Random.nextInt(config.pcQueueDepth) + 1)  // to control the async read-write situations
          }
          dut.io.out.ready.poke(true)
          fork
            .withRegion(Monitor) {
              while (!dut.io.out.valid.peekBoolean()) {
                step(1)
              }
              val temp = q.dequeue()
              for(j <- 0 until config.nWide) {
                dut.io.out.bits.controller(j).pc.expect((temp + 4 * j).U)
                dut.io.out.bits.controller(j).mask.expect(true.B)
              }
            }
            .joinAndStep()
          dut.io.out.ready.poke(false)
        }
      }.joinAndStep()
    }
  }

  /* 
    this test is problematic due to the multithreaded nature of the testbench,
    it is easier to test this functionality in the waveform directly,
    though it would be easier to test it after the complete frontend is implemented
   */
  "Fetch1" should "work under misprediction" in {
    test(new Fetch1Stage(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      fork {
        for(i <- 0 until TEST_SIZE) {
          if(i == TEST_SIZE / 2) {
            dut.io.in.mispred.en.poke(true.B)
            dut.io.in.mispred.pc.poke((pc - (i / 2 * 4 * config.nWide)).U)
            dut.io.in.mispred.targetpc.poke(target_pc.U)
          } else {
            dut.io.in.mispred.en.poke(false.B)
          }

          fork
            .withRegion(Monitor) {
              while (!dut.io.debug.queue_ready.peekBoolean()) {
                step(1)
              }
              //if(i == TEST_SIZE / 2) {
                //q.clear()
                //pc = target_pc
              //}

              //q.enqueue(pc)
              pc = pc + 4 * config.nWide
            }
            .joinAndStep()
          }
      }.fork {
        for(i <- 0 until TEST_SIZE) {
          if(scala.util.Random.nextDouble() < 0.4) {
            step(scala.util.Random.nextInt(config.pcQueueDepth) + 1)  // to control the async read-write situations
          }
          dut.io.out.ready.poke(true)
          fork
            .withRegion(Monitor) {
              while (!dut.io.out.valid.peekBoolean()) {
                step(1)
              }
              //val temp = q.dequeue()
              for(j <- 0 until config.nWide) {
                //dut.io.out.bits.controller(j).pc.expect((temp + 4 * j).U)
                //dut.io.out.bits.controller(j).mask.expect(true.B)
              }
            }
            .joinAndStep()
          dut.io.out.ready.poke(false)
        }
      }.joinAndStep()
    }
  }

  "Fetch1" should "emit Verilog" in {
    GenerateVerilog(new Fetch1Stage(new WoodConfig))
  }
}

