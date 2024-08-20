package wood.fru

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog}
import wood.WoodConfig

class Fetch2Spec extends AnyFlatSpec with ChiselScalatestTester {

  "Fetch2Stage" should "emit Verilog" in {
    GenerateVerilog(new Fetch2Stage(new WoodConfig))
  }
}
