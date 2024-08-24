package wood.util

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.lsu.LSCMI

class WoodLSCMIPipelineRegister(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Decoupled(new LSCMI(config)))
    val setflushed   = Input(Bool())
    val frontRetired = Input(Bool()) // retired status of the next stage
    val out          = Decoupled(new LSCMI(config))
  })

  val regDataNextInvalidRetired = Wire(new LSCMI(config))

  val regDataNext  = Wire(new LSCMI(config))
  val regValidNext = Wire(Bool())
  val regData      = RegEnable(regDataNext, 0.U.asTypeOf(new LSCMI(config)), 1.B)
  val regValid     = RegEnable(regValidNext, 0.B, 1.B)

  regDataNextInvalidRetired := MuxCase(
    regData,
    Seq(
      (io.setflushed & io.out.ready & io.in.bits.retired)  -> io.in.bits, // ignore flush
      (io.setflushed & io.out.ready & !io.in.bits.retired) -> io.in.bits, // but set flushed=1
      (io.setflushed & !io.out.ready & io.frontRetired)    -> regData, // ignore flush but save the retired
      (io.setflushed & !io.out.ready & !io.frontRetired)   -> regData, // but set flushed=1
      (!io.setflushed & io.out.ready)                      -> io.in.bits,
      (!io.setflushed & !io.out.ready)                     -> regData // save the retired
    )
  )
  regDataNext := regDataNextInvalidRetired
  regDataNext.flushed := MuxCase(
    regDataNextInvalidRetired.flushed,
    Seq(
      (io.setflushed & io.out.ready & !io.in.bits.retired) -> 1.B, // but set flushed=1
      (io.setflushed & !io.out.ready & !io.frontRetired)   -> 1.B // but set flushed=1
    )
  )
  regDataNext.retired := MuxCase(
    regDataNextInvalidRetired.retired,
    Seq(
      (io.setflushed & !io.out.ready & io.frontRetired) -> (regData.retired | io.frontRetired),
      (!io.setflushed & !io.out.ready)                  -> (regData.retired | io.frontRetired)
    )
  )

  regValidNext := MuxCase(
    regValid,
    Seq(
      (io.setflushed & io.out.ready & io.in.bits.retired)  -> io.in.valid, // ignore flush
      (io.setflushed & io.out.ready & !io.in.bits.retired) -> 0.B,
      (io.setflushed & !io.out.ready & io.frontRetired)    -> regValid, // ignore flush
      (io.setflushed & !io.out.ready & !io.frontRetired)   -> 0.B,
      (!io.setflushed & io.out.ready)                      -> io.in.valid,
      (!io.setflushed & !io.out.ready)                     -> regValid
    )
  )

  io.out.bits  := regData
  io.out.valid := regValid
  io.in.ready  := (!io.in.valid) | (io.in.valid & io.out.ready)
}
