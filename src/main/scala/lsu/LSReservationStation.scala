package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus

class LSReservationStationRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(ValidIO(new LSRSMI(config)))
    val flush          = Input(Bool())
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = ValidIO(new LSRSMI(config))
  })

  val operandBusMatches = Wire(Vec(config.nWide, Bool()))
  val retireBusMatches  = Wire(Vec(config.nWide, Bool()))

  val rowNext          = Wire(new LSRSMI(config))
  val operandReadyNext = Wire(Bool())

  val row          = RegEnable(rowNext, 0.U.asTypeOf(new LSRSMI(config)), 1.B)
  val operandReady = RegEnable(operandReadyNext, 0.U, 1.B)

  val operandBusMatchIndex = PriorityEncoder(operandBusMatches)
  val retireBusMatchIndex  = PriorityEncoder(retireBusMatches)

  when(io.flush) {
    rowNext          := 0.U.asTypeOf(new LSRSMI(config))
    operandReadyNext := 0.U
  }.elsewhen(io.in.valid) {
    rowNext          := 0.U.asTypeOf(new LSRSMI(config))
    operandReadyNext := 0.U
    rowNext          := io.in.bits
  }.otherwise {
    val operandTargetAddr = io.lsOperandBus(operandBusMatchIndex).bits.targetAddr
    val operandRs2        = io.lsOperandBus(operandBusMatchIndex).bits.rs2Data

    rowNext          := row
    rowNext.addr     := Mux(operandBusMatches.asUInt.orR, operandTargetAddr, row.addr)
    rowNext.rs2Data  := Mux(operandBusMatches.asUInt.orR, operandRs2, row.rs2Data)
    rowNext.retired  := row.retired | retireBusMatches.asUInt.orR
    operandReadyNext := operandReady | operandBusMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := operandReady

  for (j <- 0 until config.nWide) {
    operandBusMatches(j) := io.lsOperandBus(j).valid & (row.rdTag === io.lsOperandBus(j).bits.rdTag)
    retireBusMatches(j)  := io.storeRetireBus(j).valid & (row.rdTag === io.storeRetireBus(j).bits.tag)
  }

  dontTouch(row.inst) // only for debugging
  dontTouch(row.pc) // only for debugging
  dontTouch(io.in.bits.inst) // only for debugging
  dontTouch(io.in.bits.pc) // only for debugging
}

class LSReservationStation(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSRSMI(config)))
    val flush          = Input(Bool())
    val lsOperandBus   = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = Decoupled(new LSRSMI(config))
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
  io.out.valid := !empty & outValid(deqPtr.value) & valid(deqPtr.value)

  when(io.flush) {
    valid.foreach(_ := 0.B)
    enqPtr.reset()
    deqPtr.reset()
  }

  rows.zipWithIndex.foreach {
    case (row, i) =>
      row.io.flush          := io.flush
      row.io.in.valid       := io.in.valid && (enqPtr.value === i.U) && !full
      row.io.in.bits        := io.in.bits
      row.io.lsOperandBus   := io.lsOperandBus
      row.io.storeRetireBus := io.storeRetireBus
      outValid(i)           := row.io.out.valid && (deqPtr.value === i.U) && valid(deqPtr.value)
  }
}
