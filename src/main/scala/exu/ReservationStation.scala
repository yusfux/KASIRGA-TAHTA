package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCDemux, DCRRArbiter}

class ReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Decoupled(new MI(config)))
    val forwardBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus   = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val lsWakeupBus = Input(Vec(1, ValidIO(new TagBus(config))))
    val out         = Decoupled(new MI(config))
  })

  val busR1MatchesForward  = Wire(Vec(config.nWide, Bool()))
  val busR1MatchesWakeup   = Wire(Vec(config.nWide, Bool()))
  val busR1MatchesLSWakeup = Wire(Vec(1, Bool()))

  val busR2MatchesForward  = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesWakeup   = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesLSWakeup = Wire(Vec(1, Bool()))

  val rowNext   = Wire(new MI(config))
  val emptyNext = Wire(Bool())

  val writeToRow = (io.in.valid & io.in.ready)

  val row   = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), 1.B)
  val empty = RegEnable(emptyNext, 1.B, 1.B)

  val sOnlyRead     = io.out.fire
  val sOnlyWrite    = io.in.fire
  val sReadAndWrite = io.in.fire & io.out.fire

  io.in.ready  := empty | (!empty & io.out.fire)
  io.out.valid := !empty & (row.rs1TagReady & row.rs2TagReady)

  when(io.in.fire & io.out.fire) {
    emptyNext           := 0.U
    rowNext             := io.in.bits
    rowNext.rs1TagReady := io.in.bits.rs1TagReady
    rowNext.rs2TagReady := io.in.bits.rs2TagReady
  }.elsewhen(io.in.fire) {
    emptyNext           := 0.U
    rowNext             := io.in.bits
    rowNext.rs1TagReady := io.in.bits.rs1TagReady
    rowNext.rs2TagReady := io.in.bits.rs2TagReady
  }.elsewhen(io.out.fire) {
    emptyNext := 1.U
    rowNext   := 0.U.asTypeOf(new MI(config))
  }.otherwise {
    emptyNext := empty
    rowNext   := row
    rowNext.rs1TagReady := row.rs1TagReady | MuxCase(
      0.U,
      Seq(
        (!empty.asBool & busR1MatchesForward.asUInt.orR)  -> 1.U,
        (!empty.asBool & busR1MatchesWakeup.asUInt.orR)   -> 1.U,
        (!empty.asBool & busR1MatchesLSWakeup.asUInt.orR) -> 1.U
      )
    )
    row.rs2TagReady := row.rs2TagReady | MuxCase(
      0.U,
      Seq(
        (!empty.asBool & busR2MatchesForward.asUInt.orR)  -> 1.U,
        (!empty.asBool & busR2MatchesWakeup.asUInt.orR)   -> 1.U,
        (!empty.asBool & busR2MatchesLSWakeup.asUInt.orR) -> 1.U
      )
    )
  }

  io.out.bits := row

  for (j <- 0 until config.nWide) {
    busR1MatchesForward(j) := io.forwardBus(j).valid & (row.rs1Tag === io.forwardBus(j).bits.tag)
    busR2MatchesForward(j) := io.forwardBus(j).valid & (row.rs2Tag === io.forwardBus(j).bits.tag)
    busR1MatchesWakeup(j)  := io.wakeupBus(j).valid & (row.rs1Tag === io.wakeupBus(j).bits.tag)
    busR2MatchesWakeup(j)  := io.wakeupBus(j).valid & (row.rs2Tag === io.wakeupBus(j).bits.tag)
  }

  busR1MatchesLSWakeup(0) := io.lsWakeupBus(0).valid & (row.rs1Tag === io.lsWakeupBus(0).bits.tag)
  busR2MatchesLSWakeup(0) := io.lsWakeupBus(0).valid & (row.rs2Tag === io.lsWakeupBus(0).bits.tag)
}

class ReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Decoupled(new MI(config)))
    val flush       = Input(Bool())
    val forwardBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val lsWakeupBus = Input(Vec(1, ValidIO(new TagBus(config))))
    val wakeupBus   = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out         = Decoupled(new MI(config))
  })

  val rows    = Seq.fill(config.rsDepth)(Module(new ReservationStationRow(config)))
  val arbiter = Module(new DCRRArbiter(new MI(config), config.rsDepth))
  val demux   = Module(new DCDemux(new MI(config))(1, config.rsDepth))

  val rowReady = Wire(Vec(config.rsDepth, Bool()))

  demux.io.in(0)  <> io.in
  demux.io.sel(0) := PriorityEncoder(rowReady)

  (0 until config.rsDepth).foreach(j => {
    rows(j).reset := (this.reset.asBool | io.flush).asBool

    rowReady(j) := rows(j).io.in.ready

    rows(j).io.forwardBus  <> io.forwardBus
    rows(j).io.wakeupBus   <> io.wakeupBus
    rows(j).io.lsWakeupBus <> io.lsWakeupBus

    rows(j).io.in    <> demux.io.out(j)(0)
    arbiter.io.in(j) <> rows(j).io.out
  })

  arbiter.io.out <> io.out
}
