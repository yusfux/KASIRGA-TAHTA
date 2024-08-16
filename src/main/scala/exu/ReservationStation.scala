package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCArbiter, DCDemux}

class ReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out        = Decoupled(new MI(config))

  })

  val busR1MatchesForward = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesForward = Wire(Vec(config.nWide, Bool()))
  val busR1MatchesWakeup  = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesWakeup  = Wire(Vec(config.nWide, Bool()))

  val rs1TagReadyNext = Wire(UInt(1.W))
  val rs2TagReadyNext = Wire(UInt(1.W))
  val rowNext         = Wire(new MI(config))
  val emptyNext       = Wire(Bool())

  val writeToRow = (io.in.valid & io.in.ready)

  val row         = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), 1.B)
  val rs1TagReady = RegEnable(rs1TagReadyNext, 0.U, 1.B)
  val rs2TagReady = RegEnable(rs2TagReadyNext, 0.U, 1.B)
  val empty       = RegEnable(emptyNext, 1.B, 1.B)

  val sOnlyRead     = io.out.fire
  val sOnlyWrite    = io.in.fire
  val sReadAndWrite = io.in.fire & io.out.fire

  io.in.ready  := empty | (!empty & io.out.fire)
  io.out.valid := !empty & (rs1TagReady & rs2TagReady)

  when(io.in.fire & io.out.fire) {
    emptyNext       := 0.U
    rowNext         := io.in.bits
    rs1TagReadyNext := io.in.bits.rs1TagReady
    rs2TagReadyNext := io.in.bits.rs2TagReady
  }.elsewhen(io.in.fire) {
    emptyNext       := 0.U
    rowNext         := io.in.bits
    rs1TagReadyNext := io.in.bits.rs1TagReady
    rs2TagReadyNext := io.in.bits.rs2TagReady
  }.elsewhen(io.out.fire) {
    emptyNext       := 1.U
    rowNext         := 0.U.asTypeOf(new MI(config))
    rs1TagReadyNext := 0.U
    rs2TagReadyNext := 0.U
  }.otherwise {
    emptyNext := empty
    rowNext   := row
    rs1TagReadyNext := rs1TagReady | MuxCase(
      0.U,
      Seq(
        (!empty.asBool & busR1MatchesForward.asUInt.orR) -> 1.U,
        (!empty.asBool & busR1MatchesWakeup.asUInt.orR)  -> 1.U
      )
    )
    rs2TagReadyNext := rs2TagReady | MuxCase(
      0.U,
      Seq(
        (!empty.asBool & busR2MatchesForward.asUInt.orR) -> 1.U,
        (!empty.asBool & busR2MatchesWakeup.asUInt.orR)  -> 1.U
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
}

class ReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val flush      = Input(Bool())
    val forwardBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out        = Decoupled(new MI(config))
  })

  val rows    = Seq.fill(config.rsDepth)(Module(new ReservationStationRow(config)))
  val arbiter = Module(new DCArbiter(new MI(config))(config.rsDepth, 1))
  val demux   = Module(new DCDemux(new MI(config))(1, config.rsDepth))

  val rowReady = Wire(Vec(config.rsDepth, Bool()))

  demux.io.in(0)  <> io.in
  demux.io.sel(0) := PriorityEncoder(rowReady)

  (0 until config.rsDepth).foreach(j => {
    rows(j).reset := (this.reset.asBool | io.flush).asBool

    rowReady(j) := rows(j).io.in.ready

    rows(j).io.forwardBus <> io.forwardBus
    rows(j).io.wakeupBus  <> io.wakeupBus

    rows(j).io.in    <> demux.io.out(j)(0)
    arbiter.io.in(j) <> rows(j).io.out
  })

  arbiter.io.out(0) <> io.out
}
