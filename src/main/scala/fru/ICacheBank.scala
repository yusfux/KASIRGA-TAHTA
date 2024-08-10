package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{ReadPortI, ReadPortO}

class ICacheBankIO(config: WoodConfig) extends Bundle {
  val core = new Bundle() {
    val req = Flipped(DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth)))
    val resp = DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth))
  }

  val mem = new Bundle() {
    val req = DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth))
    val resp = Flipped(DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth)))
  }
}

class ICacheBank(config: WoodConfig) extends Module {
  val io = IO(new ICacheBankIO(config))

  val icachecontroller = Module(new ICacheController(config))
  val sram = SRAM(config.icacheDepth,UInt(config.itaglen.W + config.idatalen.W + config.ivalidlen.W), 0, 0, 1)

  io.core <> icachecontroller.io.core
  io.mem <> icachecontroller.io.mem
  sram <> icachecontroller.io.cache
}