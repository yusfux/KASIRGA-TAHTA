package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus

class LSAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inPass         = Flipped(Decoupled(new LSMI(config)))
    val inCache        = Flipped(Decoupled(new LSMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val outSQ          = Decoupled(new LSMI(config)) // TODO ready
    val outRF          = ValidIO(new LSMI(config))
  })

  val retireOverrider = Module(new LSOverrideRetire(config))
  val alu             = Module(new ALUAtom(config))
  val selfMerged      = Wire(Decoupled(new LSMI(config)))

  selfMerged       <> io.inCache
  selfMerged.ready := io.outSQ.ready
  alu.io.out.ready := selfMerged.ready

  val isWriteOperation = selfMerged.bits.wStrobe.asUInt.orR

  (0 until config.dataWidth / 8).foreach(j => {
    val atomByteUpdate = io.inPass.bits.wStrobe(j) && (io.inPass.bits.addr === io.inCache.bits.addr)

    selfMerged.bits.memDataRead(j) := MuxCase(
      io.inCache.bits.memDataRead(j),
      Array(
        (io.inCache.bits.wStrobe(j)) -> io.inCache.bits.memDataRead(j),
        (atomByteUpdate)             -> io.inPass.bits.memDataRead(j)
      ).toIndexedSeq
    )
    selfMerged.bits.wStrobe(j) := io.inCache.bits.wStrobe(j) | atomByteUpdate
  })

  retireOverrider.io.storeRetireBus := io.storeRetireBus

  alu.io.in <> selfMerged

  io.inPass.ready  := alu.io.in.ready
  io.inCache.ready := alu.io.in.ready

  retireOverrider.io.in <> alu.io.out
  io.outSQ              <> retireOverrider.io.out
  io.outSQ.valid        := retireOverrider.io.out.valid & isWriteOperation

  io.outRF.bits  := selfMerged.bits
  io.outRF.valid := selfMerged.valid

  dontTouch(io.inPass.bits.inst) //  testbench only
  dontTouch(io.inPass.bits.pc) //  testbench only
  dontTouch(io.inCache.bits.inst) //  testbench only
  dontTouch(io.inCache.bits.pc) //  testbench only
}
