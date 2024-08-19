package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class LSU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })
  io.in <> io.out
}
