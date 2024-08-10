package wood.fru

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.WoodConfig
import wood.util.GenerateVerilog

class ICacheBankSpec extends AnyFlatSpec with ChiselScalatestTester {

  "ICacheBank" should "emit Verilog" in {
    GenerateVerilog(new ICacheBank(new WoodConfig))
  }
}
