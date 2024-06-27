package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RenameStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val retiredBus = Flipped(Vec(config.nWide, Decoupled(new Tag(config))))
    val out        = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val numReadPorts  = (config.nWide * 2)
  val numWritePorts = config.nWide

  val flist = Module(new FreeList(config))
  flist.io.in <> io.retiredBus

  val frfDepth = 32
  val frontEndRegisterFile = Module(
    new DecoupledBlockRAM(new Tag(config))(
      BlockRAMParams(frfDepth, numReadPorts, numWritePorts)
    )
  )

  (0 until config.nWide).foreach(j => {
    frontEndRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rs1
    frontEndRegisterFile.io.rip(j).valid     := io.in(j).valid

    frontEndRegisterFile.io.rip(j + config.nWide).bits.addr := io.in(j).bits.rs2
    frontEndRegisterFile.io.rip(j + config.nWide).valid     := io.in(j).valid

    io.in(j).ready := frontEndRegisterFile.io.rip(j + config.nWide).ready & frontEndRegisterFile.io.rip(j).ready
  })

  (0 until config.nWide).foreach(j => {
    val read_freelist = io.in(j).bits.write_rf === BitPat(s"b${DecodeConfig.WRITE_RF_1}")
    flist.io.out(j).ready := io.in(j).valid & read_freelist & frontEndRegisterFile.io.wp(j).ready
    io.in(j).ready        := flist.io.out(j).valid

    frontEndRegisterFile.io.wp(j).bits.addr   := io.in(j).bits.rd
    frontEndRegisterFile.io.wp(j).bits.data   := flist.io.out(j).bits
    frontEndRegisterFile.io.wp(j).valid       := flist.io.out(j).valid
    frontEndRegisterFile.io.wp(j).bits.enable := flist.io.out(j).valid
  })

  (0 until config.nWide).foreach(j => {
    io.out(j).bits                                      := io.in(j).bits
    io.out(j).valid                                     := frontEndRegisterFile.io.rop(j).valid & frontEndRegisterFile.io.rop(j + config.nWide).valid
    frontEndRegisterFile.io.rop(j).ready                := io.out(j).ready
    frontEndRegisterFile.io.rop(j + config.nWide).ready := io.out(j).ready
  })

  (0 until config.nWide).foreach(j => {
    io.out(j).bits.rs1_tag := frontEndRegisterFile.io.rop(j).bits.data.tag
    io.out(j).bits.rs2_tag := frontEndRegisterFile.io.rop(j + config.nWide).bits.data.tag
  })
}
