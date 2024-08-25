package wood

import chisel3._
import chisel3.util._
import wood.exu.ExUnit
import wood.fru.FrUnit

case class TestConfig(val maxWidth: Int = 2) {}

case class WoodConfig(
  nWide:            Int = 4,
  xlen:             Int = 32,
  mmInterfaceWidth: Int = 128, // Main Memory Interface Width
  mmDepth:          Int = 86016,
  //--------------
  // FrUnitConfig
  pcInitAddr:   String = "h8000_0000",
  iCacheDepth:  Int    = 1024,
  btbDepth:     Int    = 32,
  pcListDepth:  Int    = 16,
  pcQueueDepth: Int    = 16,
  scQueueDepth: Int    = 2,
  //--------------
  // ExUnitConfig
  miQueueDepth: Int = 16,
  robDepth:     Int = 16,
  rsDepth:      Int = 2, // Reservation station depth
  //--------------
  // LsUnitConfig
  lsSQDepth: Int = 3, // LS Store Queue Depth
  lsRSDepth: Int = 2 // LS reservation station Depth
  //--------------
) {
  val dcacheDepth           = mmDepth // TODO: connect the cache
  val dCacheLineWidth       = mmInterfaceWidth
  val iCacheLineWidth       = mmInterfaceWidth // TODO set or use this
  val numBytes              = xlen / 8
  val numDCacheLineBytes    = dCacheLineWidth / 8
  val wordAddressStartIndex = log2Ceil(numBytes) - 1

  require(
    robDepth % nWide == 0,
    "RobDepth must be divisible by nWide"
  )
  require(
    xlen == 32,
    "Other xlen values are not tested"
  )
  val prfDepth = (32 + (rsDepth * nWide) + (8 * nWide) + (robDepth * nWide))
  require(
    (prfDepth % nWide == 0),
    "prfDepth must be divisible by nWide"
  )
  require(
    (mmInterfaceWidth == 128),
    "Other mmInterfaceWidth values are not tested"
  )

  val ghrWidth = log2Ceil(btbDepth)

  val byteOffset = log2Ceil(xlen >> 3)
  val bankOffset = log2Ceil(nWide)
  val memOffset  = log2Ceil(mmInterfaceWidth / xlen)

  val itaglen   = xlen - (log2Ceil(iCacheDepth) + byteOffset + bankOffset)
  val idatalen  = xlen
  val ivalidlen = 1

  val tagWidth: Int = log2Ceil(prfDepth)

  val aluCrossbarIndex = 0 // NOTE: ALU has to be 0
  val imuCrossbarIndex = 1
  val iduCrossbarIndex = 2
  val listExCrossbarUnits: List[Int] = List(nWide, 1, 1) // alu, mdu, idu
  val listExUnits:         List[Int] = List(nWide, 1, 1) // alu, mdu, idu

}

class ReqPort(addrWidth: Int, dataWidth: Int) extends Bundle {
  val ReadReq = new Bundle {
    val addr = UInt(addrWidth.W)
  }

  val WriteReq = new Bundle {
    val addr = UInt(addrWidth.W)
    val data = UInt(dataWidth.W)
  }
}

class RespPort(dataWidth: Int) extends Bundle {
  val ReadResp = new Bundle {
    val data = UInt(dataWidth.W)
  }
}

class MemPortR(config: WoodConfig) extends Bundle {
  val req  = DecoupledIO(new ReqPort(config.xlen, config.mmInterfaceWidth).ReadReq)
  val resp = Flipped(DecoupledIO(new RespPort(config.mmInterfaceWidth).ReadResp))
}

class MemPortW(config: WoodConfig) extends Bundle {
  val req = Flipped(DecoupledIO(new ReqPort(config.xlen, config.mmInterfaceWidth).WriteReq))
}

class CorePort(config: WoodConfig) extends Bundle {
  val req  = Flipped(DecoupledIO(new ReqPort(config.xlen, config.xlen).ReadReq))
  val resp = DecoupledIO(new RespPort(config.xlen).ReadResp)
}

class Wood(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memr = new MemPortR(config)
  })

  val frunit = Module(new FrUnit(config))
  val exunit = Module(new ExUnit(config))

  frunit.io.bpBus <> exunit.io.bpBus

  for (i <- 0 until config.nWide) {
    exunit.io.in(i).valid     := frunit.io.instPacket.valid
    exunit.io.in(i).bits.pc   := frunit.io.instPacket.bits(i).pc
    exunit.io.in(i).bits.inst := frunit.io.instPacket.bits(i).inst
  }

  frunit.io.instPacket.ready    := exunit.io.in.map(_.ready).reduce(_ && _)
  exunit.io.in.map(_.bits.valid := true.B)

  frunit.io.mem <> io.memr
}
