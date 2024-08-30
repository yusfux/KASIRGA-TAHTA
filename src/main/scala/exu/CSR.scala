package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCDemux

class CSRQueueRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(ValidIO(new MI(config)))
    val flush        = Input(Bool())
    val csrRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out          = ValidIO(new MI(config))
  })

  val rowNext          = Wire(new MI(config))
  val row              = RegEnable(rowNext, 0.U.asTypeOf(new MI(config)), 1.B)
  val retireBusMatches = Wire(Vec(config.nWide, Bool()))
  val matchIndex       = PriorityEncoder(retireBusMatches)

  when(io.flush) {
    rowNext := 0.U.asTypeOf(new MI(config))
  }.elsewhen(io.in.valid) {
    rowNext := io.in.bits
  }.otherwise {
    rowNext         := row
    rowNext.retired := row.retired | retireBusMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := row.retired

  for (j <- 0 until config.nWide) {
    retireBusMatches(j) := io.csrRetireBus(j).valid & (row.rdTag === io.csrRetireBus(j).bits.tag)
  }
}

class CSRQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Decoupled(new MI(config)))
    val csrRetireBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush        = Input(Bool())
    val out          = Decoupled(new MI(config))
  })

  val rows  = Seq.fill(config.lsSQDepth)(Module(new CSRQueueRow(config)))
  val demux = Module(new DCDemux(new MI(config))(1, config.lsSQDepth))

  val valid  = RegInit(VecInit(Seq.fill(config.lsSQDepth)(false.B)))
  val enqPtr = Counter(config.lsSQDepth)
  val deqPtr = Counter(config.lsSQDepth)
  val empty  = enqPtr.value === deqPtr.value && !valid(deqPtr.value)
  val full   = enqPtr.value === deqPtr.value && valid(deqPtr.value)

  val rowBits   = VecInit(rows.map(_.io.out.bits))
  val rowValids = VecInit(rows.map(_.io.out.valid))
  io.out.bits := Mux1H(UIntToOH(deqPtr.value), rowBits)

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
    demux.io.out(j)(0).ready := (enqPtr.value === j.U) && !full && !valid(enqPtr.value)
    rows(j).io.in.bits       := demux.io.out(j)(0).bits
    rows(j).io.in.valid      := demux.io.out(j)(0).valid && !io.in.bits.flushed && !full
    rows(j).io.flush         := io.flush
    rows(j).io.csrRetireBus  := io.csrRetireBus
  })
}

class CSR(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Decoupled(new MI(config)))
    val csrRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush        = Input(Bool())
    val out          = Decoupled(new MI(config))
  })

  val csrq = Module(new CSRQueue(config))
  // val csrs = Module(new CSRRegisters(config))

  csrq.io.in           <> io.in
  csrq.io.flush        <> io.flush
  csrq.io.csrRetireBus <> io.csrRetireBus

  csrq.io.out.ready := DontCare // TODO: connect csrs
  io.out.bits       := DontCare
  io.out.valid      := DontCare
}
