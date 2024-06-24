package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.{DecodeConfig, MI}
import wood.std.{BlockRAMParams, DecoupledBlockRAM}

class RetireStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in              = Flipped(Vec(numPorts, Decoupled(new MI())))
    val in_flist_retire = Flipped(Vec(numPorts, Decoupled(UInt(ExConfig.tagWidth.W))))
    val out             = Vec(numPorts, Decoupled(new MI()))
  })

  val numReadPorts  = (numPorts * 2)
  val numWritePorts = numPorts

  val flist = Module(new FreeList(numPorts))
  flist.io.in <> io.in_flist_retire

  val frfDepth = 32
  val frontEndRegisterFile = Module(
    new DecoupledBlockRAM(
      BlockRAMParams(ExConfig.tagWidth, frfDepth, numReadPorts, numWritePorts)
    )
  )

  (0 until numPorts).foreach(j => {
    frontEndRegisterFile.io.rip(j).bits.addr := io.in(j).bits.rs1
    frontEndRegisterFile.io.rip(j).valid     := io.in(j).valid

    frontEndRegisterFile.io.rip(j + numPorts).bits.addr := io.in(j).bits.rs2
    frontEndRegisterFile.io.rip(j + numPorts).valid     := io.in(j).valid

    io.in(j).ready := frontEndRegisterFile.io.rip(j + numPorts).ready & frontEndRegisterFile.io.rip(j).ready
  })

  (0 until numPorts).foreach(j => {
    val read_freelist = io.in(j).bits.write_rf === BitPat(s"b${DecodeConfig.WRITE_RF_1}")
    val rd_tag        = flist.io.out(j).bits
    flist.io.out(j).ready := io.in(j).valid & read_freelist & frontEndRegisterFile.io.wp(j).ready
    io.in(j).ready        := flist.io.out(j).valid

    frontEndRegisterFile.io.wp(j).bits.addr   := io.in(j).bits.rd
    frontEndRegisterFile.io.wp(j).bits.data   := rd_tag
    frontEndRegisterFile.io.wp(j).valid       := flist.io.out(j).valid
    frontEndRegisterFile.io.wp(j).bits.enable := flist.io.out(j).valid
  })

  (0 until numPorts).foreach(j => {
    io.out(j).bits                                  := io.in(j).bits
    io.out(j).valid                                 := frontEndRegisterFile.io.rop(j).valid & frontEndRegisterFile.io.rop(j + numPorts).valid
    frontEndRegisterFile.io.rop(j).ready            := io.out(j).ready
    frontEndRegisterFile.io.rop(j + numPorts).ready := io.out(j).ready
  })

  (0 until numPorts).foreach(j => {
    io.out(j).bits.rs1_tag := frontEndRegisterFile.io.rop(j).bits.data
    io.out(j).bits.rs2_tag := frontEndRegisterFile.io.rop(j + numPorts).bits.data
  })
}
