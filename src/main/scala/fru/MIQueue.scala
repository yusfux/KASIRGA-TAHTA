package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.DCRRQueue

class MIStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val q = Module(new DCRRQueue(new MI(config))(config.nWide, config.miQueueDepth))

  val out_ready = Wire(Vec(config.nWide, Bool()))
  val out_valid = Wire(Vec(config.nWide, Bool()))
  out_ready := io.out.map(_.ready)
  out_valid := io.in.map(_.valid)
  val valid = out_valid.asUInt.andR
  val ready = out_ready.asUInt.andR
  val stall = !(valid && ready)

  (0 until config.nWide).foreach(j => {
    io.out(j).bits    := RegEnable(q.io.out(j).bits, 0.U.asTypeOf(new MI(config)), !stall)
    io.out(j).valid   := RegEnable(q.io.out(j).valid, 0.B, !stall)
    q.io.out(j).ready := io.out(j).ready
  })
  q.io.in <> io.in
}
