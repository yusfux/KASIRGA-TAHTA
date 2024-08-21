package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.DCPipelineRegister

class LSDCache1Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val lsAtomBus      = Flipped(ValidIO(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val lsDCache1Bus   = ValidIO(new LSCMI(config))
    val out            = Decoupled(new LSCMI(config))
  })

  val pReg            = Module(new DCPipelineRegister(new LSCMI(config))(1))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val self            = Wire(Decoupled(new LSCMI(config)))

  retireOverrider.io.storeRetireBus <> io.storeRetireBus

  retireOverrider.io.in <> io.in
  self                  <> retireOverrider.io.out

  pReg.io.flush     := io.flush
  pReg.io.valids(0) := io.in.valid

  io.lsDCache1Bus.bits  := io.in.bits
  io.lsDCache1Bus.valid := io.in.valid

  (0 until config.numDCacheLineBytes).foreach(j => {
    val selfAddr = io.in.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
    val atomAddr = io.lsAtomBus.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)

    val atomByteUpdate = io.lsAtomBus.bits.wStrobe(j) && (atomAddr === selfAddr)

    self.bits.cacheLine(j) := MuxCase(
      io.in.bits.cacheLine(j),
      Array(
        (io.in.bits.wStrobe(j)) -> io.in.bits.cacheLine(j),
        (atomByteUpdate)        -> io.lsAtomBus.bits.cacheLine(j)
      ).toIndexedSeq
    )
    self.bits.wStrobe(j) := io.in.bits.wStrobe(j) | atomByteUpdate
  })

  pReg.io.in <> self
  io.out     <> pReg.io.out
}
