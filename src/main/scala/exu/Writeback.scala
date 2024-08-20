package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.lsu.LSBus

class WritebackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Vec(config.nWide, ValidIO(new DataBus(config)))
    val lsOperandBus = Vec(config.nWide, ValidIO(new LSBus(config)))
    val exceptionBus = Vec(config.nWide, ValidIO(new ExceptionBus(config)))
  })

  (0 until config.nWide).foreach(j => {
    val isStore = (io.in(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_TYPE_S, 2).U)
    val isLoad  = (io.in(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_TYPE_L, 2).U)

    io.exceptionBus(j).bits.tag       := io.in(j).bits.rdTag
    io.exceptionBus(j).bits.pc        := io.in(j).bits.targetPC
    io.exceptionBus(j).bits.exception := io.in(j).bits.exception
    io.exceptionBus(j).bits.taken     := io.in(j).bits.taken
    io.exceptionBus(j).valid          := io.in(j).valid && !isLoad

    io.writebackBus(j).bits.tag  := io.in(j).bits.rdTag
    io.writebackBus(j).bits.data := io.in(j).bits.rdData
    io.writebackBus(j).valid     := io.in(j).valid && !isLoad
    io.in(j).ready               := 1.U // This stage is always ready

    io.lsOperandBus(j).bits.targetAddr := io.in(j).bits.rdData
    io.lsOperandBus(j).bits.rs2Data    := io.in(j).bits.rs2Data
    io.lsOperandBus(j).bits.rdTag      := io.in(j).bits.rdTag
    io.lsOperandBus(j).bits.inst       := io.in(j).bits.inst // debug only
    io.lsOperandBus(j).bits.pc         := io.in(j).bits.pc // debug only
    io.lsOperandBus(j).valid           := io.in(j).valid && (isLoad | isStore)
  })
}
