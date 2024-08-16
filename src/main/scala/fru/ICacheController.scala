package wood.fru

import chisel3._
import chisel3.util._
import wood.{CorePort, MemPortR, WoodConfig}

object CacheState extends ChiselEnum {
  val init, idle, read, refill = Value
}

class ICacheController(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core  = new CorePort(config)
    val mem   = new MemPortR(config.copy(memDataWidth = config.dataWidth))
    val cache = Flipped(new SRAMInterface(config.icacheDepth, UInt((config.ivalidlen + config.itaglen + config.idatalen).W), 0, 0, 1))
  })

  // ---------------------------------------------------------------------------
  val corerequest = new Bundle() {
    val addr = io.core.req.bits.addr
    val idx  = addr(log2Ceil(config.icacheDepth) + config.byteOffset + config.bankOffset - 1, config.bankOffset + config.byteOffset)
    val tag  = addr(config.pcWidth - 1, log2Ceil(config.icacheDepth) + config.bankOffset + config.byteOffset)

    val addrReg = RegEnable(addr, 0.U, io.core.req.fire)
    val idxReg  = RegEnable(idx , 0.U, io.core.req.fire)
    val tagReg  = RegEnable(tag , 0.U, io.core.req.fire)
  }

  val cacheresponse = new Bundle() {
    val cacheline = io.cache.readwritePorts(0).readData

    val valid = cacheline(config.itaglen + config.idatalen + config.ivalidlen - 1)
    val tag   = cacheline(config.itaglen + config.idatalen - 1, config.idatalen)
    val data  = cacheline(config.idatalen - 1, 0)
  }

  val state = RegInit(CacheState.init)
  val isInit   = state === CacheState.init
  val isIdle   = state === CacheState.idle
  val isRead   = state === CacheState.read
  val isRefill = state === CacheState.refill

  // ---------------------------------------------------------------------------

  val (initIdx, initCompleted) = Counter(true.B, config.icacheDepth)

  val isHit = cacheresponse.valid && (cacheresponse.tag === corerequest.tagReg)

  val memRespData   = Cat(1.U, corerequest.tagReg, io.mem.resp.bits.data)
  val cacheInitData = 0.U

  val wen   = isInit || io.mem.resp.fire
  val wack  = RegNext(wen, init = false.B)
  val wdata = Mux(isInit, cacheInitData, memRespData)
  //waddr is not a very good choice as a name, it is not always waddr
  val waddr = Mux(isInit, initIdx, corerequest.idxReg)

  val cren = io.core.req.fire
  val mren = io.mem.req.fire

  io.core.req.ready      := isIdle || io.core.resp.fire
  io.core.resp.valid     := isRead && isHit
  io.core.resp.bits.data := cacheresponse.data

  io.cache.readwritePorts(0).address   := Mux(cren, corerequest.idx, waddr)
  io.cache.readwritePorts(0).enable    := true.B
  io.cache.readwritePorts(0).isWrite   := wen 
  io.cache.readwritePorts(0).writeData := wdata

  io.mem.req.valid     := isRead   && !isHit
  io.mem.resp.ready    := isRefill && !wack //TODO: i did it while watching dts in the background, it is propably wrong
  io.mem.req.bits.addr := corerequest.addrReg

  import CacheState._
  switch(state) {
    is(init) {
      when(initCompleted) {
        state := idle
      }
    }
    is(idle) {
      when(cren) {
        state := read
      }
    }
    is(read) {
      when(isHit) {
        when(io.core.resp.fire) {
          when(cren) {
            state := read
          }.otherwise {
            state := idle
          }
        }
      }.otherwise {
        when(mren) {
          state := refill
        }
      }
    }
    is(refill) {
      when(wack) {
        state := read
      }
    }
  }
}