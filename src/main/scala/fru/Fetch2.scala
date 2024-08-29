package wood.fru

import chisel3._
import chisel3.util._
import wood.fru.PCInst
import wood.{ICacheMemPort, WoodConfig}

class Fetch2Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val pcPacket   = Flipped(DecoupledIO(Vec(config.nWide, new PCMask(config))))
    val instPacket = DecoupledIO(Vec(config.nWide, new PCInst(config)))
    val mem        = new ICacheMemPort(config)

    val flush = Input(Bool())
  })

  val pcPacketReg =
    RegEnable(io.pcPacket.bits, VecInit(Seq.fill(config.nWide)(0.U.asTypeOf(new PCMask(config)))), io.pcPacket.valid && io.pcPacket.ready)

  val icachebankcont = Module(new ICacheBankController(config))
  val kill           = RegInit(false.B)

  icachebankcont.io.mem <> io.mem
  for (i <- 0 until config.nWide) {
    icachebankcont.io.core(i).req.valid     := io.pcPacket.valid && io.pcPacket.ready && io.pcPacket.bits(i).valid
    icachebankcont.io.core(i).req.bits.addr := io.pcPacket.bits(i).pc
    icachebankcont.io.core(i).resp.ready    := io.instPacket.ready && io.instPacket.valid

    io.instPacket.bits(i).pc    := pcPacketReg(i).pc
    io.instPacket.bits(i).valid := pcPacketReg(i).valid
    io.instPacket.bits(i).inst  := icachebankcont.io.core(i).resp.bits.data
  }

  val allRespValid = icachebankcont.io.core.map(_.resp.valid).reduce(_ && _)
  val allReqReady  = icachebankcont.io.core.map(_.req.ready).reduce(_ && _)

  io.pcPacket.ready   := allReqReady
  io.instPacket.valid := allRespValid

  when(io.flush && !allRespValid && !allReqReady) {
    kill := true.B
  }

  when(kill && allRespValid) {
    kill                                        := false.B
    icachebankcont.io.core.foreach(_.resp.ready := true.B)
  }

  when(io.flush && allRespValid) {
    icachebankcont.io.core.foreach(_.resp.ready := true.B)
  }

  when(kill) {
    io.instPacket.valid := false.B
    io.pcPacket.ready   := false.B
  }
}
