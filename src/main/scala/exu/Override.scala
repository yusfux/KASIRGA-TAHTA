package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI

class OverrideFromBus(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new MI(config)))
    val inBus = Input(Vec(config.nWide, ValidIO(new DataBus(config))))
    val out   = Decoupled(new MI(config))
  })

  val rs1TagMatches = Wire(Vec(config.nWide, Bool()))
  val rs2TagMatches = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach { i =>
    rs1TagMatches(i) := (io.in.bits.rs1Tag === io.inBus(i).bits.tag) & io.inBus(i).valid
    rs2TagMatches(i) := (io.in.bits.rs2Tag === io.inBus(i).bits.tag) & io.inBus(i).valid
  }

  val rs1MatchIndex = PriorityEncoder(rs1TagMatches.asUInt)
  val rs2MatchIndex = PriorityEncoder(rs2TagMatches.asUInt)

  val overriden = Wire(Decoupled(new MI(config)))
  overriden              <> io.in
  overriden.bits.rs1Data := Mux(rs1TagMatches.asUInt.orR, io.inBus(rs1MatchIndex).bits.data, io.in.bits.rs1Data)
  overriden.bits.rs2Data := Mux(rs2TagMatches.asUInt.orR, io.inBus(rs2MatchIndex).bits.data, io.in.bits.rs2Data)

  overriden.bits.rs1TagReady := io.in.bits.rs1TagReady | rs1TagMatches.asUInt.orR
  overriden.bits.rs2TagReady := io.in.bits.rs2TagReady | rs2TagMatches.asUInt.orR

  io.out <> overriden
}

class OverrideFromBuses(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val inBus = Input(Vec(config.nWide, ValidIO(new DataBus(config))))
    val out   = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val overriders = Seq.tabulate(config.nWide) { _ =>
    Module(new OverrideFromBus(config))
  }

  (0 until config.nWide).foreach(j => {
    overriders(j).io.in    <> io.in(j)
    overriders(j).io.inBus <> io.inBus
    io.out(j)              <> overriders(j).io.out
  })
}
