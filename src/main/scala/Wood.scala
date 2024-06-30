package wood

import chisel3._
import chisel3.util._
import wood.exu.{Bus, ExUnit}
import wood.fru.FrUnit

case class TestConfig(val maxWidth: Int = 1) {}

case class WoodConfig(
  nWide:     Int = 4,
  dataWidth: Int = 32,
  addrWidth: Int = 32,
  xlen:      Int = 32,
  //--------------
  // FrUnitConfig
  iCacheDepth:  Int = 1024,
  pcListDepth:  Int = 16,
  miQueueDepth: Int = 16,
  //--------------
  // ExUnitConfig
  prfDepth: Int = 128,
  rsDepth:  Int = 4 // Reservation station depth
  //--------------
) {
  val pcIndexWidth: Int = log2Ceil(pcListDepth)

  val tagWidth:      Int = log2Ceil(prfDepth)
  val numALUs:       Int = nWide
  val numPortsFloat: Int = nWide
  val numPortsInt:   Int = nWide
}

class Wood(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx      = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val forwardBus = Vec(config.nWide, Decoupled(new Bus(config)))

  })

  val frunit = Module(new FrUnit(config))
  val exunit = Module(new ExUnit(config))

  frunit.io.in         <> io.in
  frunit.io.pcIdx      <> io.pcIdx
  exunit.io.in         <> frunit.io.out
  frunit.io.forwardBus <> exunit.io.forwardBus
  io.forwardBus        <> exunit.io.forwardBus
}
