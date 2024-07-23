package wood

import chisel3._
import chisel3.util._
import wood.exu.{DataBus, ExUnit}
import wood.fru.FrUnit

case class TestConfig(val maxWidth: Int = 2) {}

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
  scQueueDepth: Int = 2,
  //--------------
  // ExUnitConfig
  robDepth: Int = 16,
  rsDepth:  Int = 4 // Reservation station depth
  //--------------
) {

  require(
    robDepth % nWide == 0,
    "RobDepth must be divisible by nWide"
  )

  val prfDepth = (32 + (rsDepth * nWide) + (8 * nWide) + (robDepth * nWide))
  require(
    (prfDepth % nWide == 0),
    "prfDepth must be divisible by nWide and smaller than prfDepth"
  )

  // val pcIndexWidth: Int = log2Ceil(pcListDepth)
  val pcIndexWidth:       Int = 32 // TODO: use pc list not pc
  val pcIndexOffsetWidth: Int = log2Ceil(nWide)

  val tagWidth:      Int       = log2Ceil(prfDepth)
  val listExUnits:   List[Int] = List(nWide, 1, 1) // alu,mdu, idu
  val numPortsFloat: Int       = nWide
  val numPortsInt:   Int       = nWide

  val aluCrossbarIndex = 0 // NOTE: ALU has to be 0
  val imuCrossbarIndex = 1
  val iduCrossbarIndex = 2
}

class Wood(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx      = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val forwardBus = Vec(config.nWide, ValidIO(new DataBus(config)))
  })

  val frunit = Module(new FrUnit(config))
  val exunit = Module(new ExUnit(config))

  frunit.io.in    <> io.in
  frunit.io.pcIdx <> io.pcIdx
  exunit.io.in    <> frunit.io.out
  io.forwardBus   <> exunit.io.forwardBus
}
