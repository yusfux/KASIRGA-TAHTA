package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class ScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val tagBuses   = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val retiredBus = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val stall      = Input(UInt(1.W))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val reservationStations = Seq.tabulate(config.nWide) { j =>
    Module(new ReservationStation(config))
  }

  val validList = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide * 2)
    )
  )

  (0 until config.nWide).foreach(j => {
    reservationStations(j).io.in                 <> io.in(j)
    validList.io.rip(j).bits.addr                := io.in(j).bits.rs1_tag
    validList.io.rip(j).valid                    := io.in(j).valid
    validList.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2_tag
    validList.io.rip(j + config.nWide).valid     := io.in(j).valid

    reservationStations(j).io.r1Valid        := validList.io.rop(j).bits.data
    reservationStations(j).io.r2Valid        := validList.io.rop(j + config.nWide).bits.data
    validList.io.rop(j).ready                := 1.U // TODO
    validList.io.rop(j + config.nWide).ready := 1.U // TODO

    validList.io.wp(j).bits.addr   := io.tagBuses(j).bits.tag
    validList.io.wp(j).bits.enable := io.tagBuses(j).valid
    validList.io.wp(j).bits.data   := 1.U
    validList.io.wp(j).valid       := io.tagBuses(j).valid
    io.tagBuses(j).ready           := validList.io.wp(j).ready

    validList.io.wp(j + config.nWide).bits.addr   := io.retiredBus(j).bits.tag
    validList.io.wp(j + config.nWide).bits.enable := io.retiredBus(j).valid
    validList.io.wp(j + config.nWide).bits.data   := 0.U
    validList.io.wp(j + config.nWide).valid       := io.retiredBus(j).valid
    io.retiredBus(j).ready                        := validList.io.wp(j + config.nWide).ready

    reservationStations(j).io.stall := io.stall

    reservationStations(j).io.tagBuses <> io.tagBuses

    io.out(j).bits  := RegEnable(reservationStations(j).io.out.bits, 0.U.asTypeOf(new MI(config)), io.stall.asBool)
    io.out(j).valid := RegEnable(reservationStations(j).io.out.valid, 1.B, io.stall.asBool)

    reservationStations(j).io.out.ready := io.out(j).ready
  })
}
