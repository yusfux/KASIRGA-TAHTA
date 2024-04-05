package regfile

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
class RegFileSpec extends AnyFlatSpec with ChiselScalatestTester {

  "RegFile" should "work" in {
    test(new RegFile(32, 1600000, 2, 1)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
      for (w <- 0 until 16) {
        for (d <- 0 until 256) {
          // Write data to the register file
          dut.io.writePorts(0).enable.poke(true.B)
          dut.io.writePorts(0).addr.poke(w.U)
          dut.io.writePorts(0).data.poke(d.U)
          dut.clock.step(1)

          // Read data from the register file
          for (r <- 0 until 16) {
            dut.io.readPorts(0).addr.poke(r.U)
            dut.io.readPorts(1).addr.poke(r.U)
            dut.clock.step(1)

            // Check the read data
            if (r == w) {
              dut.io.readPorts(0).data.expect(d.U)
              dut.io.readPorts(1).data.expect(d.U)
            }
          }
        }
      }
    }
  }
}
