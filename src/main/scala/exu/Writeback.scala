package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

class WritebackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Vec(config.nWide, ValidIO(new DataBus(config)))
  })

  (0 until config.nWide).foreach(j => {
    io.writebackBus(j).bits.tag  := io.in(j).bits.rdTag
    io.writebackBus(j).bits.data := io.in(j).bits.rdData
    io.writebackBus(j).valid     := io.in(j).valid
    io.in(j).ready               := 1.U // This stage is always ready
  })
}
