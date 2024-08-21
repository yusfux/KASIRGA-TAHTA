package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.DCPipelineRegister

class StoreQueueRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(ValidIO(new LSCMI(config)))
    val flush          = Input(Bool())
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = ValidIO(new LSCMI(config))
  })

  val busRdMatches = Wire(Vec(config.nWide, Bool()))
  val rowNext      = Wire(new LSCMI(config))

  val row = RegEnable(rowNext, 0.U.asTypeOf(new LSCMI(config)), 1.B)

  val matchIndex = PriorityEncoder(busRdMatches)
  when(io.flush) {
    rowNext := 0.U.asTypeOf(new LSCMI(config))
  }.elsewhen(io.in.valid) {
    rowNext := io.in.bits
  }.otherwise {
    rowNext         := row
    rowNext.retired := row.retired | busRdMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := row.retired

  for (j <- 0 until config.nWide) {
    busRdMatches(j) := io.storeRetireBus(j).valid & (row.rdTag === io.storeRetireBus(j).bits.tag)
  }
}

class StoreQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val camReadIn      = Input(UInt(config.xlen.W))
    val storeRetireBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val camReadOut     = Output(new LSCMI(config))
    val out            = Decoupled(new LSCMI(config))
  })

  val rows     = Seq.fill(config.lsSQDepth)(Module(new StoreQueueRow(config)))
  val valid    = RegInit(VecInit(Seq.fill(config.lsSQDepth)(false.B)))
  val outValid = RegInit(VecInit(Seq.fill(config.lsSQDepth)(false.B)))
  val enqPtr   = Counter(config.lsSQDepth)
  val deqPtr   = Counter(config.lsSQDepth)
  val empty    = enqPtr.value === deqPtr.value && !valid(deqPtr.value)
  val full     = enqPtr.value === deqPtr.value && valid(deqPtr.value)

  val rowBits = VecInit(rows.map(_.io.out.bits))

  io.out.bits := Mux1H(UIntToOH(deqPtr.value), rowBits)

  when(io.in.fire) {
    valid(enqPtr.value) := true.B
    enqPtr.inc()
  }

  when(io.out.fire) {
    valid(deqPtr.value) := false.B
    deqPtr.inc()
  }

  io.in.ready  := !full
  io.out.valid := !empty

  when(io.flush) {
    valid.foreach(_ := 0.B)
    enqPtr.reset()
    deqPtr.reset()
  }

  (0 until config.lsSQDepth).foreach(j => {
    rows(j).io.flush          := io.flush
    rows(j).io.in.valid       := io.in.fire && (enqPtr.value === j.U)
    rows(j).io.in.bits        := io.in.bits
    rows(j).io.storeRetireBus := io.storeRetireBus
    outValid(j)               := rows(j).io.out.valid && (deqPtr.value === j.U) && valid(deqPtr.value)
  })

  //********* CAM Logic ************//
  val recencyArray = VecInit(Seq.fill(config.lsSQDepth)(0.U))
  (0 until config.lsSQDepth).foreach(j => {
    recencyArray(j) := j.U - deqPtr.value
  })

  val (finalWstrobe, finalData) =
    (0 until config.lsSQDepth).foldLeft((0.U(config.numDCacheLineBytes.W), VecInit(Seq.fill(config.numDCacheLineBytes)(0.U(8.W))))) {
      case ((accWstrobe, accData), j) =>
        val currentWstrobe = rows(j).io.out.bits.wStrobe
        val currentData    = rows(j).io.out.bits.cacheLine
        val currentRecency = recencyArray(j)

        val newWstrobe = accWstrobe | (currentWstrobe.asUInt & Fill(config.numDCacheLineBytes, valid(j)))

        val addrMatch = VecInit(Seq.fill(config.lsSQDepth)(0.B))
        addrMatch.zipWithIndex.foreach {
          case (row, j) => {
            val rowAddr = rows(j).io.out.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
            val camAddr = io.camReadIn(config.xlen - 1, config.dCacheAddrStartIndex)
            addrMatch(j) := rowAddr === camAddr
          }
        }

        val wstrobeValid = VecInit(rows.map(_.io.out.bits.wStrobe))

        val recencyCompare = VecInit(Seq.fill(config.lsSQDepth)(0.B))
        recencyCompare.zipWithIndex.foreach {
          case (row, j) =>
            recencyCompare(j) := currentRecency > recencyArray(j)
        }

        val newData = VecInit(Seq.fill(config.numDCacheLineBytes)(0.U(8.W)))
        newData := (0 until config.numDCacheLineBytes).map { byteIndex =>
          val isCurrentByteValid = currentWstrobe(byteIndex)

          val moreRecentValid = (0 until j).map(k => addrMatch(k) && wstrobeValid(k)(byteIndex) && recencyCompare(k)).foldLeft(true.B)(_ && _)

          val selectedData =
            Mux(isCurrentByteValid && moreRecentValid, currentData(byteIndex), accData(byteIndex))

          selectedData
        }

        (newWstrobe, newData)
    }

  val tstrobe = Wire(Vec(config.numDCacheLineBytes, Bool()))
  tstrobe := VecInit(Seq.tabulate(config.numDCacheLineBytes)(j => finalWstrobe(j)))

  io.camReadOut           := DontCare
  io.camReadOut.wStrobe   := tstrobe
  io.camReadOut.cacheLine := finalData
}

