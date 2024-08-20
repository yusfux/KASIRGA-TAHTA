package wood.fru

import chisel3._
import chiseltest._
import scala.util.Random
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class BranchPredictorSpec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 1024
  val config    = new WoodConfig()
  val pc        = BigInt(config.pcInitAddr.stripPrefix("h").replace("_", ""), 16)
  val pclist    = for (i <- 0 until TEST_SIZE) yield pc + 4 * i

  /*
    TODO: only the main functionality is tested, more comprehensive tests should be added asap
   */
  "BranchPredictor" should "work" in {
    test(new BranchPredictor(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.pc.poke(pc.U)
      dut.io.bpBus.map(_.valid.poke(false.B))
      dut.io.bpBus.map(_.bits.mispredict.poke(false.B))
      dut.io.bpBus.map(_.bits.exception.poke(false.B))
      dut.io.bpBus.map(_.bits.pc.poke(0.U))
      dut.io.bpBus.map(_.bits.taken.poke(false.B))
      dut.io.bpBus.map(_.bits.targetPC.poke(0.U))
      step()

      for (i <- 0 until TEST_SIZE) {
        for (i <- 0 until config.nWide) {
          dut.io.bpBus(i).valid.poke(Random.nextBoolean().B)
          dut.io.bpBus(i).bits.mispredict.poke(Random.nextBoolean().B)
          dut.io.bpBus(i).bits.exception.poke(Random.nextBoolean().B)
          dut.io.bpBus(i).bits.pc.poke(pclist(Random.nextInt(TEST_SIZE)))
          dut.io.bpBus(i).bits.taken.poke(Random.nextBoolean().B)
          dut.io.bpBus(i).bits.targetPC.poke(pclist(Random.nextInt(TEST_SIZE)))

          dut.io.pc.poke(pclist(Random.nextInt(TEST_SIZE)))
        }
        step()
      }

    }
  }

  "BranchPredictor" should "emit Verilog" in {
    GenerateVerilog(new BranchPredictor(new WoodConfig))
  }
}
