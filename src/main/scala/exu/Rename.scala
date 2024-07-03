package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.{BlockRAMParams, DCPipelineRegister, DecoupledBlockRAM}

class RenameStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val commitedBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))
    val out         = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val flist = Module(new FreeList(config))
  val pReg  = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  val frontEndRegisterFile = Module(
    new DecoupledBlockRAM(new Bus(config))(
      BlockRAMParams(32, config.nWide * 2, config.nWide)
    )
  )

  flist.io.in <> io.commitedBus

  (0 until config.nWide).foreach(j => {
    frontEndRegisterFile.io.rip(j).bits.addr                := io.in(j).bits.rs1
    frontEndRegisterFile.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2

    frontEndRegisterFile.io.rip(j).valid                := io.in(j).valid
    frontEndRegisterFile.io.rip(j + config.nWide).valid := io.in(j).valid

    io.in(j).ready := frontEndRegisterFile.io.rip(j + config.nWide).ready & frontEndRegisterFile.io.rip(j).ready
  })

  (0 until config.nWide).foreach(j => {
    val read_freelist = io.in(j).bits.writeRf === DecodeConfig.WRITE_RF_1.toInt.U
    flist.io.out(j).ready := io.in(j).valid & read_freelist

    frontEndRegisterFile.io.wp(j).bits.addr   := io.in(j).bits.rd
    frontEndRegisterFile.io.wp(j).bits.data   := flist.io.out(j).bits
    frontEndRegisterFile.io.wp(j).valid       := flist.io.out(j).valid && read_freelist
    frontEndRegisterFile.io.wp(j).bits.enable := flist.io.out(j).valid

    io.in(j).ready := flist.io.out(j).valid
  })

  val overriden = Wire(Vec(config.nWide, new MI(config)))

  (0 until config.nWide).foreach(j => {
    overriden(j)        := io.in(j).bits
    overriden(j).rs1Tag := frontEndRegisterFile.io.rop(j).bits.data.tag
    overriden(j).rs2Tag := frontEndRegisterFile.io.rop(j + config.nWide).bits.data.tag
    overriden(j).rdTag  := flist.io.out(j).bits.tag

    pReg.io.in(j).bits  := overriden(j)
    pReg.io.in(j).valid := frontEndRegisterFile.io.rop(j).valid & frontEndRegisterFile.io.rop(j + config.nWide).valid
    io.in(j).ready      := pReg.io.in(j).ready

    frontEndRegisterFile.io.rop(j).ready                := io.out(j).ready
    frontEndRegisterFile.io.rop(j + config.nWide).ready := io.out(j).ready
  })

  io.out <> pReg.io.out
}
