package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{CorePort, MemPort}

class ICacheBank(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = new CorePort(config)
    val mem  = new MemPort(config.copy(memDataWidth = config.dataWidth))
  })

  val icachecontroller = Module(new ICacheController(config))
  val sram = SRAM(config.icacheDepth, UInt(config.itaglen.W + config.idatalen.W + config.ivalidlen.W), 0, 0, 1)

  io.core <> icachecontroller.io.core
  io.mem  <> icachecontroller.io.mem
  sram    <> icachecontroller.io.cache
}