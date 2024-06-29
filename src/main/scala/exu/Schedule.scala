package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class ScheduleStage(val config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus  = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val stall       = Input(UInt(1.W))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val reservationStations = Seq.tabulate(config.nWide) { j =>
    Module(new ReservationStation(config))
  }

  val validList = Module(
    new DecoupledBlockRAM(UInt(1.W))(
      BlockRAMParams(config.prfDepth, config.nWide * 2, config.nWide * 2)
    )
  )

  val rs1_tag_valid = Wire(Vec(config.nWide, UInt(1.W)))
  val rs2_tag_valid = Wire(Vec(config.nWide, UInt(1.W)))
  val updatedIn     = Wire(Vec(config.nWide, new MI(config)))

  (0 until config.nWide).foreach(j => {
    rs2_tag_valid(j) := MuxCase(
      validList.io.rop(j + config.nWide).bits.data,
      Array(
        (io.in(j).bits.operand === Integer.parseInt(DecodeConfig.OPERAND_IMM, 2).U)   -> 1.U,
        (io.in(j).bits.operand === Integer.parseInt(DecodeConfig.OPERAND_PCIMM, 2).U) -> 1.U
      ).toIndexedSeq
    )

    rs1_tag_valid(j) := MuxCase(
      validList.io.rop(j).bits.data,
      Array(
        (io.in(j).bits.operand === Integer.parseInt(DecodeConfig.OPERAND_PCIMM, 2).U) -> 1.U
      ).toIndexedSeq
    )

    updatedIn(j)               := io.in(j).bits
    updatedIn(j).rs1_tag_valid := rs1_tag_valid(j)
    updatedIn(j).rs2_tag_valid := rs2_tag_valid(j)

    reservationStations(j).io.in.bits  := updatedIn(j)
    reservationStations(j).io.in.valid := io.in(j).valid
    io.in(j).ready                     := reservationStations(j).io.in.ready

    validList.io.rip(j).bits.addr                := io.in(j).bits.rs1_tag
    validList.io.rip(j).valid                    := io.in(j).valid
    validList.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2_tag
    validList.io.rip(j + config.nWide).valid     := io.in(j).valid

    validList.io.rop(j).ready                := 1.U // TODO
    validList.io.rop(j + config.nWide).ready := 1.U // TODO

    validList.io.wp(j).bits.addr   := io.forwardBus(j).bits.tag
    validList.io.wp(j).bits.enable := io.forwardBus(j).valid
    validList.io.wp(j).bits.data   := 1.U
    validList.io.wp(j).valid       := io.forwardBus(j).valid
    io.forwardBus(j).ready         := validList.io.wp(j).ready

    validList.io.wp(j + config.nWide).bits.addr   := io.commitedBus(j).bits.tag
    validList.io.wp(j + config.nWide).bits.enable := io.commitedBus(j).valid
    validList.io.wp(j + config.nWide).bits.data   := 0.U
    validList.io.wp(j + config.nWide).valid       := io.commitedBus(j).valid
    io.commitedBus(j).ready                       := validList.io.wp(j + config.nWide).ready

    reservationStations(j).io.stall := io.stall

    reservationStations(j).io.forwardBus <> io.forwardBus

    io.out(j).bits  := RegEnable(reservationStations(j).io.out.bits, 0.U.asTypeOf(new MI(config)), io.stall.asBool)
    io.out(j).valid := RegEnable(reservationStations(j).io.out.valid, 1.B, io.stall.asBool)

    reservationStations(j).io.out.ready := io.out(j).ready
  })
}
