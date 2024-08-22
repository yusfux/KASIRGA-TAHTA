package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus

class LSStoreQueueRow(config: WoodConfig) extends Module {
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

class LSStoreQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val camReadIn      = Input(UInt(config.xlen.W))
    val storeRetireBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val camReadOut     = Output(new LSCMI(config))
    val out            = Decoupled(new LSCMI(config))
  })

  val rows     = Seq.fill(config.lsSQDepth)(Module(new LSStoreQueueRow(config)))
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
  io.out.valid := !empty & outValid(deqPtr.value) & valid(deqPtr.value)

  when(io.flush) {
    valid.foreach(_ := 0.B)
    enqPtr.reset()
    deqPtr.reset()
  }

  (0 until config.lsSQDepth).foreach(j => {
    rows(j).io.flush          := io.flush
    rows(j).io.in.valid       := io.in.fire && (enqPtr.value === j.U) && !full
    rows(j).io.in.bits        := io.in.bits
    rows(j).io.storeRetireBus := io.storeRetireBus
    outValid(j)               := rows(j).io.out.valid && (deqPtr.value === j.U) && valid(deqPtr.value)
  })

  //********* CAM Logic ************//
  val recencyArray = VecInit(Seq.fill(config.lsSQDepth)(0.U))
  (0 until config.lsSQDepth).foreach(j => {
    recencyArray(j) := j.U - deqPtr.value
  })

  val addrMatch = VecInit(
    rows.map(row =>
      row.io.out.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex) ===
        io.camReadIn(config.xlen - 1, config.dCacheAddrStartIndex)
    )
  )

  val validAndMatch = VecInit(valid.zip(addrMatch).map { case (v, m) => v && m })

  val matchingEntries = validAndMatch.asUInt

  val (finalWstrobe, finalData) =
    (0 until config.lsSQDepth).foldLeft((0.U(config.numDCacheLineBytes.W), VecInit(Seq.fill(config.numDCacheLineBytes)(0.U(8.W))))) {
      case ((accWstrobe, accData), j) =>
        val currentWstrobe = rows(j).io.out.bits.wStrobe
        val currentData    = rows(j).io.out.bits.cacheLine
        val currentRecency = recencyArray(j)

        val newWstrobe = accWstrobe | (currentWstrobe.asUInt & Fill(config.numDCacheLineBytes, validAndMatch(j)))

        val moreRecentMatch = (0 until j).map(k => validAndMatch(k) && (recencyArray(k) < currentRecency)).foldLeft(false.B)(_ || _)

        val newData = VecInit((0 until config.numDCacheLineBytes).map { byteIndex =>
          val isCurrentByteValid = currentWstrobe(byteIndex)
          Mux(isCurrentByteValid && !moreRecentMatch, currentData(byteIndex), accData(byteIndex))
        })

        (newWstrobe, newData)
    }

  val tstrobe = Wire(Vec(config.numDCacheLineBytes, Bool()))
  tstrobe := VecInit(Seq.tabulate(config.numDCacheLineBytes)(j => finalWstrobe(j)))

  io.camReadOut           := DontCare
  io.camReadOut.wStrobe   := tstrobe
  io.camReadOut.cacheLine := finalData
}
