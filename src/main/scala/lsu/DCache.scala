package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig

class DCacheMemPort(config: WoodConfig) extends Bundle {
  val req = DecoupledIO(new Bundle {
    val addr = UInt(32.W)
    val data = UInt(config.mmInterfaceWidth.W)
    val wen  = Bool()
  })
  val resp = Flipped(DecoupledIO(new Bundle {
    val data = UInt(config.mmInterfaceWidth.W)
  }))
}

class DCache(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = new Bundle {
      val req = Flipped(DecoupledIO(new Bundle {
        val addr  = UInt(32.W)
        val tag   = UInt(config.tagWidth.W)
        val data  = UInt(config.ddatalen.W)
        val wstrb = Vec(config.ddatalen / 8, Bool())
        val wen   = Bool()
        val lsOp  = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
      }))
      val resp = DecoupledIO(new Bundle {
        val data = UInt(config.ddatalen.W)
        val tag  = UInt(config.tagWidth.W)
        val addr = UInt(32.W)
        val lsOp = UInt(DecodeConfig.subWidths(DecodeConfig.lsOpIdx).W)
      })
    }

    val mem = new DCacheMemPort(config)
  })

  val dcachebank = for (i <- 0 until config.dcachewaycount) yield {
    val dcache = SRAM(config.dcacheDepth, UInt(config.ddatalen.W), 0, 0, 1)
    dcache
  }

  val metadata = for (i <- 0 until 4) yield {
    new Bundle {
      val tagbank = SRAM(config.dcacheDepth, UInt(config.dtaglen.W), 0, 0, 1)
      val dirty   = RegInit(VecInit(Seq.fill(config.dcacheDepth)(false.B)))
      val valid   = RegInit(VecInit(Seq.fill(config.dcacheDepth)(false.B)))
    }
  }

  val dcachebankio = VecInit(dcachebank.map(_.readwritePorts(0)))
  val tagbankio    = VecInit(metadata.map(_.tagbank.readwritePorts(0)))

  object CacheState extends ChiselEnum {
    val idle, access, memWrite, memRead, response = Value
  }

  val state      = RegInit(CacheState.idle)
  val isIdle     = state === CacheState.idle
  val isAccess   = state === CacheState.access
  val isMemWrite = state === CacheState.memWrite
  val isMemRead  = state === CacheState.memRead
  val isResponse = state === CacheState.response

  val lsOpReg  = RegEnable(io.core.req.bits.lsOp, 0.U, io.core.req.fire)
  val rdTagReg = RegEnable(io.core.req.bits.tag, 0.U, io.core.req.fire)
  val addrReg  = RegEnable(io.core.req.bits.addr, 0.U, io.core.req.fire)
  val wenReg   = RegEnable(io.core.req.bits.wen, false.B, io.core.req.fire)
  val wstrbReg = RegEnable(io.core.req.bits.wstrb, VecInit(Seq.fill(16)(false.B)), io.core.req.fire)
  val wdataReg = RegEnable(io.core.req.bits.data, 0.U, io.core.req.fire)
  val tagReg   = RegEnable(io.core.req.bits.addr(32 - 1, log2Ceil(config.dcacheDepth) + log2Ceil(config.ddatalen / 8)), 0.U, io.core.req.fire)
  val idxReg = RegEnable(
    io.core.req.bits.addr(log2Ceil(config.dcacheDepth) + log2Ceil(config.ddatalen / 8) - 1, log2Ceil(config.ddatalen / 8)),
    0.U,
    io.core.req.fire
  )

  val idx    = io.core.req.bits.addr(log2Ceil(config.dcacheDepth) + log2Ceil(config.ddatalen / 8) - 1, log2Ceil(config.ddatalen / 8))
  val wrfull = wstrbReg.reduce(_ && _)

  val dataList  = WireInit(VecInit(Seq.fill(4)(0.U(config.ddatalen.W))))
  val tagList   = WireInit(VecInit(Seq.fill(4)(0.U(config.dtaglen.W))))
  val validList = WireInit(VecInit(Seq.fill(4)(false.B)))
  val dirtyList = WireInit(VecInit(Seq.fill(4)(false.B)))
  val matchList = WireInit(VecInit(Seq.fill(4)(false.B)))

  dataList.zip(dcachebankio).map { case (d, b) => d := b.readData }
  tagList.zip(tagbankio).map { case (t, m) => t := m.readData }
  validList.zip(metadata).map { case (v, m) => v := m.valid(idxReg) }
  dirtyList.zip(metadata).map { case (d, m) => d := m.dirty(idxReg) }
  matchList.zip(tagList.zip(validList).map { case (t, v) => t === tagReg && v }).map { case (m, c) => m := c } //:DD

  val plru0 = RegInit(VecInit(Seq.fill(config.dcacheDepth)(true.B)))
  val plru1 = RegInit(VecInit(Seq.fill(config.dcacheDepth)(true.B)))
  val plru2 = RegInit(VecInit(Seq.fill(config.dcacheDepth)(true.B)))

  def updatePlruTree(idx: UInt, way: UInt): Unit = {
    plru0(idx) := way(1)
    when(way(1) === 0.U) {
      plru1(idx) := way(0)
    }.otherwise {
      plru2(idx) := way(0)
    }
  }

  val hitWay       = OHToUInt(matchList)
  val victimUpdate = Cat(~plru0(idxReg), Mux(plru0(idxReg) === 0.U, ~plru2(idxReg), ~plru1(idxReg)))
  val victimWay    = RegEnable(victimUpdate, 0.U, io.core.req.fire)
  val victimTag    = tagList(victimWay)
  val victimData   = dataList(victimWay)

  val isHit   = matchList.reduce(_ || _)
  val isWrite = wenReg
  val isDirty = dirtyList(victimWay)
  val isValid = validList(victimWay)

  val memData = RegEnable(io.mem.resp.bits.data, 0.U, io.mem.resp.fire)

  val updatedData = VecInit(Seq.tabulate(16) { i =>
    Mux(wstrbReg(i), wdataReg(8 * i + 7, 8 * i), dataList(hitWay)(8 * i + 7, 8 * i))
  })
  val updatedMemData = VecInit(Seq.tabulate(16) { i =>
    Mux(wstrbReg(i), wdataReg(8 * i + 7, 8 * i), io.mem.resp.bits.data(8 * i + 7, 8 * i))
  })

  val cacheAddr        = Mux(io.core.req.fire, idx, idxReg)
  val cacheWriteHit    = (isAccess && isHit && isWrite)
  val cacheWriteVictim = (io.mem.req.fire && isWrite) || (io.mem.resp.fire && ~isWrite)
  val cacheWriteFirst  = (isAccess && ~isHit && ~isDirty && isWrite)
  val cacheRead        = io.core.req.fire
  val cacheData =
    Mux(io.mem.resp.fire && ~isWrite, io.mem.resp.bits.data, Mux(io.mem.resp.fire && isWrite && isMemRead, updatedMemData.asUInt, updatedData.asUInt))

  val tagAddr        = Mux(io.core.req.fire, idx, idxReg)
  val tagWriteHit    = (isAccess && isHit && isWrite)
  val tagWriteVictim = (io.mem.req.fire && isWrite) || (io.mem.resp.fire && ~isWrite)
  val tagWriteFirst  = (isAccess && ~isHit && ~isDirty && isWrite)
  val tagRead        = io.core.req.fire
  val tagData        = tagReg

  (0 until 4).foreach { i =>
    dcachebankio(i).address   := cacheAddr
    dcachebankio(i).writeData := cacheData
    dcachebankio(i).enable    := cacheRead || (cacheWriteHit && i.U === hitWay) || ((cacheWriteVictim || cacheWriteFirst) && i.U === victimWay)
    dcachebankio(i).isWrite   := (cacheWriteHit && i.U === hitWay) || ((cacheWriteVictim || cacheWriteFirst) && i.U === victimWay)

    tagbankio(i).address   := tagAddr
    tagbankio(i).writeData := tagData
    tagbankio(i).enable    := tagRead || (tagWriteHit && i.U === hitWay) || ((tagWriteVictim || tagWriteFirst) && i.U === victimWay)
    tagbankio(i).isWrite   := (tagWriteHit && i.U === hitWay) || ((tagWriteVictim || tagWriteFirst) && i.U === victimWay)
  }

  (0 until 4).foreach { i =>
    when(cacheWriteHit && i.U === hitWay) {
      metadata(i).dirty(idxReg) := true.B
    }
    when(cacheWriteVictim && i.U === victimWay) {
      when(io.mem.resp.fire && ~isWrite) {
        metadata(i).dirty(idxReg) := false.B
      }.otherwise {
        metadata(i).dirty(idxReg) := true.B
      }
    }
    when(cacheWriteFirst && i.U === victimWay) {
      metadata(i).dirty(idxReg) := true.B
      metadata(i).valid(idxReg) := true.B
    }
  }

  import CacheState._
  switch(state) {
    is(idle) {
      when(io.core.req.fire) {
        state := access
      }
    }
    is(access) {
      when(isHit) {
        updatePlruTree(idxReg, hitWay)
        when(isWrite) {
          state := idle
        }.otherwise {
          state := response
        }
      }.otherwise {
        updatePlruTree(idxReg, victimUpdate)
        when(isWrite) {
          state := Mux(isDirty, memWrite, Mux(wrfull, idle, memRead))
        }.otherwise {
          state := Mux(isDirty, memWrite, memRead)
        }
      }
    }
    is(memWrite) {
      when(io.mem.req.fire) {
        state := Mux(isWrite && wrfull, idle, memRead)
      }
    }
    is(memRead) {
      when(io.mem.resp.fire) {
        state := Mux(isWrite, idle, response)
      }
    }
    is(response) {
      when(io.core.resp.fire) {
        state := idle
      }
    }
  }

  io.core.req.ready      := isIdle
  io.core.resp.bits.data := Mux(isHit, dataList(hitWay), memData)
  io.core.resp.bits.tag  := rdTagReg
  io.core.resp.bits.addr := addrReg
  io.core.resp.bits.lsOp := lsOpReg

  io.core.resp.valid := isResponse

  io.mem.req.valid     := isMemWrite || isMemRead
  io.mem.req.bits.addr := Mux(isMemWrite, Cat(victimTag, idxReg, 0.U(4.W)), Cat(tagReg, idxReg, 0.U(4.W)))
  io.mem.req.bits.data := victimData
  io.mem.req.bits.wen  := isMemWrite
  io.mem.resp.ready    := isMemRead

}
