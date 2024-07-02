package wood.exu

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.ParallelTestExecution

import wood.util.TestGenerateVerilog
import wood.{TestConfig, WoodConfig}

class ReservationStationSpec extends AnyFlatSpec with ChiselScalatestTester with ParallelTestExecution {
  val tconfig = new TestConfig()
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ReservationStation" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ReservationStation(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
  (1 to tconfig.maxWidth).foreach(j => {
    val config = new WoodConfig(nWide = j)
    "ReservationStationRow" should s"emit Verilog ${j} wide" in {
      TestGenerateVerilog(new ReservationStationRow(config), testNames.filter(_.contains("emit Verilog")), j)
    }
  })
}
