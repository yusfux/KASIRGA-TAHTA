package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GetBackendAnnotation, GenerateVerilog}

class PCQueueSpec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 1024
  val config = new WoodConfig

  "PCQueue" should "work" in {
    test(new PCQueue(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      val inSources = dut.io.in.initSource()
      val outSinks  = dut.io.out.initSink()

      var i = 0
      val data = Seq.fill(TEST_SIZE)(new Bundle {
        val fetchpc = {
          i = i + 1
          i
        }
        val mask = Seq.fill(config.nWide)(scala.util.Random.nextBoolean())
      })

      fork {
        for(i <- 0 until TEST_SIZE) {
          val x = data(i)
          if(i == config.pcQueueDepth / 2) {
            //dut.io.flush.poke(true.B)
          }
          if(i == config.pcQueueDepth / 2 + 1) {
            dut.io.flush.poke(false.B)
          }

          dut.io.in.bits.fetchpc.poke(x.fetchpc.U)
          for(j <- 0 until config.nWide) {
            dut.io.in.bits.mask(j).poke(x.mask(j).B)
          }
          dut.io.in.valid.poke(true)
          fork
            .withRegion(Monitor) {
              while (!dut.io.in.ready.peekBoolean()) {
                step(1)
              }
            }
            .joinAndStep()
          dut.io.in.valid.poke(false)
        }
      }.fork {
        for(i <- 0 until TEST_SIZE) {
          step(scala.util.Random.nextInt(config.pcQueueDepth) + 1)  // to control the async read-write situations
          val x = data(i)
          dut.io.out.ready.poke(true)
          fork
            .withRegion(Monitor) {
              while (!dut.io.out.valid.peekBoolean()) {
                step(1)
              }
              dut.io.out.valid.expect(true.B)
              dut.io.out.bits.fetchpc.expect(x.fetchpc.U)
              for(j <- 0 until config.nWide) {
                dut.io.out.bits.mask(j).expect(x.mask(j).B)
              }
            }
            .joinAndStep()
          dut.io.out.ready.poke(false)
        }
      }.joinAndStep()
    }
  }


  "PCQueue" should "emit Verilog" in {
    GenerateVerilog(new Fetch1Stage(new WoodConfig))
  }
}
