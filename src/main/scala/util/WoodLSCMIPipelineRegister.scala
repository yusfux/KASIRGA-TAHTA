package wood.util

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.lsu.LSCMI

class WoodLSCMIPipelineRegister(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Decoupled(new LSCMI(config)))
    val flush        = Input(Bool())
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
      (io.flush & io.out.ready & io.in.bits.retired)  -> io.in.bits, // ignore flush
      (io.flush & io.out.ready & !io.in.bits.retired) -> 0.U.asTypeOf(new LSCMI(config)),
      (io.flush & !io.out.ready & io.frontRetired)    -> regData, // ignore flush but save the retired
      (io.flush & !io.out.ready & !io.frontRetired)   -> 0.U.asTypeOf(new LSCMI(config)),
      (!io.flush & io.out.ready)                      -> io.in.bits,
      (!io.flush & !io.out.ready)                     -> regData // save the retired
    )
  )
  regDataNext := regDataNextInvalidRetired
  regDataNext.retired := MuxCase(
    regDataNextInvalidRetired.retired,
    Seq(
      (io.flush & !io.out.ready & io.frontRetired) -> (regData.retired | io.frontRetired),
      (!io.flush & !io.out.ready)                  -> (regData.retired | io.frontRetired)
    )
  )

  regValidNext := MuxCase(
    regValid,
    Seq(
      (io.flush & io.out.ready & io.in.bits.retired)  -> io.in.valid, // ignore flush
      (io.flush & io.out.ready & !io.in.bits.retired) -> 0.B,
      (io.flush & !io.out.ready & io.frontRetired)    -> regValid, // ignore flush
      (io.flush & !io.out.ready & !io.frontRetired)   -> 0.B,
      (!io.flush & io.out.ready)                      -> io.in.valid,
      (!io.flush & !io.out.ready)                     -> regValid
    )
  )

  io.out.bits  := regData
  io.out.valid := regValid
  io.in.ready  := (!io.in.valid) | (io.in.valid & io.out.ready)
}
