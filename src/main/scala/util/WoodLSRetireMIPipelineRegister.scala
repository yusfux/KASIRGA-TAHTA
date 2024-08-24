package wood.util

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.MI
import wood.lsu.LSOperandBus

class WoodLSRetireMIPipelineRegister(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Decoupled(new MI(config)))
    val setflushed   = Input(Bool())
    val frontRetired = Input(Bool()) // retired status of the next stage
    val lsOperandBus = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val out          = Decoupled(new MI(config))
  })

  val operandBusMatches          = Wire(Vec(config.nWide, Bool()))
  val regDataNextInvalidForwards = Wire(new MI(config))

  val regDataNext  = Wire(new MI(config))
  val regValidNext = Wire(Bool())
  val regData      = RegEnable(regDataNext, 0.U.asTypeOf(new MI(config)), 1.B)
  val regValid     = RegEnable(regValidNext, 0.B, 1.B)

  val operandBusMatchIndex = PriorityEncoder(operandBusMatches)
  val operandTargetAddr    = io.lsOperandBus(operandBusMatchIndex).bits.targetAddr
  val operandRs2           = io.lsOperandBus(operandBusMatchIndex).bits.rs2Data

  // debug only
  dontTouch(operandBusMatches)
  dontTouch(operandBusMatchIndex)
  dontTouch(regData.rdData)

  regDataNextInvalidForwards := MuxCase(
    regData,
    Seq(
      (io.setflushed & io.out.ready & io.in.bits.retired)  -> io.in.bits, // ignore flush
      (io.setflushed & io.out.ready & !io.in.bits.retired) -> io.in.bits, // but set flushed=1
      (io.setflushed & !io.out.ready & io.frontRetired)    -> regData, // ignore flush but save operands and the retired
      (io.setflushed & !io.out.ready & !io.frontRetired)   -> regData, // but set flushed=1
      (!io.setflushed & io.out.ready)                      -> io.in.bits,
      (!io.setflushed & !io.out.ready)                     -> regData //  save operands and the retired
    )
  )
  regDataNext := regDataNextInvalidForwards
  regDataNext.flushed := MuxCase(
    regDataNextInvalidForwards.flushed,
    Seq(
      (io.setflushed & io.out.ready & !io.in.bits.retired) -> 1.B, // but set flushed=1
      (io.setflushed & !io.out.ready & !io.frontRetired)   -> 1.B // but set flushed=1
    )
  )
  regDataNext.retired := MuxCase(
    regDataNextInvalidForwards.retired,
    Seq(
      (io.setflushed & !io.out.ready & io.frontRetired) -> (regData.retired | io.frontRetired),
      (!io.setflushed & !io.out.ready)                  -> (regData.retired | io.frontRetired)
    )
  )
  regDataNext.rdData := MuxCase(
    regDataNextInvalidForwards.rdData,
    Seq(
      (io.setflushed & !io.out.ready & io.frontRetired) -> Mux(operandBusMatches.asUInt.orR, operandTargetAddr, regData.rdData),
      (!io.setflushed & !io.out.ready)                  -> Mux(operandBusMatches.asUInt.orR, operandTargetAddr, regData.rdData)
    )
  )
  regDataNext.rs2Data := MuxCase(
    regDataNextInvalidForwards.rs2Data,
    Seq(
      (io.setflushed & !io.out.ready & io.frontRetired) -> Mux(operandBusMatches.asUInt.orR, operandRs2, regData.rs2Data),
      (!io.setflushed & !io.out.ready)                  -> Mux(operandBusMatches.asUInt.orR, operandRs2, regData.rs2Data)
    )
  )
  regDataNext.operandReady := MuxCase(
    regDataNextInvalidForwards.operandReady,
    Seq(
      (io.setflushed & !io.out.ready & io.frontRetired) -> (regData.operandReady | operandBusMatches.asUInt.orR),
      (!io.setflushed & !io.out.ready)                  -> (regData.operandReady | operandBusMatches.asUInt.orR)
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

  for (j <- 0 until config.nWide) {
    operandBusMatches(j) := io.lsOperandBus(j).valid & (regData.rdTag === io.lsOperandBus(j).bits.rdTag)
  }

  io.out.bits  := regData
  io.out.valid := regValid
  io.in.ready  := (!io.in.valid) | (io.in.valid & io.out.ready)
}
