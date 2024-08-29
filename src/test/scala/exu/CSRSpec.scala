package wood.exu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.{GenerateVerilog, GetBackendAnnotation}

class CSRSpec extends AnyFlatSpec with ChiselScalatestTester {

  "CSR" should "work" in {
    test(new CSR(new WoodConfig)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.csr.ren.poke(true.B)
      dut.io.csr.addr.poke(CSRs.misa.U)
      step()
      println(dut.io.csr.rdata.peek().litValue)
    }
  }

  "CSR" should "emit Verilog" in {
    GenerateVerilog(new CSR(new WoodConfig))
  }
}
