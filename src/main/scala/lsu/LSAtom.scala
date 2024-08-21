package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{DataBus, DecodeConfig, TagBus}

class LSExtend(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inData = Input(UInt(config.xlen.W))
    val lsOp   = Input(UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W))
    val out    = Output(UInt(config.xlen.W))
  })

  val rawOp = Wire(UInt(LSOp.getWidth.W))
  rawOp := io.lsOp
  val (control, valid) = LSOp.safe(rawOp)

  val result = Wire(UInt(config.xlen.W))

  result := DontCare
// format: off
  switch(control) {
    is(LSOp.lb)  { result := Cat(Fill(24,io.inData( 7)),io.inData( 7,0)) }
    is(LSOp.lh)  { result := Cat(Fill(16,io.inData(15)),io.inData(15,0)) }
    is(LSOp.lw)  { result :=                            io.inData        }
    is(LSOp.lbu) { result := Cat(Fill(24,          0.U),io.inData( 7,0)) }
    is(LSOp.lhu) { result := Cat(Fill(16,          0.U),io.inData(15,0)) }
  }
// format: on
  io.out := result
}

class LSAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inPass         = Flipped(Decoupled(new LSCMI(config)))
    val inCache        = Flipped(Decoupled(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val outSQ          = Decoupled(new LSCMI(config)) // TODO ready
    val outRF          = Vec(1, ValidIO(new DataBus(config)))
  })

  val extender        = Module(new LSExtend(config))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val alu             = Module(new ALUAtom(config))
  val selfMerged      = Wire(Decoupled(new LSCMI(config)))

  selfMerged       <> io.inCache
  selfMerged.ready := io.outSQ.ready
  alu.io.out.ready := selfMerged.ready

  val isWriteOperation = selfMerged.bits.wStrobe.asUInt.orR

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

  retireOverrider.io.storeRetireBus := io.storeRetireBus

  alu.io.in <> selfMerged

  io.inPass.ready  := alu.io.in.ready
  io.inCache.ready := alu.io.in.ready

  retireOverrider.io.in <> alu.io.out
  io.outSQ              <> retireOverrider.io.out
  io.outSQ.valid        := retireOverrider.io.out.valid & isWriteOperation

  val addrOffset  = (selfMerged.bits.addr(log2Ceil(config.numDCacheLineBytes) - 1, 2))
  val shiftAmount = addrOffset * (config.xlen).U
  val data        = (selfMerged.bits.cacheLine.asUInt >> shiftAmount)(config.xlen, 0)

  extender.io.inData    := data
  extender.io.lsOp      := selfMerged.bits.lsOp
  io.outRF(0).bits.data := extender.io.out
  io.outRF(0).bits.tag  := selfMerged.bits.rdTag
  io.outRF(0).valid     := selfMerged.valid

  dontTouch(io.inPass.bits.inst) //  testbench only
  dontTouch(io.inPass.bits.pc) //  testbench only
  dontTouch(io.inCache.bits.inst) //  testbench only
  dontTouch(io.inCache.bits.pc) //  testbench only
}
