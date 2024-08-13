package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{MemPort, PCInst}

class Fetch2Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val pc         = Vec(config.nWide, Flipped(DecoupledIO(UInt(config.pcWidth.W))))
    val mem        = new MemPort(config)
    val instPacket = Vec(config.nWide, DecoupledIO(new PCInst(config)))
  })

  val icachebankcont = Module(new ICacheBankController(config))

  icachebankcont.io.mem <> io.mem
  icachebankcont.io.core.zipWithIndex.foreach { case (core, i) =>
    core.req.bits.addr := io.pc(i).bits
    core.req.valid     := io.pc(i).valid
    io.pc(i).ready     := core.req.ready

    core.resp.ready := io.instPacket(i).ready
    io.instPacket(i).bits.inst := core.resp.bits.data
  }

  val allRespValid = icachebankcont.io.core.zipWithIndex.map { case (core, i) => core.resp.valid || ~io.pc(i).valid }.reduce(_ & _)

  (0 until config.nWide) foreach {i => io.instPacket(i).bits.pc := RegEnable(io.pc(i).bits, io.pc(i).fire) }
  (0 until config.nWide) foreach {i => io.instPacket(i).valid   := icachebankcont.io.core(i).resp.valid && allRespValid}
}