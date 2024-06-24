package wood.exu

// import chisel3._
import chiseltest._
// import wood.fru.MI

import org.scalatest.flatspec.AnyFlatSpec
// import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.util.{GenerateVerilog}

class ReservationStationSpec extends AnyFlatSpec with ChiselScalatestTester {

  "ReservationStationRow" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new ReservationStationRow(numPorts))
  }
  "ReservationStation" should "emit Verilog" in {
    val numPorts = 2
    GenerateVerilog(new ReservationStation(numPorts))
  }

}
