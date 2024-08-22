package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DecodeConfig, MI, TagBus}
import wood.std.DCRRShifter
import wood.util.WoodLSCMIPipelineRegister

class LSArbiter[T <: Data](gen: T)(numInputs: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numInputs, Decoupled(gen.cloneType)))
    val out = Decoupled(gen.cloneType)
  })
  val counter = Counter(numInputs)

  when(io.in(counter.value).fire) {
    counter.inc()
  }

  io.in.map(_.ready := 0.B)
  io.out            <> io.in(counter.value)
}

class LSScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val frontRetired   = Input(Bool())
    val selfRetired    = Output(Vec(config.nWide, Bool()))
    val out            = Decoupled(new LSCMI(config))
  })

  val pReg            = Module(new WoodLSCMIPipelineRegister(config))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val extendWStrobe   = Module(new LSExtendWriteStrobe(config))
  val inputRRShifter  = Module(new DCRRShifter(new MI(config))(config.nWide))
  val lsarbiter       = Module(new LSArbiter(new LSRSMI(config))(config.nWide))
  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new LSReservationStation(config))
  }
  val inputRetireOverrider = Seq.tabulate(config.nWide) { _ =>
    Module(new LSOverrideRetire(new MI(config))(config))
  }
  val self                    = Wire(DecoupledIO(new LSCMI(config)))
  val reservationStationReady = Wire(Vec(config.nWide, Bool()))
  val allReady                = reservationStationReady.asUInt.andR

  inputRRShifter.io.in    <> io.in
  inputRRShifter.io.flush := io.flush

  (0 until config.nWide).foreach(j => {
    inputRetireOverrider(j).io.storeRetireBus := io.storeRetireBus
    inputRetireOverrider(j).io.in             <> io.in(j)
    io.selfRetired(j)                         := inputRetireOverrider(j).io.out.bits.retired
    inputRetireOverrider(j).io.out.ready      := DontCare // unused

    reservationStationReady(j)    := reservationStations(j).io.in.ready
    io.in(j).ready                := allReady
    inputRRShifter.io.in(j).valid := io.in(j).valid && allReady

    lsarbiter.io.in(j) <> reservationStations(j).io.out

    reservationStations(j).io.lsOperandBus   <> io.lsOperandBus
    reservationStations(j).io.flush          := io.flush
    reservationStations(j).io.storeRetireBus := io.storeRetireBus

    reservationStations(j).io.in <> inputRRShifter.io.out.map { mi =>
      val remi = Wire(DecoupledIO(new LSRSMI(config)))
      remi.valid := mi.valid
      remi.ready := reservationStations(j).io.in.ready
      val isStore = (mi.bits.lsType === Integer.parseInt(DecodeConfig.LS_T_S, 2).U)
      val isAtom  = (mi.bits.lsType === Integer.parseInt(DecodeConfig.LS_T_A, 2).U)

      remi.bits.retired      := mi.bits.retired | inputRetireOverrider(j).io.out.bits.retired
      remi.bits.store        := isStore
      remi.bits.atom         := isAtom
      remi.bits.addr         := mi.bits.rdData
      remi.bits.rdTag        := mi.bits.rdTag
      remi.bits.inst         := mi.bits.inst // debug only
      remi.bits.pc           := mi.bits.pc // debug only
      remi.bits.rs2Data      := mi.bits.rs2Data
      remi.bits.lsOp         := mi.bits.lsOp
      remi.bits.operandReady := mi.bits.operandReady
      remi
    }(j)
    inputRRShifter.io.out(j).ready := reservationStations(j).io.in.ready
  })

  retireOverrider.io.storeRetireBus := io.storeRetireBus

  pReg.io.frontRetired := io.frontRetired

  val shiftAmount    = lsarbiter.io.out.bits.addr(log2Ceil(config.numDCacheLineBytes) - 1, 0) * 8.U
  val rs2InCacheline = (lsarbiter.io.out.bits.rs2Data << shiftAmount)(config.dCacheLineWidth - 1, 0)

  self.bits.retired    := lsarbiter.io.out.bits.retired
  self.bits.store      := lsarbiter.io.out.bits.store
  self.bits.atom       := lsarbiter.io.out.bits.atom
  self.bits.addr       := lsarbiter.io.out.bits.addr
  self.bits.rdTag      := lsarbiter.io.out.bits.rdTag
  self.bits.inst       := lsarbiter.io.out.bits.inst // debug only
  self.bits.pc         := lsarbiter.io.out.bits.pc // debug only
  self.bits.cacheLine  := rs2InCacheline.asTypeOf(Vec(config.numDCacheLineBytes, UInt(8.W)))
  self.bits.lsOp       := lsarbiter.io.out.bits.lsOp
  self.bits.commitable := 0.B

  self.bits.wStrobe := DontCare

  self.valid             := lsarbiter.io.out.valid
  lsarbiter.io.out.ready := self.ready

  extendWStrobe.io.addr := self.bits.addr
  extendWStrobe.io.lsOp := self.bits.lsOp

  retireOverrider.io.in              <> self
  retireOverrider.io.in.bits.wStrobe := extendWStrobe.io.out

  pReg.io.flush <> io.flush
  pReg.io.in    <> retireOverrider.io.out
  io.out        <> pReg.io.out
}
