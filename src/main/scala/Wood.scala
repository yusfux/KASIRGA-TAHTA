package wood

import chisel3._
import chisel3.util._
import wood.exu.ExUnit
import wood.fru.FrUnit

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
  require(
    (dataWidth % 8 == 0),
    "dataWidth must be multiple of 8"
  )
  val ghrWidth = log2Ceil(btbdepth)

  val byteOffset = log2Ceil(pcWidth >> 3)
  val bankOffset = log2Ceil(nWide)
  val memOffset  = log2Ceil(memDataWidth / dataWidth)

  val itaglen   = pcWidth - (log2Ceil(icacheDepth) + byteOffset + bankOffset)
  val idatalen  = dataWidth
  val ivalidlen = 1

  val tagWidth: Int = log2Ceil(prfDepth)

  val aluCrossbarIndex = 0 // NOTE: ALU has to be 0
  val imuCrossbarIndex = 1
  val iduCrossbarIndex = 2
  val listExUnits: List[Int] = List(nWide, 1, 1) // alu,mdu, idu
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
  val req  = DecoupledIO(new ReqPort(config.addrWidth, config.memDataWidth).ReadReq)
  val resp = Flipped(DecoupledIO(new RespPort(config.memDataWidth).ReadResp))
}

class MemPortW(config: WoodConfig) extends Bundle {
  val req  = Flipped(DecoupledIO(new ReqPort(config.addrWidth, config.memDataWidth).WriteReq))
}

class CorePort(config: WoodConfig) extends Bundle {
  val req = Flipped(DecoupledIO(new ReqPort(config.addrWidth, config.dataWidth).ReadReq))
  val resp = DecoupledIO(new RespPort(config.dataWidth).ReadResp)
}

class Wood(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val memr = new MemPortR(config)
  })

  val frunit = Module(new FrUnit(config))
  val exunit = Module(new ExUnit(config))

  frunit.io.bpBus <> exunit.io.bpBus

  for(i <- 0 until config.nWide) {
    exunit.io.in(i).valid      := frunit.io.instPacket.valid
    exunit.io.in(i).bits.pc    := frunit.io.instPacket.bits(i).pc
    exunit.io.in(i).bits.inst  := frunit.io.instPacket.bits(i).inst
  }

  frunit.io.instPacket.ready := exunit.io.in.map(_.ready).reduce(_ && _)
  exunit.io.in.map(_.bits.valid := true.B)

  frunit.io.mem <> io.memr
}
