package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{FreeList, TagBus}
import wood.fru.MI
import wood.std.{DCPipelineRegisterMultiValid, DCRRQueue}

class MIStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val commitedBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out         = Vec(config.numPortsInt, Decoupled(new MI(config)))
  })

  val flist     = Module(new FreeList(config))
  val miq       = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))
  val pReg      = Module(new DCPipelineRegisterMultiValid(new MI(config))(config.nWide, 1))
  val overriden = Wire(Vec(config.nWide, Decoupled(new MI(config))))

  miq.io.in <> io.in

  (0 until config.nWide).foreach(j => {
    flist.io.in(j).bits.tag := io.commitedBus(j).bits.tag
    flist.io.in(j).valid    := io.commitedBus(j).valid
  })

  for (j <- 0 until config.nWide) {
    overriden(j).bits       := miq.io.out(j).bits
    overriden(j).bits.rdTag := flist.io.out(j).bits.tag
    overriden(j).valid      := miq.io.out(j).valid
    pReg.io.valids(0)(j)    := flist.io.out(j).valid
    flist.io.out(j).ready   := pReg.io.in(j).ready
    miq.io.out(j).ready     := pReg.io.in(j).ready
  }
  pReg.io.in <> overriden
  io.out     <> pReg.io.out
}
