package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{ReadPortI, ReadPortO}

class ICacheControllerIO(config: WoodConfig) extends Bundle {
  val core = new Bundle() {
    val req = Flipped(DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth)))
    val resp = DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth))
  }

  val cache = Flipped(new SRAMInterface(config.icacheDepth, UInt((config.ivalidlen + config.itaglen + config.idatalen).W), 0, 0, 1))

  val mem = new Bundle() {
    val req = DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth))
    val resp = Flipped(DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth)))
  }
}

object CacheState extends ChiselEnum {
  val init, idle, read, refill = Value
}

class ICacheController(config: WoodConfig) extends Module {
  val pcWidth = config.pcWidth
  val depth = config.icacheDepth

  val depthWidth  = log2Ceil(depth)
  val offsetWidth = config.byteOffset + config.bankOffset

  val taglen   = config.itaglen
  val datalen  = config.idatalen
  val validlen = config.ivalidlen

  val io = IO(new ICacheControllerIO(config))

  // ---------------------------------------------------------------------------
  val corerequest = new Bundle() {
    val addr = io.core.req.bits.addr
    val idx  = addr(offsetWidth + depthWidth - 1, offsetWidth)
    val tag  = addr(pcWidth - 1, offsetWidth + depthWidth)

    val addrReg = RegEnable(addr, io.core.req.fire)
    val idxReg  = RegEnable(idx, io.core.req.fire)
    val tagReg  = RegEnable(tag, io.core.req.fire)
  }

  val cacheresponse = new Bundle() {
    val cacheline = io.cache.readwritePorts(0).readData

    val valid = cacheline(taglen + datalen + validlen - 1)
    val tag = cacheline(taglen + datalen - 1, datalen)
    val data = cacheline(datalen - 1, 0)
  }

  val state = RegInit(CacheState.init)
  val isInit   = state === CacheState.init
  val isIdle   = state === CacheState.idle
  val isRead   = state === CacheState.read
  val isRefill = state === CacheState.refill

  // ---------------------------------------------------------------------------

  val (initIdx, initCompleted) = Counter(true.B, depth)

  val isHit = cacheresponse.valid && (cacheresponse.tag === corerequest.tagReg)

  val memData  = Cat(1.U, corerequest.tagReg, io.mem.resp.bits.data)
  val initData = 0.U

  val wen   = isInit || io.mem.resp.fire
  val wack  = RegNext(wen)
  val wdata = Mux(isInit, initData, memData)
  //waddr is not a very good choice as a name, it is not always waddr
  val waddr = Mux(isInit, initIdx, corerequest.idxReg)

  val cren = io.core.req.fire
  val mren = io.mem.req.fire

  io.core.req.ready      := isIdle || io.core.resp.fire
  io.core.resp.valid     := isRead && isHit
  io.core.resp.bits.data := cacheresponse.data
  io.core.resp.bits.addr := corerequest.addrReg

  io.cache.readwritePorts(0).address   := Mux(cren, corerequest.idx, waddr)
  io.cache.readwritePorts(0).enable    := (cren || wack) || wen
  io.cache.readwritePorts(0).isWrite   := wen 
  io.cache.readwritePorts(0).writeData := wdata

  io.mem.req.valid     := isRead && !isHit
  io.mem.req.bits.addr := corerequest.addrReg
  io.mem.resp.ready    := isRefill && !wack //TODO: i did it while watching dts in the background, it is propably wrong

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