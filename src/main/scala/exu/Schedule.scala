package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCArbiter, DCDemux, DCPipelineRegister}

class ReadyList(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus   = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val commitedBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val readyList = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(1.W))))

  (0 until config.nWide).foreach(j => {
    when(io.forwardBus(j).valid) {
      readyList(io.forwardBus(j).bits.tag) := 1.U
    }
    when(io.commitedBus(j).valid) {
      readyList(io.commitedBus(j).bits.tag) := 0.U
    }
  })

  // No need to forward the commitedBus, there is a 2 cycle delay between tag being added to the free list and ready list is being read.
  val overrideForward = Module(new OverrideRsFromBuses(config))
  val overrideWakeup  = Module(new OverrideRsFromBuses(config))

  overrideForward.io.inBus := io.forwardBus.map { bus =>
    val dBus = Wire(ValidIO(new DataBus(config)))
    dBus.bits.tag  := bus.bits.tag
    dBus.valid     := bus.valid
    dBus.bits.data := DontCare
    dBus
  }
  overrideWakeup.io.inBus := io.wakeupBus.map { bus =>
    val dBus = Wire(ValidIO(new DataBus(config)))
    dBus.bits.tag  := bus.bits.tag
    dBus.valid     := bus.valid
    dBus.bits.data := DontCare
    dBus
  }

  val overridenRsTagReady = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRsTagReady <> io.in
  (0 until config.nWide).foreach(j => {
    overridenRsTagReady(j).bits.rs1TagReady := io.in(j).bits.rs1TagReady | readyList(io.in(j).bits.rs1Tag)
    overridenRsTagReady(j).bits.rs2TagReady := io.in(j).bits.rs2TagReady | readyList(io.in(j).bits.rs2Tag)
  })
  overrideForward.io.in <> overridenRsTagReady
  overrideWakeup.io.in  <> overrideForward.io.out
  io.out                <> overrideWakeup.io.out
}

class ScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus   = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val commitedBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val stall       = Input(UInt(1.W))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new ReservationStation(config))
  }
  val bypassArbiters = Seq.tabulate(config.nWide) { _ =>
    Module(new DCArbiter(new MI(config))(2, 1))
  }
  val bypassDemuxes = Seq.tabulate(config.nWide) { _ =>
    Module(new DCDemux(new MI(config))(1, 2))
  }

  val readyList = Module(new ReadyList(config))
  val pRegs     = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  readyList.io.in          <> io.in
  readyList.io.commitedBus <> io.commitedBus
  readyList.io.wakeupBus   <> io.wakeupBus
  readyList.io.forwardBus  <> io.forwardBus

  (0 until config.nWide).foreach(j => {
    bypassDemuxes(j).io.sel(0) := io.out(j).ready & io.in(j).valid & io.in(j).bits.rs1TagReady & io.in(j).bits.rs2TagReady

    bypassDemuxes(j).io.in(0)    <> readyList.io.out(j)
    reservationStations(j).io.in <> bypassDemuxes(j).io.out(0)(0)
    bypassArbiters(j).io.in(1)   <> bypassDemuxes(j).io.out(1)(0)
    bypassArbiters(j).io.in(0)   <> reservationStations(j).io.out

    reservationStations(j).io.forwardBus <> io.forwardBus
    reservationStations(j).io.wakeupBus  <> io.wakeupBus
    reservationStations(j).io.stall      := io.stall

    pRegs(j).io.valids(0) := bypassArbiters(j).io.out(0).valid

    pRegs(j).io.in <> bypassArbiters(j).io.out(0)
    io.out(j)      <> pRegs(j).io.out
  })
}