class LSDCache0Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val lsAtomBus      = Flipped(DecoupledIO(new LSCMI(config)))
    val lsDCache1Bus   = Flipped(ValidIO(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val outCache       = Decoupled(new LSCMI(config)) // comb out
    val outPass        = Decoupled(new LSCMI(config))
  })

  val pReg            = Module(new DCPipelineRegister(new LSCMI(config))(1))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val arbiter         = Module(new Arbiter(new LSCMI(config), 2))
  val sq              = Module(new StoreQueue(config))
  val selfPass        = Wire(Decoupled(new LSCMI(config)))

  retireOverrider.io.storeRetireBus <> io.storeRetireBus

  retireOverrider.io.in <> io.in
  selfPass              <> retireOverrider.io.out
  sq.io.camReadIn       := io.in.bits.addr
  sq.io.flush           := io.flush
  sq.io.in.valid        := io.in.fire
  sq.io.storeRetireBus  := io.storeRetireBus
  sq.io.in              <> io.lsAtomBus

  (0 until config.numDCacheLineBytes).foreach(j => {
    val selfAddr    = io.in.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
    val atomAddr    = io.lsAtomBus.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
    val dcache1Addr = io.lsAtomBus.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)

    val atomByteUpdate    = io.lsAtomBus.bits.wStrobe(j) && (atomAddr === selfAddr)
    val dcache1ByteUpdate = io.lsDCache1Bus.bits.wStrobe(j) && (dcache1Addr === selfAddr)

    selfPass.bits.cacheLine(j) := MuxCase(
      io.in.bits.cacheLine(j),
      Array(
        (io.in.bits.wStrobe(j))       -> io.in.bits.cacheLine(j),
        (dcache1ByteUpdate)           -> io.lsDCache1Bus.bits.cacheLine(j),
        (atomByteUpdate)              -> io.lsAtomBus.bits.cacheLine(j),
        (sq.io.camReadOut.wStrobe(j)) -> sq.io.camReadOut.cacheLine(j)
      ).toIndexedSeq
    )
    selfPass.bits.wStrobe(j) := io.in.bits.wStrobe(j) | atomByteUpdate | dcache1ByteUpdate
  })

  io.in.ready := io.outPass.ready & io.outCache.ready & (arbiter.io.chosen === 1.U)

  arbiter.io.in(0) <> sq.io.out
  arbiter.io.in(1) <> io.in
  io.outCache      <> arbiter.io.out

  pReg.io.flush     := io.flush
  pReg.io.valids(0) := io.in.valid

  pReg.io.in <> selfPass
  io.outPass <> pReg.io.out
}
