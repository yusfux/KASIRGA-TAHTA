package wood.fru

import chisel3._
import chisel3.util._
import wood.exu.BranchPredictorBus
import wood.{MemPortR, WoodConfig}

class PCQueueEntry(config: WoodConfig) extends Bundle {
  val fetchpc = UInt(config.pcWidth.W)
  val mask    = Vec(config.nWide, Bool())
}

class PCMask(config: WoodConfig) extends Bundle {
  val pc    = UInt(config.pcWidth.W)
  val valid = Bool()
}

class PCInst(config: WoodConfig) extends Bundle {
  val pc    = UInt(config.pcWidth.W)
  val inst  = UInt(config.xlen.W)
  val valid = Bool()
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus        = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val instPacket   = DecoupledIO(Vec(config.nWide, new PCInst(config)))
    val mem          = new MemPortR(config)
  })

  val f1stage = Module(new Fetch1Stage(config))
  val f2stage = Module(new Fetch2Stage(config))
  val flush   = io.bpBus.map(bp => bp.valid && (bp.bits.mispredict || bp.bits.exception)).reduce(_ || _)

  f1stage.io.bpBus <> io.bpBus
  f2stage.io.mem   <> io.mem
  io.instPacket    <> f2stage.io.instPacket

  f2stage.io.flush := flush

  for(i <- 0 until config.nWide) {
    f2stage.io.pcPacket.bits(i).pc    := RegNext(Mux(flush, 0.U    , f1stage.io.pcPacket.bits(i).pc   ), init = 0.U    )
    f2stage.io.pcPacket.bits(i).valid := RegNext(Mux(flush, false.B, f1stage.io.pcPacket.bits(i).valid), init = false.B)
    f2stage.io.pcPacket.valid         := RegNext(Mux(flush, false.B, f1stage.io.pcPacket.valid        ), init = false.B)
    f1stage.io.pcPacket.ready         := f2stage.io.pcPacket.ready

    io.instPacket.bits(i).pc          := RegNext(Mux(flush, 0.U    , f2stage.io.instPacket.bits(i).pc   ), init = 0.U    )
    io.instPacket.bits(i).inst        := RegNext(Mux(flush, 0.U    , f2stage.io.instPacket.bits(i).inst ), init = 0.U    )
    io.instPacket.bits(i).valid       := RegNext(Mux(flush, false.B, f2stage.io.instPacket.bits(i).valid), init = false.B)
    io.instPacket.valid               := RegNext(Mux(flush, false.B, f2stage.io.instPacket.valid        ), init = false.B)
    f2stage.io.instPacket.ready       := io.instPacket.ready
  }
}