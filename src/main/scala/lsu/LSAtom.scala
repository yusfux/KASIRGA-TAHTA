package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DataBus, TagBus}

class LSAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inPass         = Flipped(Decoupled(new LSCMI(config)))
    val inCache        = Flipped(Decoupled(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val selfRetired    = Output(Bool())
    val outSQ          = Decoupled(new LSCMI(config)) // TODO ready
    val outRF          = Output(Vec(1, ValidIO(new DataBus(config))))
  })

  val extender           = Module(new LSExtend(config))
  val retireOverrider    = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val alu                = Module(new ALUAtom(config))
  val selfMerged         = Wire(Decoupled(new LSCMI(config)))
  val rs2                = Wire(UInt(config.xlen.W))
  val mem                = Wire(UInt(config.xlen.W))
  val mergedMem          = Wire(UInt(config.xlen.W))
  val storeCacheLine     = Wire(Vec(config.numDCacheLineBytes, UInt(8.W)))
  val storeCacheLineMask = Wire(UInt(config.dCacheLineWidth.W))
  val storeDataShifted   = Wire(UInt(config.dCacheLineWidth.W))

  selfMerged                <> io.inPass
  selfMerged.bits.cacheLine := io.inCache.bits.cacheLine

  io.inPass.ready  := io.outSQ.ready
  io.inCache.ready := io.outSQ.ready

  val storeMask   = Fill(config.xlen, 0.B)
  val shiftAmount = selfMerged.bits.addr(log2Ceil(config.numDCacheLineBytes) - 1, 0) * 8.U
  mem                := (io.inCache.bits.cacheLine.asUInt >> shiftAmount)(config.xlen - 1, 0)
  rs2                := (selfMerged.bits.cacheLine.asUInt >> shiftAmount)(config.xlen - 1, 0)
  mergedMem          := (selfMerged.bits.cacheLine.asUInt >> shiftAmount)(config.xlen - 1, 0)
  storeCacheLineMask := (storeMask << shiftAmount)(config.dCacheLineWidth - 1, 0)
  storeDataShifted   := (alu.io.out << shiftAmount)(config.dCacheLineWidth - 1, 0)

  val tmpStoreCacheline = (selfMerged.bits.cacheLine.asUInt & storeCacheLineMask) | storeDataShifted
  storeCacheLine := tmpStoreCacheline.asTypeOf(Vec(config.numDCacheLineBytes, UInt(8.W)))

  alu.io.rs2            := rs2
  alu.io.mem            := mem
  alu.io.lsOp           := selfMerged.bits.lsOp
  extender.io.inData    := mergedMem
  extender.io.lsOp      := selfMerged.bits.lsOp
  io.outRF(0).bits.data := extender.io.out
  io.outRF(0).bits.tag  := selfMerged.bits.rdTag
  io.outRF(0).valid     := selfMerged.valid && !selfMerged.bits.store // TODO: let the atoms go

  retireOverrider.io.storeRetireBus    <> io.storeRetireBus
  retireOverrider.io.in                <> selfMerged
  retireOverrider.io.in.bits.cacheLine <> storeCacheLine
  io.outSQ                             <> retireOverrider.io.out
  io.outSQ.valid                       := retireOverrider.io.out.valid & selfMerged.bits.store
  io.outSQ.bits.commitable             := 1.B
  io.outSQ.bits.cacheLine              := selfMerged.bits.cacheLine

  io.selfRetired := retireOverrider.io.out.bits.retired

  // apply accumulated writes to the data read from cache
  (0 until config.numDCacheLineBytes).foreach(j => {
    // assert(io.inPass.bits.addr === io.inCache.bits.addr) //  https://github.com/llvm/circt/issues/6970

    selfMerged.bits.cacheLine(j) := MuxCase(
      io.inCache.bits.cacheLine(j),
      Array(
        (io.inPass.bits.wStrobe(j)) -> io.inPass.bits.cacheLine(j)
      ).toIndexedSeq
    )
    selfMerged.bits.wStrobe(j) := io.inPass.bits.wStrobe(j)
  })

  dontTouch(io.inPass.bits.inst) //  testbench only
  dontTouch(io.inPass.bits.pc) //  testbench only
  dontTouch(io.inCache.bits.inst) //  testbench only
  dontTouch(io.inCache.bits.pc) //  testbench only
}
