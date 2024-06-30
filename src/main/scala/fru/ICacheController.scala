package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{ReadPortI, ReadPortO, WritePortI}

class ICacheControllerIO(c: CacheParams) extends Bundle {
  val core = new Bundle() {
    val req = Flipped(DecoupledIO(new ReadPortI(UInt(c.coreDataWidth.W))(c.coreAddrWidth)))
    val resp = DecoupledIO(new ReadPortO(UInt(c.coreDataWidth.W))(c.coreAddrWidth))
  }

  val cache = new Bundle() {
    val req = new Bundle() {
      val read = DecoupledIO(new ReadPortI(UInt(c.cacheDataWidth.W))(c.cacheAddrWidth))
      val write = DecoupledIO(new WritePortI(UInt(c.cacheDataWidth.W))(c.cacheAddrWidth))
    }
    val resp = Flipped(DecoupledIO(new ReadPortO(UInt((c.cacheDataWidth).W))(c.cacheAddrWidth)))
  }

  val mem = new Bundle() {
    val req = DecoupledIO(new ReadPortI(UInt(c.memDataWidth.W))(c.memAddrWidth))
    val resp = Flipped(DecoupledIO(new ReadPortO(UInt(c.memDataWidth.W))(c.memAddrWidth)))
  }
}

object CacheState extends ChiselEnum {
  val idle, readCache, waitCache, readMem, waitMem, refill, waitCore = Value
}

case class CacheParams(
    coreAddrWidth:  Int,
    memAddrWidth:   Int,
    cacheAddrWidth: Int,
    coreDataWidth:  Int,
    memDataWidth:   Int,
    cacheDataWidth: Int
)

class ICacheController(config: WoodConfig) extends Module {

  val xlen:      Int = config.xlen
  val addrwidth: Int = config.addrWidth
  val datawidth: Int = config.dataWidth
  val depth:     Int = config.iCacheDepth

  val lineSelectBits   = log2Ceil(depth)
  val instSelectOffset = log2Ceil(xlen >> 3)

  val taglen   = xlen - (lineSelectBits + instSelectOffset)
  val datalen  = datawidth
  val validlen = 1

  val io = IO(new ICacheControllerIO(new CacheParams(
      coreAddrWidth  = addrwidth,
      memAddrWidth   = addrwidth,
      cacheAddrWidth = log2Ceil(depth),
      coreDataWidth  = datawidth,
      memDataWidth   = datawidth,
      cacheDataWidth = validlen + taglen + datalen
  )))

  // BUNDLE DECLERATIONS -------------------------------------------------------
  // below lines are just for ease of use, they do not make any differences on 
  // the architectural level, just syntatix sugars (except registers in corerequest)
  val dataout = RegInit(0.U(datawidth.W))

  val corerequest = new Bundle() {
    val ack  = io.core.req.fire
    val addr = io.core.req.bits.addr
    val idx  = addr(instSelectOffset + lineSelectBits - 1, instSelectOffset)
    val tag  = addr(xlen - 1, instSelectOffset + lineSelectBits)

    val addrReg = RegEnable(addr, ack)
    val tagReg = RegEnable(tag, ack)
    val idxReg = RegEnable(idx, ack)
  }

  val coreresponse = new Bundle() {
    val ack = io.core.resp.fire
    val data = dataout
  }

  val memrequest = new Bundle() {
    val ack = io.mem.req.fire
    val addr = corerequest.addrReg
  }

  val memresponse = new Bundle() {
    val ack = io.mem.resp.fire
    val data = io.mem.resp.bits.data
    val dataReg = RegEnable(data, ack)
  }

  val cacherequest = new Bundle() {
    val read = new Bundle() {
      val ack = io.cache.req.read.fire
      val addr = corerequest.idxReg
      val tag = corerequest.tagReg
    }

    val write = new Bundle() {
      val ack = io.cache.req.write.fire
      val addr = corerequest.idxReg
      val data = Cat(1.U, Cat(corerequest.tagReg, memresponse.dataReg))
    }
  }

  val cacheresponse = new Bundle() {
    val ack = io.cache.resp.fire
    val valid = io.cache.resp.bits.data(taglen + datalen)
    val tag = io.cache.resp.bits.data(taglen + datalen - 1, datalen)
    val data = io.cache.resp.bits.data(datalen - 1, 0)
  }
  // BUNDLE DECLERATIONS -------------------------------------------------------

  val state = RegInit(CacheState.idle)
  val isIdle       = state === CacheState.idle
  val isWaitCore   = state === CacheState.waitCore
  val isReadCache  = state === CacheState.readCache
  val isRefill     = state === CacheState.refill
  val isWaitCache  = state === CacheState.waitCache
  val isReadMem    = state === CacheState.readMem
  val isWaitMem    = state === CacheState.waitMem

  val isHit = cacheresponse.valid && cacheresponse.tag === cacherequest.read.tag
  when(cacheresponse.ack && isHit) {
    dataout := cacheresponse.data
  }.elsewhen(memresponse.ack) {
    dataout := memresponse.data
  }

  //TODO: if we also give the response to the core without waiting to refill, it
  // would also let us free from couple of cycles
  io.core.req.ready := isIdle
  io.core.resp.valid := isWaitCore || (cacheresponse.ack && isHit)
  io.core.resp.bits.data := dataout
  io.core.resp.bits.addr := corerequest.addrReg

  io.cache.req.read.valid := isReadCache
  io.cache.req.read.bits.addr := cacherequest.read.addr
  io.cache.req.write.valid := isRefill
  io.cache.req.write.bits.enable := isRefill
  io.cache.req.write.bits.addr := cacherequest.write.addr
  io.cache.req.write.bits.data := cacherequest.write.data
  io.cache.resp.ready := isWaitCache

  io.mem.req.valid := isReadMem
  io.mem.req.bits.addr := memrequest.addr
  io.mem.resp.ready := isWaitMem

  /* 
   * it was all fun and simple in its initial form without having any nested when
   * statements but this let us be free of couple of cycles we've spent due to handshaking.
   * 
   * it may violate some of the principles that comes with the handshaking but i believe
   * it is safe for now, may need to refactor tho
   */
  switch(state) {
    is(CacheState.idle) {
      when(corerequest.ack) {
        state := CacheState.readCache
      }
    }
    is(CacheState.readCache) {
      when(cacherequest.read.ack) {
        state := CacheState.waitCache
      }
    }
    is(CacheState.waitCache) {
      when(cacheresponse.ack) {
        when(isHit) {
          when(coreresponse.ack) {
            state := CacheState.idle
          }.otherwise {
            state := CacheState.waitCore
          }
        }.otherwise {
          state := CacheState.readMem
        }
      }
    }
    is(CacheState.readMem) {
      when(memrequest.ack) {
        state := CacheState.waitMem
      }
    }
    is(CacheState.waitMem) {
      when(memresponse.ack) {
        state := CacheState.refill
      }
    }
    is(CacheState.refill) {
      when(cacherequest.write.ack) {
        state := CacheState.waitCore
      }
    }
    is(CacheState.waitCore) {
      when(coreresponse.ack) {
        state := CacheState.idle
      }
    }
  }
}