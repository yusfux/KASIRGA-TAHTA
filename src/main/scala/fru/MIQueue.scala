package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.DecodeConfig.{TYPE_FLOAT, TYPE_INT}
import wood.fru.MI
import wood.std.{DCCrossbar, DCPipelineRegister, DCRRQueue}

class MIStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in      = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val toFloat = Vec(config.numPortsFloat, Decoupled(new MI(config)))
    val toInt   = Vec(config.numPortsInt, Decoupled(new MI(config)))
  })

  require((config.nWide <= config.numPortsInt), "Blocking distribution is not supported.")
  require((config.nWide <= config.numPortsFloat), "Blocking distribution is not supported.")

  val crossbar = Module(
    new DCCrossbar(new MI(config))(config.nWide, List(config.numPortsFloat, config.numPortsInt))
  )
  val intQueue   = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))
  val floatQueue = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))
  val pRegInt    = Module(new DCPipelineRegister(new MI(config))(config.nWide))
  val pRegFloat  = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  crossbar.io.sel <> io.in.map(_.bits.isFloat)
  crossbar.io.in  <> io.in

  intQueue.io.in <> crossbar.io.out(TYPE_INT.toInt)
  pRegInt.io.in  <> intQueue.io.out
  io.toInt       <> pRegInt.io.out

  floatQueue.io.in <> crossbar.io.out(TYPE_FLOAT.toInt)
  pRegFloat.io.in  <> floatQueue.io.out
  io.toFloat       <> pRegFloat.io.out
}
