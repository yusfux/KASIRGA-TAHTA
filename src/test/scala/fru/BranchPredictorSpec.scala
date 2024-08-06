package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class BranchPredictorSpec extends AnyFlatSpec with ChiselScalatestTester {
  val TEST_SIZE = 1024
  val config = new WoodConfig()
  val PC = "h1000_0000".U
  val PCXD = 268435456
  var pc = 268435456

  /* 
    TODO: only the main functionality is tested, more comprehensive tests should be added asap
   */
  "BranchPredictor" should "work" in {
    test(new BranchPredictor(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.in.fetchpc.poke(0.U)
      dut.io.in.mispred.fetchpc.poke(0.U)
      dut.io.in.mispred.pcidx.poke(0.U)
      dut.io.in.mispred.targetpc.poke(0.U)
      dut.io.in.mispred.en.poke(false.B)
      dut.io.in.mispred.taken.poke(false.B)
      step()

      for(i <- 0 until TEST_SIZE) {
        val rand = scala.util.Random.nextDouble() < 0.2
        val rtaken = scala.util.Random.nextDouble() < 0.8
        val rpc = scala.util.Random.nextInt(i + 1)
        

        dut.io.in.fetchpc.poke(pc)
        dut.io.in.mispred.fetchpc.poke((pc - (rpc * 4 * config.nWide)).U)
        dut.io.in.mispred.pcidx.poke((i % config.nWide).U)
        dut.io.in.mispred.targetpc.poke(PC)

        if(rand) {
          dut.io.in.mispred.en.poke(true.B)
          dut.io.in.mispred.taken.poke(rtaken.B)
          pc = PCXD
        } else {
          dut.io.in.mispred.en.poke(false.B)
          dut.io.in.mispred.taken.poke(false.B)
        }
        step()
        if(!rand) {
          pc = pc + 4 * config.nWide
        }
      }
    }
  }

  "BranchPredictor" should "emit Verilog" in {
    GenerateVerilog(new BranchPredictor(new WoodConfig))
  }
}