package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus

class LSSQStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val selfRetired    = Output(Bool())
    val out            = Decoupled(new LSCMI(config)) // comb out
  })

  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val arbiter         = Module(new Arbiter(new LSCMI(config), 2))
  val sq              = Module(new LSStoreQueue(config))
  val sqArbIndex      = 0
  val inArbIndex      = 1

  io.in.ready := sq.io.in.ready && io.out.ready && (arbiter.io.chosen === inArbIndex.U)

  retireOverrider.io.storeRetireBus <> io.storeRetireBus
  retireOverrider.io.in             <> io.in
  sq.io.in                          <> retireOverrider.io.out
  sq.io.in.valid                    := io.in.bits.store && retireOverrider.io.out.fire

  sq.io.camReadIn      := io.in.bits.addr
  sq.io.flush          := io.flush
  sq.io.storeRetireBus := io.storeRetireBus

  arbiter.io.in(sqArbIndex)       <> sq.io.out
  arbiter.io.in(inArbIndex)       <> io.in
  arbiter.io.in(inArbIndex).valid := (!io.in.bits.store & io.in.valid)
  io.out                          <> arbiter.io.out

  io.out.bits.sqData  := sq.io.camReadOut.bits.sqData
  io.out.bits.wStrobe := sq.io.camReadOut.bits.wStrobe
  io.selfRetired      := retireOverrider.io.out.bits.retired

}
