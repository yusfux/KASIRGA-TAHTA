package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{MI, TagBus}
import wood.lsu.{LSBus, LSMI}
import wood.std.{DCPipelineRegister, DCRRArbiter}

class LSScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSBus(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val out            = Decoupled(new LSMI(config))
  })

  val pReg            = Module(new DCPipelineRegister(new LSMI(config))(1))
  val retireOverrider = Module(new LSOverrideRetire(config))
  val arbiter         = Module(new DCRRArbiter(new LSMI(config), config.rsDepth))
  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new LSReservationStation(config))
  }

  (0 until config.nWide).foreach(j => {
    arbiter.io.in(j)                         <> reservationStations(j).io.out
    reservationStations(j).io.lsOperandBus   <> io.lsOperandBus
    reservationStations(j).io.flush          := io.flush
    reservationStations(j).io.storeRetireBus := io.storeRetireBus

    reservationStations(j).io.in <> io.in.map { mi =>
      val remi = Wire(DecoupledIO(new LSMI(config)))
      remi.valid := mi.valid
      remi.ready := reservationStations(j).io.in.ready

      remi.bits         := DontCare
      remi.bits.rdTag   := mi.bits.rdTag
      remi.bits.retired := mi.bits.retired
      remi.bits.inst    := mi.bits.inst // debug only
      remi.bits.pc      := mi.bits.pc // debug only
      remi
    }(j)
    io.in(j).ready := reservationStations(j).io.in.ready
  })

  retireOverrider.io.storeRetireBus := io.storeRetireBus

  pReg.io.valids(0) := arbiter.io.out.valid

  retireOverrider.io.in <> arbiter.io.out
  pReg.io.flush         <> io.flush
  pReg.io.in            <> retireOverrider.io.out
  io.out                <> pReg.io.out
}
