package wood.exu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class CSRSpec extends AnyFlatSpec with ChiselScalatestTester {

  "CSR" should "work" in {
    test(new CSR(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.addr.poke(CSRs.marchid.U)
      dut.io.ren.poke(false.B)
      dut.io.retired.poke(true.B)
      dut.io.wdata.poke(0.U)
      dut.io.wen.poke(false.B)

      step()
      dut.io.wen.poke(true.B)
      step()
      dut.io.wen.poke(false.B)
      step()
      dut.io.wdata.poke("hFFFF_FFFF".U)
      dut.io.wen.poke(true.B)
      step(10)
    }
  }

  "CSR" should "emit Verilog" in {
    GenerateVerilog(new CSR(new WoodConfig))
  }
}
