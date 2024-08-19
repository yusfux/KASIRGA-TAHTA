package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.lsu.LSBus

class WritebackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Vec(config.nWide, ValidIO(new DataBus(config)))
    val lsBus        = Vec(config.nWide, ValidIO(new LSBus(config)))
    val exceptionBus = Vec(config.nWide, ValidIO(new ExceptionBus(config)))
  })

  (0 until config.nWide).foreach(j => {
    io.exceptionBus(j).bits.tag       := io.in(j).bits.rdTag
    io.exceptionBus(j).bits.pc        := io.in(j).bits.targetPC
    io.exceptionBus(j).bits.exception := io.in(j).bits.exception
    io.exceptionBus(j).bits.taken     := io.in(j).bits.taken
    io.exceptionBus(j).valid          := io.in(j).valid

    io.writebackBus(j).bits.tag  := io.in(j).bits.rdTag
    io.writebackBus(j).bits.data := io.in(j).bits.rdData
    io.writebackBus(j).valid     := io.in(j).valid
    io.in(j).ready               := 1.U // This stage is always ready

    io.lsBus(j).bits.addr := io.in(j).bits.rdData
    io.lsBus(j).bits.data := io.in(j).bits.rs2Data
    io.lsBus(j).bits.tag  := io.in(j).bits.rdTag
    io.lsBus(j).bits.inst := io.in(j).bits.inst // debug only
    io.lsBus(j).bits.pc   := io.in(j).bits.pc // debug only
    io.lsBus(j).valid     := io.in(j).valid
  })
}
