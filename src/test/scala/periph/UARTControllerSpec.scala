package wood.periph

import chiseltest._
import chisel3._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GetBackendAnnotation}
import wood.WoodConfig

class UARTControllerSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig

  val dataString = "Hello, World!".reverse
  val dataBits   = dataString.map(_.toInt).map(_.toBinaryString).map(s => "0" * (8 - s.length) + s).mkString
  val baudDiv    = 100

  "UART" should "work for recv" in {
    test(new UARTReciever(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.uart_rx.poke(true.B)
      dut.io.rx_en.poke(true.B)
      dut.io.baud_div.poke(baudDiv.U)
      step(10)
      for (i <- 0 until dataString.length) {
        dut.io.uart_rx.poke(false.B)
        step(baudDiv)
        for (j <- 0 until 8) {
          dut.io.uart_rx.poke(dataBits(dataBits.length() - 1 - (i * 8 + j)).asDigit.B)
          step(baudDiv)
        }
        dut.io.uart_rx.poke(true.B)
        step(baudDiv)
      }
    }
  }

  "UART" should "work for trans" in {
    test(new UARTTransmitter(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      val haha  = "YusfAydinHaha".reverse.map(_.toInt).map(_.toBinaryString).map(s => "0" * (8 - s.length) + s)
      var value = ""
      dut.clock.setTimeout(0)

      dut.io.baud_div.poke(baudDiv.U)
      dut.io.tx_en.poke(true.B)
      dut.io.wen.poke(true.B)
      (0 until haha.length).foreach(i => {
        dut.io.data.poke(("b" + haha(i)).U)
        step()
      })
      dut.io.wen.poke(false.B)

      for (a <- 0 until "YusfAydinHaha".length()) {
        while (dut.io.uart_tx.peekBoolean()) {
          step()
        }

        step(baudDiv / 2)

        for (i <- 0 until 8) {
          step(baudDiv)

          if (dut.io.uart_tx.peekBoolean()) {
            value += "1"
          } else {
            value += "0"
          }
        }
        step(baudDiv + baudDiv / 2)
      }

      value = value.reverse
      println(value.grouped(8).map(s => Integer.parseInt(s, 2).toChar).mkString)
    }
  }
}
