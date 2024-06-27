package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

class WriteBackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val tagBuses = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  (0 until config.nWide).foreach(j => {
    io.tagBuses(j).bits.tag := io.in(j).bits.rd_tag
    io.tagBuses(j).valid    := io.in(j).valid
    io.in(j).ready          := io.tagBuses(j).ready
  })
}
