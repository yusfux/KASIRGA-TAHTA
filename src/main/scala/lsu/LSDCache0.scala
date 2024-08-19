package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.DCPipelineRegister

class StoreQueueRow(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(ValidIO(new LSMI(config)))
    val flush          = Input(Bool())
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = ValidIO(new LSMI(config))
  })

  val busRdMatches = Wire(Vec(config.nWide, Bool()))

  val commitableNext = Wire(Bool())
  val rowNext        = Wire(new LSMI(config))

  val row        = RegEnable(rowNext, 0.U.asTypeOf(new LSMI(config)), 1.B)
  val commitable = RegEnable(commitableNext, 0.U, 1.B)

  val matchIndex = PriorityEncoder(busRdMatches)
  when(io.flush) {
    rowNext        := 0.U.asTypeOf(new LSMI(config))
    commitableNext := 0.U
  }.elsewhen(io.in.valid) {
    rowNext        := 0.U.asTypeOf(new LSMI(config))
    commitableNext := 0.U
    rowNext.rdTag  := io.in.bits.rdTag
  }.otherwise {
    rowNext        := row
    commitableNext := commitable | busRdMatches.asUInt.orR
  }

  io.out.bits  := row
  io.out.valid := commitable

  for (j <- 0 until config.nWide) {
    busRdMatches(j) := io.storeRetireBus(j).valid & (row.rdTag === io.storeRetireBus(j).bits.tag)
  }
}

class StoreQueue(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSMI(config)))
    val camReadIn      = Input(UInt(config.addrWidth.W))
    val storeRetireBus = Input(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val camReadOut     = Output(new LSMI(config))
    val out            = Decoupled(new LSMI(config))
  })

  val rows     = Seq.fill(config.lssqDepth)(Module(new StoreQueueRow(config)))
  val valid    = RegInit(VecInit(Seq.fill(config.lssqDepth)(false.B)))
  val outValid = RegInit(VecInit(Seq.fill(config.lssqDepth)(false.B)))
  val enqPtr   = Counter(config.lssqDepth)
  val deqPtr   = Counter(config.lssqDepth)
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

  (0 until config.lssqDepth).foreach(j => {
    rows(j).io.flush          := io.flush
    rows(j).io.in.valid       := io.in.fire && (enqPtr.value === j.U)
    rows(j).io.in.bits        := io.in.bits
    rows(j).io.storeRetireBus := io.storeRetireBus
    outValid(j)               := rows(j).io.out.valid && (deqPtr.value === j.U) && valid(deqPtr.value)
  })

  //********* CAM Logic ************//
  val recencyArray = VecInit(Seq.fill(config.lssqDepth)(0.U))
  (0 until config.lssqDepth).foreach(j => {
    recencyArray(j) := j.U - deqPtr.value
  })

  val numBytes = config.dataWidth / 8

  val (finalWstrobe, finalData) =
    (0 until config.lssqDepth).foldLeft((0.U(numBytes.W), VecInit(Seq.fill(numBytes)(0.U(8.W))))) {
      case ((accWstrobe, accData), j) =>
        val currentWstrobe = rows(j).io.out.bits.wStrobe
        val currentData    = rows(j).io.out.bits.data
        val currentRecency = recencyArray(j)

        val newWstrobe = accWstrobe | (currentWstrobe.asUInt & Fill(4, valid(j)))

        val addrMatch = VecInit(Seq.fill(config.lssqDepth)(0.B))
        addrMatch.zipWithIndex.foreach {
          case (row, j) =>
            addrMatch(j) := rows(j).io.out.bits.addr === io.camReadIn
        }

        val wstrobeValid = VecInit(rows.map(_.io.out.bits.wStrobe))

        val recencyCompare = VecInit(Seq.fill(config.lssqDepth)(0.B))
        recencyCompare.zipWithIndex.foreach {
          case (row, j) =>
            recencyCompare(j) := currentRecency > recencyArray(j)
        }

        val newData = VecInit(Seq.fill(numBytes)(0.U(8.W)))
        newData := (0 until 4).map { byteIndex =>
          val isCurrentByteValid = currentWstrobe(byteIndex)

          val moreRecentValid = (0 until j).map(k => addrMatch(k) && wstrobeValid(k)(byteIndex) && recencyCompare(k)).foldLeft(true.B)(_ && _)

          val selectedData =
            Mux(isCurrentByteValid && moreRecentValid, currentData(byteIndex), accData(byteIndex))

          selectedData
        }

        (newWstrobe, newData)
    }

  val tstrobe = Wire(Vec(numBytes, Bool()))
  tstrobe := VecInit(Seq.tabulate(numBytes)(j => finalWstrobe(j)))

  io.camReadOut         := DontCare
  io.camReadOut.wStrobe := tstrobe
  io.camReadOut.data    := finalData
}

class LSDCache0Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSMI(config)))
    val lsAtomBus      = Flipped(DecoupledIO(new LSMI(config)))
    val lsDCacheBus    = Flipped(ValidIO(new LSMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val outCache       = Decoupled(new LSMI(config)) // comb out
    val outPass        = Decoupled(new LSMI(config))
  })

  val pReg     = Module(new DCPipelineRegister(new LSMI(config))(1))
  val arbiter  = Module(new Arbiter(new LSMI(config), 2))
  val sq       = Module(new StoreQueue(config))
  val selfPass = Wire(Decoupled(new LSMI(config)))

  selfPass             <> io.in
  sq.io.camReadIn      := io.in.bits.addr
  sq.io.flush          := io.flush
  sq.io.in.valid       := io.in.fire
  sq.io.storeRetireBus := io.storeRetireBus
  sq.io.in             <> io.lsAtomBus

  (0 until config.dataWidth / 8).foreach(j => {
    val atomByteUpdate   = io.lsAtomBus.bits.wStrobe(j) && (io.in.bits.addr === io.lsAtomBus.bits.addr)
    val dcacheByteUpdate = io.lsDCacheBus.bits.wStrobe(j) && (io.in.bits.addr === io.lsDCacheBus.bits.addr)

    selfPass.bits.data(j) := MuxCase(
      io.in.bits.data(j),
      Array(
        (io.in.bits.wStrobe(j))       -> io.in.bits.data(j),
        (atomByteUpdate)              -> io.lsAtomBus.bits.data(j),
        (dcacheByteUpdate)            -> io.lsDCacheBus.bits.data(j),
        (sq.io.camReadOut.wStrobe(j)) -> sq.io.camReadOut.data(j)
      ).toIndexedSeq
    )
    selfPass.bits.wStrobe(j) := io.in.bits.wStrobe(j) | atomByteUpdate | dcacheByteUpdate
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
