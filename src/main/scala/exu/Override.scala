package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.DCBus

class OverrideFromBus(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Decoupled(new MI(config)))
    val forwardBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out        = Decoupled(new MI(config))
  })

  val rs1TagMatches = Wire(Vec(config.nWide, UInt(1.W)))
  val rs2TagMatches = Wire(Vec(config.nWide, UInt(1.W)))
  val overriden     = Wire(Decoupled(new MI(config)))

  (0 until config.nWide).foreach(j => {
    rs1TagMatches(j) := MuxCase(
      io.in.bits.rs1TagValid,
      Array(
        ((io.in.bits.rs1Tag === io.forwardBus(j).bits.tag) & io.forwardBus(j).valid) -> 1.U
      ).toIndexedSeq
    )
    rs2TagMatches(j) := MuxCase(
      io.in.bits.rs2TagValid,
      Array(
        ((io.in.bits.rs2Tag === io.forwardBus(j).bits.tag) & io.forwardBus(j).valid) -> 1.U
      ).toIndexedSeq
    )

    io.forwardBus(j).ready := io.out.ready
  })

  val rs1MatchIndex = Wire(UInt(log2Ceil(config.nWide).W))
  val rs2MatchIndex = Wire(UInt(log2Ceil(config.nWide).W))
  rs1MatchIndex := PriorityEncoder(rs1TagMatches.asUInt)
  rs2MatchIndex := PriorityEncoder(rs2TagMatches.asUInt)

  overriden                  <> io.in
  overriden.bits.rs1TagValid := rs1TagMatches.asUInt.orR
  overriden.bits.rs2TagValid := rs2TagMatches.asUInt.orR
  overriden.bits.rs1Data := Mux(
    rs1TagMatches.asUInt.orR,
    io.forwardBus(rs1MatchIndex.asUInt).bits.data,
    io.in.bits.rs1Data
  )

  overriden.bits.rs2Data := Mux(
    rs2TagMatches.asUInt.orR,
    io.forwardBus(rs2MatchIndex.asUInt).bits.data,
    io.in.bits.rs2Data
  )

  io.out <> overriden
}

class OverrideFromBuses(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val inBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out   = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val forwardBus = Module(new DCBus(new Bus(config))(config.nWide, config.nWide))
  val overriders = Seq.tabulate(config.nWide) { j =>
    Module(new OverrideFromBus(config))
  }

  forwardBus.io.in <> io.inBus

  (0 until config.nWide).foreach(j => {
    overriders(j).io.in         <> io.in(j)
    overriders(j).io.forwardBus <> forwardBus.io.out(j)
    io.out(j)                   <> overriders(j).io.out
  })
}
