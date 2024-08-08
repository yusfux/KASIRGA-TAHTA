package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig

case class PCInst(config: WoodConfig) extends Bundle {
  val pc   = UInt(config.pcWidth.W)
  val inst = UInt(32.W) // TODO
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val pc  = Flipped(Vec(config.nWide, Decoupled(UInt(config.pcWidth.W))))
    val out = Vec(config.nWide, Decoupled(new PCInst(config)))
  })
  // TODO
}
