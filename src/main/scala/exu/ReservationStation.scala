package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.DCArbiter

class ReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(new MI(config)))
    val tagBuses = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val out      = Decoupled(new MI(config))

    val stall = Input(UInt(1.W))
    val clear = Input(UInt(1.W))
    val we    = Input(UInt(1.W))

    val empty = Output(UInt(1.W))
  })

  val r1ValidNext  = Wire(UInt(1.W))
  val r2ValidNext  = Wire(UInt(1.W))
  val rowEmptyNext = Wire(UInt(1.W))

  val rowNext = Wire(new MI(config))

  val rowEmpty = RegEnable(rowEmptyNext, 0.U, !io.stall)
  val row      = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), !io.stall)

  val busR1Matches = Wire(Vec(config.nWide, Bool()))
  for (j <- 0 until config.nWide) {
    busR1Matches(j) := io.tagBuses(j).valid & (row.rs1 === io.tagBuses(j).bits.tag)
  }

  val busR2Matches = Wire(Vec(config.nWide, Bool()))
  for (j <- 0 until config.nWide) {
    busR2Matches(j) := io.tagBuses(j).valid & (row.rs2 === io.tagBuses(j).bits.tag)
  }

  val updateRow = io.in.valid && io.we.asBool
  when(updateRow) {
    row := io.in.bits
  }

  r1ValidNext := MuxCase(
    row.rs1_tag_valid,
    Seq(
      io.clear.asBool         -> 0.U,
      busR1Matches.asUInt.orR -> 1.U,
      updateRow               -> io.in.bits.rs1_tag_valid
    )
  )

  r2ValidNext := MuxCase(
    row.rs2_tag_valid,
    Seq(
      io.clear.asBool         -> 0.U,
      busR2Matches.asUInt.orR -> 1.U,
      updateRow               -> io.in.bits.rs2_tag_valid
    )
  )

  rowEmptyNext := MuxCase(
    rowEmpty,
    Seq(
      io.clear.asBool -> 1.U,
      io.we.asBool    -> 0.U
    )
  )

  rowNext               := row
  rowNext.rs1_tag_valid := r1ValidNext
  rowNext.rs2_tag_valid := r2ValidNext

  io.empty     := rowEmpty | rowEmptyNext
  io.out.bits  := row
  io.out.valid := row.rs1_tag_valid & row.rs2_tag_valid

  // TODO
  io.in.ready := 1.U
  for (j <- 0 until config.nWide) {
    io.tagBuses(j).ready := 1.U
  }
}

class ReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(new MI(config)))
    val tagBuses = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val out      = Decoupled(new MI(config))

    val r1Valid = Input(UInt(1.W))
    val r2Valid = Input(UInt(1.W))

    val stall = Input(UInt(1.W))
  })

  val rows = Seq.fill(config.rsDepth)(Module(new ReservationStationRow(config)))

  val rowTagReady  = Wire(Vec(config.rsDepth, UInt(1.W)))
  val rowFull      = Wire(Vec(config.rsDepth, UInt(1.W)))
  val rowEmpty     = Wire(Vec(config.rsDepth, UInt(1.W)))
  val rowValid     = Wire(Vec(config.rsDepth, UInt(1.W)))
  val rowScheduled = Wire(Vec(config.rsDepth, UInt(1.W)))
  (0 until config.rsDepth).foreach(j => {
    rowValid(j)     := rows(j).io.out.valid
    rowEmpty(j)     := rows(j).io.empty
    rowFull(j)      := !rows(j).io.empty
    rowScheduled(j) := rows(j).io.out.ready & rows(j).io.out.valid
    rowTagReady(j)  := rows(j).io.tagBuses.asUInt.andR
  })

  val rowEmptyIndex     = PriorityEncoder(rowEmpty.asUInt)
  val rowScheduledIndex = PriorityEncoder(rowScheduled.asUInt)
  val rowWe             = UIntToOH(rowEmptyIndex)
  (0 until config.rsDepth).foreach(j => {
    rows(j).io.we    := rowWe(j)
    rows(j).io.clear := rowScheduled(j)
    rows(j).io.stall := io.stall
  })

  (0 until config.rsDepth).foreach(j => {
    rows(j).io.in       <> io.in
    rows(j).io.tagBuses <> io.tagBuses
  })

  val arbiter = Module(new DCArbiter(new MI(config))(config.rsDepth, 1))

  (0 until config.rsDepth).foreach(j => {
    arbiter.io.in(j) <> rows(j).io.out
    (0 until config.nWide).foreach(k => {
      rows(j).io.tagBuses(k).bits  := io.tagBuses(k).bits
      rows(j).io.tagBuses(k).valid := io.tagBuses(k).valid
      io.tagBuses(k).ready         := rowTagReady.asUInt.andR
    })
  })

  arbiter.io.out(0) <> io.out
}
