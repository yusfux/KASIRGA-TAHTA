package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{BlockRAMParams, DCBus, DCPipelineRegister, DecoupledBlockRAM}

class ValidList(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val wakeupBus   = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val validList = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide * 2)
    )
  )
  // No need to forward the commitedBus, there is a 2 cycle delay between tag being added to the free list and valid list is being read.
  val forwardBus        = Module(new DCBus(new Bus(config))(config.nWide, 2)) // to overridenForward and valid list wp
  val overrideForward   = Module(new OverrideFromBuses(config)) // override from the forwardBus
  val overrideWakeup    = Module(new OverrideFromBuses(config)) // override from the wakeupBus
  val overrideValidList = Module(new OverrideFromBuses(config)) // override from the validList

  overrideWakeup.io.inBus    <> io.wakeupBus
  forwardBus.io.in           <> io.forwardBus
  overrideForward.io.inBus   <> forwardBus.io.out(0)
  overrideValidList.io.inBus <> forwardBus.io.out(1)

  val overridenRsTagValid = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRsTagValid <> io.in
  (0 until config.nWide).foreach(j => {
    overridenRsTagValid(j).bits.rs1TagValid := validList.io.rop(j).bits.data
    overridenRsTagValid(j).bits.rs2TagValid := validList.io.rop(j + config.nWide).bits.data
  })
  overrideValidList.io.in <> overridenRsTagValid
  overrideForward.io.in   <> overrideValidList.io.out
  overrideWakeup.io.in    <> overrideForward.io.out
  io.out                  <> overrideWakeup.io.out

  (0 until config.nWide).foreach(j => {
    validList.io.rip(j).bits.addr                := io.in(j).bits.rs1Tag
    validList.io.rip(j).valid                    := io.in(j).valid
    validList.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2Tag
    validList.io.rip(j + config.nWide).valid     := io.in(j).valid

    validList.io.rop(j).ready                := 1.U // TODO
    validList.io.rop(j + config.nWide).ready := 1.U // TODO

    validList.io.wp(j).bits.addr   := forwardBus.io.out(1)(j).bits.tag
    validList.io.wp(j).bits.enable := forwardBus.io.out(1)(j).valid
    validList.io.wp(j).bits.data   := 1.U
    validList.io.wp(j).valid       := forwardBus.io.out(1)(j).valid
    forwardBus.io.out(1)(j).ready  := validList.io.wp(j).ready

    validList.io.wp(j + config.nWide).bits.addr   := io.commitedBus(j).bits.tag
    validList.io.wp(j + config.nWide).bits.enable := io.commitedBus(j).valid
    validList.io.wp(j + config.nWide).bits.data   := 0.U
    validList.io.wp(j + config.nWide).valid       := io.commitedBus(j).valid
    io.commitedBus(j).ready                       := validList.io.wp(j + config.nWide).ready
  })
}

class ScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val wakeupBus   = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val stall       = Input(UInt(1.W))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val reservationStations = Seq.tabulate(config.nWide) { _ =>
    Module(new ReservationStation(config))
  }

  val validList = Module(new ValidList(config))
  val pReg      = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  val wakeupBus    = Module(new DCBus(new Bus(config))(config.nWide, 2)) // to rs and vl
  val forwardBus   = Module(new DCBus(new Bus(config))(config.nWide, 2)) // to rs and vl
  val forwardBusRS = Module(new DCBus(new Bus(config))(config.nWide, config.nWide)) // to rs banks
  val wakeupBusRS  = Module(new DCBus(new Bus(config))(config.nWide, config.nWide)) // to rs banks

  validList.io.in          <> io.in
  validList.io.commitedBus <> io.commitedBus

  wakeupBus.io.in        <> io.wakeupBus
  wakeupBusRS.io.in      <> wakeupBus.io.out(0)
  validList.io.wakeupBus <> wakeupBus.io.out(1)

  forwardBus.io.in        <> io.forwardBus
  forwardBusRS.io.in      <> forwardBus.io.out(0)
  validList.io.forwardBus <> forwardBus.io.out(1)

  (0 until config.nWide).foreach(j => {
    reservationStations(j).io.in         <> validList.io.out(j)
    reservationStations(j).io.forwardBus <> forwardBusRS.io.out(j)
    reservationStations(j).io.wakeupBus  <> wakeupBusRS.io.out(j)
    reservationStations(j).io.stall      := io.stall

    pReg.io.in(j) <> reservationStations(j).io.out
    io.out(j)     <> pReg.io.out(j)
  })
}
