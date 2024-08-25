package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCPipelineRegister

class LSDummyCache(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSCMI(config)))
    val out = Decoupled(new LSCMI(config))
  })

  val pRegOut    = Module(new DCPipelineRegister(new LSCMI(config))(1))
  val pRegMid    = Module(new DCPipelineRegister(new LSCMI(config))(1))
  val getWStrobe = Module(new LSGetWriteStrobe(config))
  val sram       = dontTouch(SRAM(config.dcacheDepth, UInt(config.mmInterfaceWidth.W), 0, 0, 1))

  val sramOut = Wire(UInt(config.mmInterfaceWidth.W))

  val sHit :: sWrite :: Nil = Enum(2)
  val state                 = RegInit(sHit)

  val isWrite = io.in.valid && io.in.bits.store

  sram.readwritePorts(0).address := MuxCase(
    io.in.bits.addr(config.xlen - 1, log2Ceil(config.dCacheLineWidth) - 3),
    Array(
      (state === sHit)   -> io.in.bits.addr(config.xlen - 1, log2Ceil(config.dCacheLineWidth) - 3),
      (state === sWrite) -> pRegMid.io.out.bits.addr(config.xlen - 1, log2Ceil(config.dCacheLineWidth) - 3)
    ).toIndexedSeq
  )

  sram.readwritePorts(0).isWrite := MuxCase(
    0.B,
    Array(
      (state === sHit)   -> 0.B,
      (state === sWrite) -> 1.B
    ).toIndexedSeq
  )

  getWStrobe.io.addr := pRegMid.io.out.bits.addr
  getWStrobe.io.lsOp := pRegMid.io.out.bits.lsOp

  val mask      = Wire(UInt(config.xlen.W))
  val byteMasks = Wire(Vec(config.numBytes, UInt(config.xlen.W)))
  for (j <- 0 until config.numBytes) {
    when(getWStrobe.io.out(j)) {
      byteMasks(j) := (0xff.U << (j * 8))
    }.otherwise {
      byteMasks(j) := (0x00.U << (j * 8))
    }
  }
  mask := byteMasks.reduce(_ | _)

  val numdCacheLineWords = config.dCacheLineWidth / config.xlen
  val shiftAmount        = pRegMid.io.out.bits.addr(numdCacheLineWords - 1, 2) * config.xlen.U
  val storeData          = (pRegMid.io.out.bits.cacheData.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0)
  val cacheMask          = ~((mask.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0))
  val storeDataMask      = ((mask.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0))
  val maskedCacheline    = (sramOut & cacheMask)
  val maskedStoreData    = (storeData & storeDataMask)

  sram.readwritePorts(0).writeData := maskedCacheline | maskedStoreData

  sram.readwritePorts(0).enable := (io.in.valid & (state === sHit)) | (pRegMid.io.out.valid & (state === sWrite))
  sramOut                       := sram.readwritePorts(0).readData

  val cacheReadData = (sramOut >> shiftAmount)(config.xlen - 1, 0)

  // debug only
  dontTouch(mask)
  dontTouch(cacheMask)
  dontTouch(storeDataMask)
  dontTouch(shiftAmount)
  dontTouch(maskedCacheline)
  dontTouch(maskedStoreData)

  pRegMid.io.valids(0) := io.in.valid
  pRegOut.io.valids(0) := pRegMid.io.out.valid
  pRegMid.io.flush     := false.B
  pRegOut.io.flush     := false.B

  pRegMid.io.in        <> io.in
  io.in.ready          := !(state === sWrite)
  pRegMid.io.out.ready := !(state === sWrite) & pRegOut.io.in.ready

  pRegOut.io.in                <> pRegMid.io.out
  pRegOut.io.in.bits.cacheData := cacheReadData.asTypeOf(Vec(config.numBytes, UInt(8.W)))
  pRegOut.io.in.valid          := pRegMid.io.out.valid && !(state === sWrite)
  io.out                       <> pRegOut.io.out

  // FSM logic
  switch(state) {
    is(sHit) {
      when(io.in.valid && isWrite) {
        state := sWrite
      }
    }
    is(sWrite) {
      state := sHit
    }
  }

}
