package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DecodeConfig, MI, TagBus}
import wood.std.{DCPipelineRegister, DCRRArbiter}

class ExtendWriteStrobe(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val lsOp = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val addr = Input(UInt(config.xlen.W))
    val out  = Output(Vec(config.numDCacheLineBytes, Bool()))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val wStrobeWord      = Wire(UInt(config.numDCacheLineBytes.W))
  val wStrobeCacheLine = Wire(UInt(config.numDCacheLineBytes.W))
  val padSize          = config.numDCacheLineBytes - 4

  wStrobeWord := 0.U
  // format: off
  switch(control) {
    is(LSOp.lb,LSOp.lbu,LSOp.lh,LSOp.lhu,LSOp.lw ) { wStrobeWord := Cat(Fill(padSize, 0.B),"b0000".U) }
    is(LSOp.sb)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b0001".U) }
    is(LSOp.sh)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b0011".U) }
    is(LSOp.sw)                                    { wStrobeWord := Cat(Fill(padSize, 0.B),"b1111".U) }
  }
  // format: on

  val shiftAmount = io.addr(log2Ceil(config.numDCacheLineBytes) - 1, 0)

  wStrobeCacheLine := (wStrobeWord << shiftAmount)(config.numDCacheLineBytes - 1, 0)

  io.out := wStrobeCacheLine.asBools
}

class LSScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val out            = Decoupled(new LSCMI(config))
  })

  val pReg            = Module(new DCPipelineRegister(new LSCMI(config))(1))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val extendWStrobe   = Module(new ExtendWriteStrobe(config))

  val arbiter = Module(new DCRRArbiter(new LSRSMI(config), config.rsDepth))
  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new LSReservationStation(config))
  }
  val self = Wire(DecoupledIO(new LSCMI(config)))

  (0 until config.nWide).foreach(j => {
    arbiter.io.in(j) <> reservationStations(j).io.out

    reservationStations(j).io.lsOperandBus   <> io.lsOperandBus
    reservationStations(j).io.flush          := io.flush
    reservationStations(j).io.storeRetireBus := io.storeRetireBus

    reservationStations(j).io.in <> io.in.map { mi =>
      val remi = Wire(DecoupledIO(new LSRSMI(config)))
      remi.valid := mi.valid
      remi.ready := reservationStations(j).io.in.ready
      val isStore = (mi.bits.lsType === Integer.parseInt(DecodeConfig.LS_T_S, 2).U)

      remi.bits.retired := mi.bits.retired
      remi.bits.store   := isStore
      remi.bits.addr    := DontCare
      remi.bits.rdTag   := mi.bits.rdTag
      remi.bits.inst    := mi.bits.inst // debug only
      remi.bits.pc      := mi.bits.pc // debug only
      remi.bits.rs2Data := DontCare
      remi.bits.lsOp    := mi.bits.lsOp
      remi
    }(j)
    io.in(j).ready := reservationStations(j).io.in.ready
  })

  retireOverrider.io.storeRetireBus := io.storeRetireBus

  pReg.io.valids(0) := arbiter.io.out.valid

  self.bits.retired   := arbiter.io.out.bits.retired
  self.bits.store     := arbiter.io.out.bits.store
  self.bits.addr      := arbiter.io.out.bits.addr
  self.bits.rdTag     := arbiter.io.out.bits.rdTag
  self.bits.inst      := arbiter.io.out.bits.inst // debug only
  self.bits.pc        := arbiter.io.out.bits.pc // debug only
  self.bits.rs2Data   := arbiter.io.out.bits.rs2Data
  self.bits.lsOp      := arbiter.io.out.bits.lsOp
  self.bits.cacheLine := DontCare
  self.bits.wStrobe   := DontCare

  self.valid           := arbiter.io.out.valid
  arbiter.io.out.ready := self.ready

  extendWStrobe.io.addr := self.bits.addr
  extendWStrobe.io.lsOp := self.bits.lsOp

  retireOverrider.io.in              <> self
  retireOverrider.io.in.bits.wStrobe := extendWStrobe.io.out

  pReg.io.flush <> io.flush
  pReg.io.in    <> retireOverrider.io.out
  io.out        <> pReg.io.out
}
