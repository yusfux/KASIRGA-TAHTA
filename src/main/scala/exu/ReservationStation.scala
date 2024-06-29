package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCArbiter, DCDemux}

class ReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out        = Decoupled(new MI(config))

    val stall = Input(Bool())
  })

  val r1ValidNext = Wire(UInt(1.W))
  val r2ValidNext = Wire(UInt(1.W))
  val rowNext     = Wire(new MI(config))
  val row         = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), !io.stall)

  val busR1Matches = Wire(Vec(config.nWide, Bool()))
  val busR2Matches = Wire(Vec(config.nWide, Bool()))

  val emptyNext      = Wire(Bool())
  val empty          = RegEnable(emptyNext, 1.B, !io.stall)
  val outReadyToFire = Wire(UInt(1.W))
  val outFiring      = Wire(UInt(1.W))

  outReadyToFire := row.rs1_tag_valid.asBool && row.rs2_tag_valid.asBool && !empty
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
    row.rs1_tag_valid,
    Seq(
      io.in.valid                               -> io.in.bits.rs1_tag_valid,
      (empty.asBool & !io.in.valid)             -> 0.U,
      (!empty.asBool & busR1Matches.asUInt.orR) -> 1.U
    )
  )

  r2ValidNext := MuxCase(
    row.rs2_tag_valid,
    Seq(
      io.in.valid                               -> io.in.bits.rs2_tag_valid,
      (empty.asBool & !io.in.valid)             -> 0.U,
      (!empty.asBool & busR2Matches.asUInt.orR) -> 1.U
    )
  )

  when(io.in.valid) {
    rowNext := io.in.bits
  }.otherwise {
    rowNext               := row
    rowNext.rs1_tag_valid := r1ValidNext
    rowNext.rs2_tag_valid := r2ValidNext
  }

  io.out.bits := row

  for (j <- 0 until config.nWide) {
    busR1Matches(j)        := io.forwardBus(j).valid & (row.rs1 === io.forwardBus(j).bits.tag)
    busR2Matches(j)        := io.forwardBus(j).valid & (row.rs2 === io.forwardBus(j).bits.tag)
    io.forwardBus(j).ready := 1.U // TODO
  }
}

class ReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out        = Decoupled(new MI(config))

    val stall = Input(UInt(1.W))
  })

  val rows    = Seq.fill(config.rsDepth)(Module(new ReservationStationRow(config)))
  val arbiter = Module(new DCArbiter(new MI(config))(config.rsDepth, 1))
  val demux   = Module(new DCDemux(new MI(config))(1, config.rsDepth))

  val rowReady       = Wire(Vec(config.rsDepth, Bool()))
  val rowTagBusReady = Wire(Vec(config.rsDepth, Bool()))

  demux.io.in(0)  <> io.in
  demux.io.sel(0) := PriorityEncoder(rowReady)

  (0 until config.rsDepth).foreach(j => {
    rowReady(j)       := rows(j).io.in.ready
    rowTagBusReady(j) := rows(j).io.forwardBus.asUInt.andR

    rows(j).io.stall      := io.stall
    rows(j).io.forwardBus <> io.forwardBus

    arbiter.io.in(j)   <> rows(j).io.out
    demux.io.out(j)(0) <> rows(j).io.in

    (0 until config.nWide).foreach(k => {
      rows(j).io.forwardBus(k).bits  := io.forwardBus(k).bits
      rows(j).io.forwardBus(k).valid := io.forwardBus(k).valid
      io.forwardBus(k).ready         := rowTagBusReady.asUInt.andR
    })
  })

  arbiter.io.out(0) <> io.out
}
