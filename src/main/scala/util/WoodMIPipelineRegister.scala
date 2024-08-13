package wood.util

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.MI

class WoodMIPipelineRegister(config: WoodConfig, numValids: Int) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val flush      = Input(Bool())
    val setflushed = Input(Bool())
    val valids     = Input(Vec(numValids, Bool()))
    val out        = Decoupled(new MI(config))
  })

  require(numValids >= 1, "Stage must have one or more inputs.")

  val regDataNext  = Wire(new MI(config))
  val regValidNext = Wire(Bool())
  val regData      = RegEnable(regDataNext, 0.U.asTypeOf(new MI(config)), 1.B)
  val regValid     = RegEnable(regValidNext, 0.B, 1.B)

  when(io.flush) {
    regData  := 0.U.asTypeOf(new MI(config))
    regValid := 0.B
  }
  when(io.setflushed) {
    regData  := 0.U.asTypeOf(new MI(config))
    regValid := 0.B
  }

  when(io.flush) {
    regDataNext  := 0.U.asTypeOf(new MI(config))
    regValidNext := 0.U
  }.elsewhen(io.setflushed) {
    regDataNext         := regData
    regDataNext.flushed := 1.B
    regValidNext        := regValid
  }.elsewhen(io.out.ready) {
    regDataNext  := io.in.bits
    regValidNext := io.in.valid
  }.otherwise {
    regDataNext  := regData
    regValidNext := regValid
  }

  io.out.bits  := regData
  io.out.valid := regValid
  io.in.ready  := (!io.valids.asUInt.orR) | ((io.in.valid & io.out.ready) & io.valids.asUInt.andR)
}
