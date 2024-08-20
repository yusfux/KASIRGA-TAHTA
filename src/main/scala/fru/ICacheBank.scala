package wood.fru

import chisel3._
import chisel3.util._
import wood.{CorePort, MemPortR, WoodConfig}

class ICacheBank(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = new CorePort(config)
    val mem  = new MemPortR(config.copy(mmInterfaceWidth = config.xlen))
  })

  val icachecontroller = Module(new ICacheController(config))
  val sram             = SRAM(config.iCacheDepth, UInt(config.itaglen.W + config.idatalen.W + config.ivalidlen.W), 0, 0, 1)

  io.core <> icachecontroller.io.core
  io.mem  <> icachecontroller.io.mem
  sram    <> icachecontroller.io.cache
}
