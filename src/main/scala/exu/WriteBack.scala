package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

class WriteBackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writeBackBus = Vec(config.nWide, Decoupled(new Bus(config)))
  })

  (0 until config.nWide).foreach(j => {
    io.writeBackBus(j).bits.tag  := io.in(j).bits.rd_tag
    io.writeBackBus(j).bits.data := io.in(j).bits.rd_data
    io.writeBackBus(j).valid     := io.in(j).valid
    io.in(j).ready               := io.writeBackBus(j).ready
  })
}
