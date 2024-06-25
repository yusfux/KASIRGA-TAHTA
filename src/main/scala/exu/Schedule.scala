package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class ScheduleStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(numPorts, Decoupled(new MI())))
    val tagBuses   = Flipped(Vec(numPorts, Decoupled(new ForwardBus())))
    val retiredBus = Flipped(Vec(numPorts, Decoupled(new Tag())))
    val stall      = Input(UInt(1.W))

    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val reservationStations = Seq.tabulate(numPorts) { j =>
    Module(new ReservationStation(numPorts))
  }

  val validList = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(ExConfig.prfDepth, numPorts * 2, numPorts * 2)
    )
  )

  (0 until numPorts).foreach(j => {
    reservationStations(j).io.in             <> io.in(j)
    validList.io.rip(j).bits.addr            := io.in(j).bits.rs1_tag
    validList.io.rip(j).valid                := io.in(j).valid
    validList.io.rip(j + numPorts).bits.addr := io.in(j).bits.rs2_tag
    validList.io.rip(j + numPorts).valid     := io.in(j).valid

    reservationStations(j).io.r1Valid    := validList.io.rop(j).bits.data
    reservationStations(j).io.r2Valid    := validList.io.rop(j + numPorts).bits.data
    validList.io.rop(j).ready            := 1.U // TODO
    validList.io.rop(j + numPorts).ready := 1.U // TODO

    validList.io.wp(j).bits.addr   := io.tagBuses(j).bits.tag
    validList.io.wp(j).bits.enable := io.tagBuses(j).valid
    validList.io.wp(j).bits.data   := 1.U
    validList.io.wp(j).valid       := io.tagBuses(j).valid
    io.tagBuses(j).ready           := validList.io.wp(j).ready

    validList.io.wp(j + numPorts).bits.addr   := io.retiredBus(j).bits.tag
    validList.io.wp(j + numPorts).bits.enable := io.retiredBus(j).valid
    validList.io.wp(j + numPorts).bits.data   := 0.U
    validList.io.wp(j + numPorts).valid       := io.retiredBus(j).valid
    io.retiredBus(j).ready                    := validList.io.wp(j + numPorts).ready

    reservationStations(j).io.stall := io.stall
    reservationStations(j).io.out   <> io.out(j)

    reservationStations(j).io.tagBuses <> io.tagBuses
  })
}
