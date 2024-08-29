package wood.fru

import chisel3._
import chisel3.util._
import wood.{ICacheCorePort, ICacheMemPort, WoodConfig}

class ICacheBank(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = new ICacheCorePort(config)
    val mem  = new ICacheMemPort(config.copy(mmInterfaceWidth = config.xlen))
  })

  val icachecontroller = Module(new ICacheController(config))
  val sram             = SRAM(config.iCacheDepth, UInt(config.itaglen.W + config.idatalen.W + config.ivalidlen.W), 0, 0, 1)

  io.core <> icachecontroller.io.core
  io.mem  <> icachecontroller.io.mem
  sram    <> icachecontroller.io.cache
}
