package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.std.DCDemux

class LSSQStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val selfRetired    = Output(Bool())
    val out            = Decoupled(new LSCMI(config)) // comb out
    val outPeriph      = Decoupled(new LSCMI(config)) // comb out
  })

  val inRetireOverrider  = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val outRetireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val cacheDemux         = Module(new DCDemux(new LSCMI(config))(1, 2))
  val periphDemux        = Module(new DCDemux(new LSCMI(config))(1, 2))
  val arbiter            = Module(new Arbiter(new LSCMI(config), 2))
  val sq                 = Module(new LSStoreQueue(config))
  val sqIndex            = 1
  val inIndex            = 0

  val cacheIndex  = 1
  val periphIndex = 0

  val isPeriph = ("h80000000".U > io.in.bits.addr) && (io.in.bits.addr >= "h20000000".U) // TODO: fix hardcoded addr

  periphDemux.io.sel(0) := Mux(isPeriph, periphIndex.U, cacheIndex.U)
  cacheDemux.io.sel(0)  := Mux(io.in.bits.store, sqIndex.U, inIndex.U)

  val flushed = outRetireOverrider.io.out.bits.flushed || (io.flush && !outRetireOverrider.io.out.bits.retired)

  inRetireOverrider.io.in  <> io.in
  periphDemux.io.in(0)     <> inRetireOverrider.io.out
  cacheDemux.io.in(0)      <> periphDemux.io.out(cacheIndex)(0)
  io.outPeriph             <> periphDemux.io.out(periphIndex)(0)
  sq.io.in                 <> cacheDemux.io.out(sqIndex)(0)
  arbiter.io.in(inIndex)   <> cacheDemux.io.out(inIndex)(0)
  arbiter.io.in(sqIndex)   <> sq.io.out
  outRetireOverrider.io.in <> arbiter.io.out
  io.out                   <> outRetireOverrider.io.out
  io.out.valid             := !flushed && outRetireOverrider.io.out.valid // drop if flushed
  io.out.bits.flushed      := flushed
  io.out.bits.sqData       := sq.io.camReadOut.bits.sqData
  io.out.bits.sqwStrobe    := sq.io.camReadOut.bits.sqwStrobe

  outRetireOverrider.io.storeRetireBus <> io.storeRetireBus
  inRetireOverrider.io.storeRetireBus  <> io.storeRetireBus

  sq.io.camReadIn      := io.in.bits.addr
  sq.io.flush          := io.flush
  sq.io.storeRetireBus := io.storeRetireBus

  io.selfRetired := outRetireOverrider.io.out.bits.retired
}
