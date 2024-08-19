package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DecodeConfig, MI, TagBus}

case class LSMI(config: WoodConfig) extends Bundle {
  val addr    = UInt(config.dataWidth.W)
  val data    = Vec(config.dataWidth / 8, UInt(8.W))
  val result  = Vec(config.dataWidth / 8, UInt(8.W))
  val rdTag   = UInt(config.tagWidth.W)
  val wStrobe = Vec(config.dataWidth / 8, Bool())
  val retired = Bool()
  val exOp    = UInt(DecodeConfig.subWidths(8).W)
  val inst    = UInt(32.W) // for testbench only
  val pc      = UInt(config.pcWidth.W) // for testbench only
}

case class LSBus(config: WoodConfig) extends Bundle {
  val addr = UInt(config.dataWidth.W)
  val data = UInt(config.dataWidth.W)
  val tag  = UInt(config.tagWidth.W)
  val inst = UInt(32.W) // for testbench only
  val pc   = UInt(config.pcWidth.W) // for testbench only
}

class LSUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val lsBus          = Flipped(Vec(config.nWide, ValidIO(new LSBus(config))))
    val flush          = Input(Bool())
    val out            = Decoupled(new MI(config))
  })

  val lsscstage  = Module(new LSScheduleStage(config))
  val lsdc0stage = Module(new LSDCache0Stage(config))
  val lsdc1stage = Module(new LSDCache1Stage(config))
  val lsducstage = Module(new LSDummyCache(config))
  val lsatstage  = Module(new LSAtom(config))

  lsscstage.io.in <> io.in

  lsdc0stage.io.in <> lsscstage.io.out
  lsducstage.io.in <> lsdc0stage.io.outCache

  // lsdc1stage.io.in <> lsdc0stage.io.outCache //TODO connect to cache
  lsdc1stage.io.in     <> lsdc0stage.io.outPass
  lsatstage.io.inPass  <> lsdc1stage.io.out
  lsatstage.io.inCache <> lsducstage.io.out

  lsscstage.io.flush  := io.flush
  lsdc0stage.io.flush := io.flush
  lsdc1stage.io.flush := io.flush

  io.out                  <> lsatstage.io.outEx
  lsdc0stage.io.lsAtomBus <> lsatstage.io.outSQ

  lsscstage.io.lsBus           <> io.lsBus
  lsdc0stage.io.storeRetireBus <> io.storeRetireBus

  lsdc0stage.io.lsDCacheBus <> lsdc1stage.io.lsDCacheBus

  lsdc1stage.io.lsAtomBus.bits  := lsatstage.io.outSQ.bits
  lsdc1stage.io.lsAtomBus.valid := lsatstage.io.outSQ.valid
}
