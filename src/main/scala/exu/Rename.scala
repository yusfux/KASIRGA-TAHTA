package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RenameStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val flist = Module(new FreeList(config))
  flist.io.in <> io.commitedBus

  val frontEndRegisterFile = Module(
    new DecoupledBlockRAM(new Bus(config))(
      BlockRAMParams(32, config.nWide * 2, config.nWide)
    )
  )

  (0 until config.nWide).foreach(j => {
    frontEndRegisterFile.io.rip(j).bits.addr                := io.in(j).bits.rs1
    frontEndRegisterFile.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2

    frontEndRegisterFile.io.rip(j).valid                := io.in(j).valid
    frontEndRegisterFile.io.rip(j + config.nWide).valid := io.in(j).valid

    io.in(j).ready := frontEndRegisterFile.io.rip(j + config.nWide).ready & frontEndRegisterFile.io.rip(j).ready
  })

  (0 until config.nWide).foreach(j => {
    val read_freelist = io.in(j).bits.write_rf === DecodeConfig.WRITE_RF_1.toInt.U
    flist.io.out(j).ready := io.in(j).valid & read_freelist

    frontEndRegisterFile.io.wp(j).bits.addr   := io.in(j).bits.rd
    frontEndRegisterFile.io.wp(j).bits.data   := flist.io.out(j).bits
    frontEndRegisterFile.io.wp(j).valid       := flist.io.out(j).valid && read_freelist
    frontEndRegisterFile.io.wp(j).bits.enable := flist.io.out(j).valid

    io.in(j).ready := flist.io.out(j).valid
  })

  val in_ready = Wire(Vec(config.nWide, Bool()))
  val in_valid = Wire(Vec(config.nWide, Bool()))

  in_ready := io.in.map(_.ready)
  in_valid := io.in.map(_.valid)

  // all inputs have to be valid and all outputs have to be ready to not stall
  val valid = in_valid.asUInt.andR
  val ready = in_ready.asUInt.andR
  val stall = !(valid && ready)

  val outBitsNext  = Wire(Vec(config.nWide, new MI(config)))
  val outValidNext = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach(j => {
    outValidNext(j) := frontEndRegisterFile.io.rop(j).valid & frontEndRegisterFile.io.rop(j + config.nWide).valid

    outBitsNext(j)         := io.in(j).bits
    outBitsNext(j).rs1_tag := frontEndRegisterFile.io.rop(j).bits.data.tag
    outBitsNext(j).rs2_tag := frontEndRegisterFile.io.rop(j + config.nWide).bits.data.tag
    outBitsNext(j).rd_tag  := flist.io.out(j).bits.tag

    io.out(j).bits  := RegEnable(outBitsNext(j), 0.U.asTypeOf(new MI(config)), !stall)
    io.out(j).valid := RegEnable(outValidNext(j), 1.U, !stall)

    frontEndRegisterFile.io.rop(j).ready                := io.out(j).ready
    frontEndRegisterFile.io.rop(j + config.nWide).ready := io.out(j).ready

  })
}
