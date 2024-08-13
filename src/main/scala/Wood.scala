package wood

import chisel3._
import chisel3.util._
// import wood.exu.{DataBus, ExUnit}
// import wood.fru.FrUnit

case class TestConfig(val maxWidth: Int = 2) {}

case class WoodConfig(
  nWide:        Int = 4,
  dataWidth:    Int = 32,
  addrWidth:    Int = 32,
  pcWidth:      Int = 32,
  xlen:         Int = 32,
  memDataWidth: Int = 128,
  memDepth:     Int = 1024,
  //--------------
  // FrUnitConfig
  pcInitAddr:   String = "h8000_0000",
  icacheDepth:  Int    = 1024,
  btbdepth:     Int    = 32,
  pcListDepth:  Int    = 16,
  pcQueueDepth: Int    = 16,
  scQueueDepth: Int    = 2,
  //--------------
  // ExUnitConfig
  miQueueDepth: Int = 16,
  robDepth:     Int = 16,
  rsDepth:      Int = 4 // Reservation station depth
  //--------------
) {

  require(
    robDepth % nWide == 0,
    "RobDepth must be divisible by nWide"
  )

  val prfDepth = (32 + (rsDepth * nWide) + (8 * nWide) + (robDepth * nWide))
  require(
    (prfDepth % nWide == 0),
    "prfDepth must be divisible by nWide"
  )
  val ghrWidth   = log2Ceil(btbdepth)

  val byteOffset = log2Ceil(pcWidth >> 3)
  val bankOffset = log2Ceil(nWide)
  val memOffset  = log2Ceil(memDataWidth / dataWidth)

  val itaglen    = pcWidth - (log2Ceil(icacheDepth) + byteOffset + bankOffset)
  val idatalen   = dataWidth
  val ivalidlen  = 1

  val tagWidth: Int = log2Ceil(prfDepth)

  val aluCrossbarIndex = 0 // NOTE: ALU has to be 0
  val imuCrossbarIndex = 1
  val iduCrossbarIndex = 2
  val listExUnits: List[Int] = List(nWide, 1, 1) // alu,mdu, idu
}

class Wood(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val uart_rx = Input(UInt(1.W))
    val uart_tx = Output(UInt(1.W))
  })

  // val frunit = Module(new FrUnit(config))
  // val exunit = Module(new ExUnit(config))
  // // TODO: load store unit

  // frunit.io.in <> io.in
  // //frunit.io.pcIdx <> io.pcIdx
  // exunit.io.in  <> frunit.io.out
  // io.forwardBus <> exunit.io.forwardBus
}
