package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCArbiter, DCDemux}

class ReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out        = Decoupled(new MI(config))

    val stall = Input(Bool())
  })

  val r1ValidNext = Wire(UInt(1.W))
  val r2ValidNext = Wire(UInt(1.W))
  val rowNext     = Wire(new MI(config))
  val row         = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), !io.stall)

  val busR1MatchesForward = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesForward = Wire(Vec(config.nWide, Bool()))
  val busR1MatchesWakeup  = Wire(Vec(config.nWide, Bool()))
  val busR2MatchesWakeup  = Wire(Vec(config.nWide, Bool()))

  val emptyNext      = Wire(Bool())
  val empty          = RegEnable(emptyNext, 1.B, !io.stall)
  val outReadyToFire = Wire(UInt(1.W))
  val outFiring      = Wire(UInt(1.W))

  outReadyToFire := row.rs1TagReady.asBool && row.rs2TagReady.asBool && !empty
  io.out.valid   := outReadyToFire
  outFiring      := outReadyToFire.asBool && io.out.ready
  io.in.ready    := outFiring | empty

  emptyNext := MuxCase(
    empty,
    Seq(
      io.in.valid                       -> 0.U,
      (outFiring.asBool & !io.in.valid) -> 1.U
    )
  )

  r1ValidNext := MuxCase(
    row.rs1TagReady,
    Seq(
      io.in.valid                                      -> io.in.bits.rs1TagReady,
      (empty.asBool & !io.in.valid)                    -> 0.U,
      (!empty.asBool & busR1MatchesForward.asUInt.orR) -> 1.U,
      (!empty.asBool & busR1MatchesWakeup.asUInt.orR)  -> 1.U
    )
  )

  r2ValidNext := MuxCase(
    row.rs2TagReady,
    Seq(
      io.in.valid                                      -> io.in.bits.rs2TagReady,
      (empty.asBool & !io.in.valid)                    -> 0.U,
      (!empty.asBool & busR2MatchesForward.asUInt.orR) -> 1.U,
      (!empty.asBool & busR2MatchesWakeup.asUInt.orR)  -> 1.U
    )
  )

  when(io.in.valid) {
    rowNext := io.in.bits
  }.otherwise {
    rowNext             := row
    rowNext.rs1TagReady := r1ValidNext
    rowNext.rs2TagReady := r2ValidNext
  }

  io.out.bits := row

  for (j <- 0 until config.nWide) {
    busR1MatchesForward(j) := io.forwardBus(j).valid & (row.rs1 === io.forwardBus(j).bits.tag)
    busR2MatchesForward(j) := io.forwardBus(j).valid & (row.rs2 === io.forwardBus(j).bits.tag)
    busR1MatchesWakeup(j)  := io.wakeupBus(j).valid & (row.rs1 === io.wakeupBus(j).bits.tag)
    busR2MatchesWakeup(j)  := io.wakeupBus(j).valid & (row.rs2 === io.wakeupBus(j).bits.tag)
  }
}

class ReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out        = Decoupled(new MI(config))

    val stall = Input(UInt(1.W))
  })

  val rows    = Seq.fill(config.rsDepth)(Module(new ReservationStationRow(config)))
  val arbiter = Module(new DCArbiter(new MI(config))(config.rsDepth, 1))
  val demux   = Module(new DCDemux(new MI(config))(1, config.rsDepth))

  val rowReady = Wire(Vec(config.rsDepth, Bool()))

  demux.io.in(0)  <> io.in
  demux.io.sel(0) := PriorityEncoder(rowReady)

  (0 until config.rsDepth).foreach(j => {
    rowReady(j) := rows(j).io.in.ready

    rows(j).io.stall      := io.stall
    rows(j).io.forwardBus <> io.forwardBus
    rows(j).io.wakeupBus  <> io.wakeupBus

    rows(j).io.in    <> demux.io.out(j)(0)
    arbiter.io.in(j) <> rows(j).io.out
  })

  arbiter.io.out(0) <> io.out
}
