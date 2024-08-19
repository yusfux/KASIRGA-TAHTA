package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class LSReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(ValidIO(new LSMI(config)))
    val flush = Input(Bool())
    val lsBus = Flipped(Vec(config.nWide, ValidIO(new LSBus(config))))
    val out   = ValidIO(new LSMI(config))
  })

  val busRdMatches = Wire(Vec(config.nWide, Bool()))

  val rdTagReadyNext = Wire(Bool())
  val rowNext        = Wire(new LSMI(config))

  val row        = RegEnable(rowNext, 0.U.asTypeOf(new LSMI(config)), 1.B)
  val rdTagReady = RegEnable(rdTagReadyNext, 0.U, 1.B)

  val matchIndex = PriorityEncoder(busRdMatches)
  when(io.flush) {
    rowNext        := 0.U.asTypeOf(new LSMI(config))
    rdTagReadyNext := 0.U
  }.elsewhen(io.in.valid) {
    rowNext        := 0.U.asTypeOf(new LSMI(config))
    rdTagReadyNext := 0.U
    rowNext.rdTag  := io.in.bits.rdTag
  }.otherwise {
    val targetAddr = io.lsBus(matchIndex).bits.addr
    val tdata      = Wire(Vec(config.dataWidth / 8, UInt(8.W)))
    tdata := VecInit(Seq.tabulate(4)(j => io.lsBus(matchIndex).bits.data(8 * j + 7, 8 * j)))

    rowNext        := row
    rowNext.addr   := Mux(busRdMatches.asUInt.orR, targetAddr, row.addr)
    rowNext.data   := Mux(busRdMatches.asUInt.orR, tdata, row.data)
    rdTagReadyNext := rdTagReady | busRdMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := rdTagReady

  for (j <- 0 until config.nWide) {
    busRdMatches(j) := io.lsBus(j).valid & (row.rdTag === io.lsBus(j).bits.tag)
  }
}

class LSReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Decoupled(new LSMI(config)))
    val flush = Input(Bool())
    val lsBus = Flipped(Vec(config.nWide, ValidIO(new LSBus(config))))
    val out   = Decoupled(new LSMI(config))
  })

  val rows     = Seq.fill(config.rsDepth)(Module(new LSReservationStationRow(config)))
  val valid    = RegInit(VecInit(Seq.fill(config.rsDepth)(false.B)))
  val outValid = RegInit(VecInit(Seq.fill(config.rsDepth)(false.B)))
  val enqPtr   = Counter(config.rsDepth)
  val deqPtr   = Counter(config.rsDepth)
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

  rows.zipWithIndex.foreach {
    case (row, i) =>
      row.io.flush    := io.flush
      row.io.in.valid := io.in.fire && (enqPtr.value === i.U)
      row.io.in.bits  := io.in.bits
      row.io.lsBus    := io.lsBus
      outValid(i)     := row.io.out.valid && (enqPtr.value === i.U) && valid(deqPtr.value)
  }
}
