package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{MI, PipelineRegister}
import wood.std.DCRRQueue

class MIStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val q    = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))
  val pReg = Module(new PipelineRegister(config))

  q.io.in    <> io.in
  pReg.io.in <> q.io.out
  io.out     <> pReg.io.out
}
