package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.DCPipelineRegister

class LSDCache1Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSMI(config)))
    val lsAtomBus      = Flipped(ValidIO(new LSMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val lsDCacheBus    = ValidIO(new LSMI(config))
    val out            = Decoupled(new LSMI(config))
  })

  val pReg            = Module(new DCPipelineRegister(new LSMI(config))(1))
  val retireOverrider = Module(new LSOverrideRetire(config))
  val self            = Wire(Decoupled(new LSMI(config)))

  retireOverrider.io.storeRetireBus <> io.storeRetireBus

  retireOverrider.io.in <> io.in
  self                  <> retireOverrider.io.out

  pReg.io.flush     := io.flush
  pReg.io.valids(0) := io.in.valid

  io.lsDCacheBus.bits  := io.in.bits
  io.lsDCacheBus.valid := io.in.valid

  (0 until config.dataWidth / 8).foreach(j => {
    val atomByteUpdate = io.lsAtomBus.bits.wStrobe(j) && (io.in.bits.addr === io.lsAtomBus.bits.addr)

    self.bits.rs2Data(j) := MuxCase(
      io.in.bits.rs2Data(j),
      Array(
        (io.in.bits.wStrobe(j)) -> io.in.bits.rs2Data(j),
        (atomByteUpdate)        -> io.lsAtomBus.bits.rs2Data(j)
      ).toIndexedSeq
    )
    self.bits.wStrobe(j) := io.in.bits.wStrobe(j) | atomByteUpdate
  })

  pReg.io.in <> self
  io.out     <> pReg.io.out
}
