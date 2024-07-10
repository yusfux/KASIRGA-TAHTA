package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAM, BlockRAMParams, DCPipelineRegister}

class ReadyList(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val wakeupBus   = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val commitedBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val readyList = Module(
    new BlockRAM(UInt(1.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide * 2)
    )
  )

  (0 until config.nWide).foreach(j => {
    readyList.io.rip(j).addr                := io.in(j).bits.rs1Tag
    readyList.io.rip(j + config.nWide).addr := io.in(j).bits.rs2Tag

    readyList.io.wp(j).addr   := io.forwardBus(j).bits.tag
    readyList.io.wp(j).enable := io.forwardBus(j).valid
    readyList.io.wp(j).data   := 1.U

    readyList.io.wp(j + config.nWide).addr   := io.commitedBus(j).bits.tag
    readyList.io.wp(j + config.nWide).enable := io.commitedBus(j).valid
    readyList.io.wp(j + config.nWide).data   := 0.U
  })

  // No need to forward the commitedBus, there is a 2 cycle delay between tag being added to the free list and ready list is being read.
  val overrideForward = Module(new OverrideFromBuses(config))
  val overrideWakeup  = Module(new OverrideFromBuses(config))

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
    overridenRsTagReady(j).bits.rs1TagReady := io.in(j).bits.rs1TagReady | readyList.io.rop(j).data
    overridenRsTagReady(j).bits.rs2TagReady := io.in(j).bits.rs2TagReady | readyList.io.rop(j + config.nWide).data
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

  val readyList = Module(new ReadyList(config))
  val pReg      = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  readyList.io.in          <> io.in
  readyList.io.commitedBus <> io.commitedBus
  readyList.io.wakeupBus   <> io.wakeupBus
  readyList.io.forwardBus  <> io.forwardBus

  (0 until config.nWide).foreach(j => {
    reservationStations(j).io.in            <> readyList.io.out(j)
    reservationStations(j).io.forwardBus(j) <> io.forwardBus(j)
    reservationStations(j).io.wakeupBus(j)  <> io.wakeupBus(j)
    reservationStations(j).io.stall         := io.stall

    pReg.io.in(j) <> reservationStations(j).io.out
    io.out(j)     <> pReg.io.out(j)
  })
}
