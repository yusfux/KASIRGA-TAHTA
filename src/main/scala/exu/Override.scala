package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class OverrideRsFromBus(val config: WoodConfig, val numBuses: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new MI(config)))
    val inBus = Input(Vec(numBuses, ValidIO(new DataBus(config))))
    val out   = Decoupled(new MI(config))
  })

  val rs1TagMatches = Wire(Vec(numBuses, Bool()))
  val rs2TagMatches = Wire(Vec(numBuses, Bool()))

  (0 until numBuses).foreach { i =>
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

class OverrideRsFromBuses(val config: WoodConfig, val numBuses: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val inBus = Input(Vec(numBuses, ValidIO(new DataBus(config))))
    val out   = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val overriders = Seq.tabulate(config.nWide) { _ =>
    Module(new OverrideRsFromBus(config, numBuses))
  }

  (0 until config.nWide).foreach(j => {
    overriders(j).io.in    <> io.in(j)
    overriders(j).io.inBus <> io.inBus
    io.out(j)              <> overriders(j).io.out
  })
}

class OverrideRdFromBus(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new MI(config)))
    val inBus = Input(Vec(config.nWide, ValidIO(new DataBus(config))))
    val out   = Decoupled(new MI(config))
  })

  val rdTagMatches = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach { i =>
    rdTagMatches(i) := (io.in.bits.rdTag === io.inBus(i).bits.tag) & io.inBus(i).valid
  }

  val overriden = Wire(Decoupled(new MI(config)))
  overriden              <> io.in
  overriden.bits.retired := Mux(rdTagMatches.asUInt.orR, 1.U, io.in.bits.retired)

  io.out <> overriden
}

class OverrideArfTagFromBus(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new RetireMI(config)))
    val inBus = Input(Vec(config.nWide, ValidIO(new ARFBus(config))))
    val out   = Decoupled(new RetireMI(config))
  })

  val rdMatches = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach { i =>
    rdMatches(i) := (io.in.bits.rd === io.inBus(i).bits.rd) & io.inBus(i).valid
  }

  val rdMatchIndex = PriorityEncoder(rdMatches.asUInt)

  val overriden = Wire(Decoupled(new RetireMI(config)))
  overriden             <> io.in
  overriden.bits.arfTag := Mux(rdMatches.asUInt.orR, io.inBus(rdMatchIndex).bits.tag, io.in.bits.arfTag)

  io.out <> overriden
}
