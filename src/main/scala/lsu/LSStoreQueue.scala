package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.{DCDemux, DCRightShifter}

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

  val rows        = Seq.fill(config.lsSQDepth)(Module(new LSStoreQueueRow(config)))
  val demux       = Module(new DCDemux(new LSCMI(config))(1, config.lsSQDepth))
  val camShifter  = Module(new DCRightShifter(new LSCMI(config))(config.lsSQDepth))
  val camReversed = Wire(Vec(config.lsSQDepth, ValidIO(new LSCMI(config))))

  val valid    = RegInit(VecInit(Seq.fill(config.lsSQDepth)(false.B)))
  val outValid = RegInit(VecInit(Seq.fill(config.lsSQDepth)(false.B)))
  val enqPtr   = Counter(config.lsSQDepth)
  val deqPtr   = Counter(config.lsSQDepth)
  val empty    = enqPtr.value === deqPtr.value && !valid(deqPtr.value)
  val full     = enqPtr.value === deqPtr.value && valid(deqPtr.value)

  val rowBits   = VecInit(rows.map(_.io.out.bits))
  val rowValids = VecInit(rows.map(_.io.out.valid))
  io.out.bits := Mux1H(UIntToOH(deqPtr.value), rowBits)
  // io.out.valid := Mux1H(UIntToOH(deqPtr.value), rowValids)

  when(io.in.fire && io.in.valid && !io.in.bits.flushed) {
    valid(enqPtr.value) := true.B
    enqPtr.inc()
  }

  when((rowBits(deqPtr.value).flushed && valid(deqPtr.value)) || (rowValids(deqPtr.value) && valid(deqPtr.value) && io.out.ready)) {
    valid(deqPtr.value) := false.B
    deqPtr.inc()
  }

  io.out.valid := !empty && rowValids(deqPtr.value) && valid(deqPtr.value) && io.out.ready

  demux.io.in(0)  <> io.in
  demux.io.sel(0) := enqPtr.value

  (0 until config.lsSQDepth).foreach(j => {
    demux.io.out(j)(0).ready  := (enqPtr.value === j.U) && !full && !valid(enqPtr.value)
    rows(j).io.in.bits        := demux.io.out(j)(0).bits
    rows(j).io.in.valid       := demux.io.out(j)(0).valid && !io.in.bits.flushed
    rows(j).io.setflushed     := io.flush
    rows(j).io.storeRetireBus := io.storeRetireBus
  })

  //********* CAM Logic ************//
  camShifter.io.shamt := deqPtr.value
  (0 until config.lsSQDepth).foreach(j => {
    camShifter.io.in(j).bits   := rowBits(j)
    camShifter.io.in(j).valid  := valid(j) && (rowBits(j).addr(config.xlen - 1, 2) === io.camReadIn(config.xlen - 1, 2))
    camShifter.io.out(j).ready := DontCare
    camReversed(j).bits        := camShifter.io.out(config.lsSQDepth - 1 - j).bits
    camReversed(j).valid       := camShifter.io.out(config.lsSQDepth - 1 - j).valid
  })

  val (finalWstrobe, finalData) = (0 until config.lsSQDepth).foldRight((0.U(config.numBytes.W), VecInit(Seq.fill(config.numBytes)(0.U(8.W))))) {
    case (j, (accWstrobe, accData)) =>
      val currentWstrobe = camReversed(j).bits.sqwStrobe.asUInt & Fill(config.numBytes, camReversed(j).valid)
      val currentData    = camReversed(j).bits.cacheData

      val newWstrobe = accWstrobe | currentWstrobe
      val newData = VecInit((0 until config.numBytes).map { byteIndex =>
        Mux(currentWstrobe(byteIndex), currentData(byteIndex), accData(byteIndex))
      })

      (newWstrobe, newData)
  }

  val tstrobe = Wire(Vec(config.numBytes, Bool()))
  tstrobe := VecInit(Seq.tabulate(config.numBytes)(j => finalWstrobe(j)))

// debug only
  dontTouch(tstrobe)
  dontTouch(valid)
  dontTouch(deqPtr.value)
  dontTouch(enqPtr.value)

  io.camReadOut                := DontCare
  io.camReadOut.bits.sqwStrobe := tstrobe
  io.camReadOut.bits.sqData    := finalData
  io.camReadOut.valid          := io.in.valid
}
