package decoder
import chisel3.util.experimental.decode.decoder
import chisel3.util.experimental.decode.EspressoMinimizer

import circt.stage.ChiselStage
import chisel3._
import chisel3.util._

import wood._

class Decoder() extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
    val mi = Output(UInt(Decode.outWidth.W))
  })

  val instDecoder: UInt = decoder(minimizer = EspressoMinimizer, input = io.inst, truthTable = Decode.miTable)
  io.mi := instDecoder
}

object DecoderMain extends App {
  GenerateVerilog(new Decoder())
}
