package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class LSDCacheWrapper(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSCMI(config)))
    val out = Decoupled(new LSCMI(config))
    val mem = new DCacheMemPort(config)
  })

  val dcache     = Module(new DCache(config))
  val getWStrobe = Module(new LSGetWriteStrobe(config))

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
  val shiftAmount        = io.in.bits.addr(numdCacheLineWords - 1, 2) * config.xlen.U
  val storeData          = (io.in.bits.cacheData.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0)
  val cacheMask          = ~((mask.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0))
  val storeDataMask      = ((mask.asUInt << shiftAmount)(config.dCacheLineWidth - 1, 0))
  val maskedStoreData    = (storeData & storeDataMask)

  val strobeShiftAmount = io.in.bits.addr(numdCacheLineWords - 1, 2) * (config.numBytes).U
  val storeMask         = (getWStrobe.io.out.asUInt << strobeShiftAmount)(config.numDCacheLineBytes - 1, 0)

  getWStrobe.io.addr := io.in.bits.addr
  getWStrobe.io.lsOp := io.in.bits.lsOp

  dcache.io.core.req.bits.lsOp  := io.in.bits.lsOp
  dcache.io.core.req.bits.tag   := io.in.bits.rdTag
  dcache.io.core.req.bits.addr  := io.in.bits.addr
  dcache.io.core.req.bits.data  := maskedStoreData
  dcache.io.core.req.bits.wen   := io.in.bits.store
  dcache.io.core.req.bits.wstrb := storeMask.asTypeOf(Vec(config.numDCacheLineBytes, Bool()))
  dcache.io.core.req.valid      := io.in.valid

  io.in.ready := dcache.io.core.req.ready

  io.out.valid              := dcache.io.core.resp.valid
  dcache.io.core.resp.ready := io.out.ready

  val outDataShiftAmount = dcache.io.core.resp.bits.addr(numdCacheLineWords - 1, 2) * config.xlen.U
  io.out.bits           := io.in.bits // debug only
  io.out.bits.cacheData := ((dcache.io.core.resp.bits.data >> outDataShiftAmount)(config.xlen - 1, 0)).asTypeOf(Vec(config.numBytes, UInt(8.W)))
  io.out.bits.rdTag     := dcache.io.core.resp.bits.tag
  io.out.bits.lsOp      := dcache.io.core.resp.bits.lsOp
  io.out.bits.addr      := dcache.io.core.resp.bits.addr

  dcache.io.mem <> io.mem

  // debug only
  dontTouch(mask)
  dontTouch(cacheMask)
  dontTouch(storeDataMask)
  dontTouch(shiftAmount)
  dontTouch(maskedStoreData)
  dontTouch(strobeShiftAmount)
  dontTouch(outDataShiftAmount)
  dontTouch(io.in.bits.pc) // debug only
  dontTouch(io.in.bits.inst) // debug only
}
