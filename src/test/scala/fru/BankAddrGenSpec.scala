package wood.fru

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class BankAddrGenSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig
  val TEST_SIZE = 1024

  "BankAddrGen" should "work" in {
    test(new BankAddrGen(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      val seq = Seq.fill(TEST_SIZE)(scala.util.Random.nextInt((scala.math.pow(2, 32) - 1).toInt))
      for(i <- 0 until TEST_SIZE) {
        dut.io.in.fetchpc.poke(seq(i).U)
        for(j <- 0 until config.nWide) {
          dut.io.out.controller(j).pc.expect((seq(i) + j * 4).U)
        }
        step()
      }
    }
  }

  "BankAddrGen" should "emit Verilog" in {
    GenerateVerilog(new BankAddrGen(new WoodConfig))
  }
}