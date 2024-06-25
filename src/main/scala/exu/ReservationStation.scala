package wood.exu

import chisel3._
import chisel3.util._
import wood.exu.ExConfig
import wood.fru.MI
import wood.std.DCArbiter

class ReservationStationRow(val numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(new MI()))
    val tagBuses = Flipped(Vec(numPorts, Decoupled(new ForwardBus())))
    val out      = Decoupled(new MI())

    val r1Valid = Input(UInt(1.W))
    val r2Valid = Input(UInt(1.W))

    val stall = Input(UInt(1.W))
    val clear = Input(UInt(1.W))
    val we    = Input(UInt(1.W))

    val empty = Output(UInt(1.W))
  })

  val r1ValidNext  = Wire(UInt(1.W))
  val r2ValidNext  = Wire(UInt(1.W))
  val rowEmptyNext = Wire(UInt(1.W))

  val r1Valid  = RegEnable(r1ValidNext, 0.U, !io.stall)
  val r2Valid  = RegEnable(r2ValidNext, 0.U, !io.stall)
  val rowEmpty = RegEnable(rowEmptyNext, 0.U, !io.stall)
  val row      = RegEnable(io.in.bits, !io.stall)

  val busR1Matches = Wire(Vec(numPorts, Bool()))
  for (j <- 0 until numPorts) {
    busR1Matches(j) := io.tagBuses(j).valid & (row.rs1 === io.tagBuses(j).bits.tag)
  }

  val busR2Matches = Wire(Vec(numPorts, Bool()))
  for (j <- 0 until numPorts) {
    busR2Matches(j) := io.tagBuses(j).valid & (row.rs2 === io.tagBuses(j).bits.tag)
  }

  val updateRow = io.in.valid && io.we.asBool
  when(updateRow) {
    row := io.in.bits
  }

  r1ValidNext := MuxCase(
    r1Valid,
    Seq(
      io.clear.asBool         -> 0.U,
      busR1Matches.asUInt.orR -> 1.U,
      updateRow               -> io.r1Valid
    )
  )

  r2ValidNext := MuxCase(
    r2Valid,
    Seq(
      io.clear.asBool         -> 0.U,
      busR2Matches.asUInt.orR -> 1.U,
      updateRow               -> io.r2Valid
    )
  )

  rowEmptyNext := MuxCase(
    rowEmpty,
    Seq(
      io.clear.asBool -> 1.U,
      io.we.asBool    -> 0.U
    )
  )

  io.empty     := rowEmpty
  io.out.bits  := row
  io.out.valid := r1Valid & r2Valid

  // TODO
  io.in.ready := 1.U
  for (j <- 0 until numPorts) {
    io.tagBuses(j).ready := 1.U
  }
}

class ReservationStation(val numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(new MI()))
    val tagBuses = Flipped(Vec(numPorts, Decoupled(new ForwardBus())))
    val out      = Decoupled(new MI())

    val r1Valid = Input(UInt(1.W))
    val r2Valid = Input(UInt(1.W))

    val stall = Input(UInt(1.W))
  })

  val rows = Seq.fill(ExConfig.rsDepth)(Module(new ReservationStationRow(numPorts)))

  val rowTagReady  = Wire(Vec(ExConfig.rsDepth, UInt(1.W)))
  val rowFull      = Wire(Vec(ExConfig.rsDepth, UInt(1.W)))
  val rowEmpty     = Wire(Vec(ExConfig.rsDepth, UInt(1.W)))
  val rowValid     = Wire(Vec(ExConfig.rsDepth, UInt(1.W)))
  val rowScheduled = Wire(Vec(ExConfig.rsDepth, UInt(1.W)))
  (0 until ExConfig.rsDepth).foreach(j => {
    rowValid(j)     := rows(j).io.out.valid
    rowEmpty(j)     := rows(j).io.empty
    rowFull(j)      := !rows(j).io.empty
    rowScheduled(j) := rows(j).io.out.ready & rows(j).io.out.valid
    rowTagReady(j)  := rows(j).io.tagBuses.asUInt.andR
  })

  val rowEmptyIndex     = PriorityEncoder(rowEmpty.asUInt)
  val rowScheduledIndex = PriorityEncoder(rowScheduled.asUInt)
  val rowWe             = UIntToOH(rowEmptyIndex)
  (0 until ExConfig.rsDepth).foreach(j => {
    rows(j).io.we    := rowWe(j)
    rows(j).io.clear := rowScheduled(j)
    rows(j).io.stall := io.stall
  })

  (0 until ExConfig.rsDepth).foreach(j => {
    rows(j).io.in       <> io.in
    rows(j).io.r1Valid  <> io.r1Valid
    rows(j).io.r2Valid  <> io.r2Valid
    rows(j).io.tagBuses <> io.tagBuses
  })

  val arbiter = Module(new DCArbiter(new MI())(ExConfig.rsDepth, 1))

  (0 until ExConfig.rsDepth).foreach(j => {
    arbiter.io.in(j) <> rows(j).io.out
    (0 until numPorts).foreach(k => {
      rows(j).io.tagBuses(k).bits  := io.tagBuses(k).bits
      rows(j).io.tagBuses(k).valid := io.tagBuses(k).valid
      io.tagBuses(k).ready         := rowTagReady.asUInt.andR
    })
  })

  arbiter.io.out(0) <> io.out
}
