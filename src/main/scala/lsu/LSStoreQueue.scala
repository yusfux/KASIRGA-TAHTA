package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus

class LSStoreQueueRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(ValidIO(new LSCMI(config)))
    val setflushed     = Input(Bool())
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = ValidIO(new LSCMI(config))
  })

  val retireBusMatches = Wire(Vec(config.nWide, Bool()))

  val rowNext = Wire(new LSCMI(config))

  val row = RegEnable(rowNext, 0.U.asTypeOf(new LSCMI(config)), 1.B)

  val matchIndex = PriorityEncoder(retireBusMatches)
  when(io.setflushed && !(row.retired | retireBusMatches.asUInt.orR) && !io.in.valid) {
    rowNext         := DontCare
    rowNext.flushed := 1.B
    // debug only
    rowNext.inst := row.inst
    rowNext.pc   := row.pc
  }.elsewhen(io.setflushed && !(io.in.bits.retired) && io.in.valid) {
    rowNext         := DontCare
    rowNext.flushed := 1.B
    // debug only
    rowNext.inst := io.in.bits.inst
    rowNext.pc   := io.in.bits.pc
  }.elsewhen(io.setflushed & (io.in.valid & io.in.bits.retired)) {
    rowNext := io.in.bits
  }.elsewhen(io.setflushed & (row.retired | retireBusMatches.asUInt.orR)) {
    rowNext         := row
    rowNext.retired := row.retired | retireBusMatches.asUInt.orR
  }.elsewhen(io.in.valid) {
    rowNext := io.in.bits
  }.otherwise {
    rowNext         := row
    rowNext.retired := row.retired | retireBusMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := row.retired | row.flushed

  for (j <- 0 until config.nWide) {
    retireBusMatches(j) := io.storeRetireBus(j).valid & (row.rdTag === io.storeRetireBus(j).bits.tag)
  }
}

class LSStoreQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val camReadIn      = Input(UInt(config.xlen.W))
    val storeRetireBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val camReadOut     = Output(ValidIO(new LSCMI(config)))
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

  (0 until config.lsSQDepth).foreach(j => {
    rows(j).io.setflushed     := io.flush
    rows(j).io.in.valid       := io.in.fire && (enqPtr.value === j.U) && !full && !io.in.bits.flushed
    rows(j).io.in.bits        := io.in.bits
    rows(j).io.storeRetireBus := io.storeRetireBus
    outValid(j)               := rows(j).io.out.valid && (deqPtr.value === j.U) && valid(deqPtr.value)
  })

//********* CAM Logic ************//
  val recencyArray = VecInit(
    Seq.tabulate(config.lsSQDepth)(j => (config.lsSQDepth - 1).U - ((j.U + config.lsSQDepth.U - enqPtr.value) % config.lsSQDepth.U))
  )

  val addrMatch = VecInit(
    rows.map(row => row.io.out.bits.addr(config.xlen - 1, 2) === io.camReadIn(config.xlen - 1, 2))
  )

  val validAndMatch = VecInit(valid.zip(addrMatch).map { case (v, m) => v && m })

  val matchingEntries = validAndMatch.asUInt

  val (finalWstrobe, finalData) = (0 until config.lsSQDepth).foldRight((0.U(config.numBytes.W), VecInit(Seq.fill(config.numBytes)(0.U(8.W))))) {
    case (j, (accWstrobe, accData)) =>
      val currentWstrobe = rows(j).io.out.bits.sqwStrobe.asUInt & Fill(config.numBytes, !rows(j).io.out.bits.flushed)
      val currentData    = rows(j).io.out.bits.cacheData

      val newWstrobe = accWstrobe | (currentWstrobe & Fill(config.numBytes, validAndMatch(j)))
      val newData = VecInit((0 until config.numBytes).map { byteIndex =>
        Mux(validAndMatch(j) && currentWstrobe(byteIndex), currentData(byteIndex), accData(byteIndex))
      })

      (newWstrobe, newData)
  }

  val tstrobe = Wire(Vec(config.numBytes, Bool()))
  tstrobe := VecInit(Seq.tabulate(config.numBytes)(j => finalWstrobe(j)))

// debug only
  dontTouch(tstrobe)
  dontTouch(addrMatch)
  dontTouch(validAndMatch)
  dontTouch(recencyArray)
  dontTouch(valid)
  dontTouch(deqPtr.value)
  dontTouch(enqPtr.value)

  io.camReadOut                := DontCare
  io.camReadOut.bits.sqwStrobe := tstrobe
  io.camReadOut.bits.sqData    := finalData
  io.camReadOut.valid          := io.in.valid
}
