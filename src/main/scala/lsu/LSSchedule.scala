package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.MI
import wood.lsu.{LSBus, LSMI}
import wood.std.{DCPipelineRegister, DCRRArbiter}

class LSScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val lsBus = Flipped(Vec(config.nWide, ValidIO(new LSBus(config))))
    val flush = Input(Bool())
    val out   = Decoupled(new LSMI(config))
  })

  val pReg    = Module(new DCPipelineRegister(new LSMI(config))(1))
  val arbiter = Module(new DCRRArbiter(new LSMI(config), config.rsDepth))
  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new LSReservationStation(config))
  }

  (0 until config.nWide).foreach(j => {
    arbiter.io.in(j)                <> reservationStations(j).io.out
    reservationStations(j).io.lsBus <> io.lsBus
    reservationStations(j).io.flush := io.flush

    reservationStations(j).io.in <> io.in.map { mi =>
      val remi = Wire(DecoupledIO(new LSMI(config)))
      remi.valid := mi.valid
      remi.ready := reservationStations(j).io.in.ready

      remi.bits         := DontCare
      remi.bits.rdTag   := mi.bits.rdTag
      remi.bits.retired := mi.bits.retired
      remi.bits.inst    := mi.bits.inst
      remi
    }(j)
    io.in(j).ready := reservationStations(j).io.in.ready
  })

// reservationStations_0_io_in_remi_1_ready

  pReg.io.valids(0) := arbiter.io.out.valid

  pReg.io.flush := io.flush
  pReg.io.in    <> arbiter.io.out
  io.out        <> pReg.io.out
}
