package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCPipelineRegister, DCRRQueue}

class ROBStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in      = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val flush   = Input(Bool())
    val out     = Vec(config.nWide, Decoupled(new RetireMI(config)))
    val firstPC = Valid(UInt(config.pcWidth.W))
  })

  val q     = Module(new DCRRQueue(new RetireMI(config))(config.nWide, config.robDepth, flow = false))
  val pRegs = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new RetireMI(config))(1)))

  val savedCounts = RegInit(VecInit(Seq.fill(config.nWide)(0.U(log2Ceil(config.robDepth + 1).W))))

  (0 until config.nWide).foreach(j => {
    when(io.flush) {
      when(q.io.count(j) > 0.U) {
        savedCounts(j) := q.io.count(j) - Mux(io.in(j).valid, 0.U, 1.U)
      }
    }
    when(savedCounts(j) > 0.U && io.out(j).fire) {
      savedCounts(j) := savedCounts(j) - 1.U
    }
  })

  q.io.in <> io.in.map { mi =>
    val remi = Wire(DecoupledIO(new RetireMI(config)))
    remi.valid := mi.valid
    mi.ready   := remi.ready

    remi.bits.isBranch := mi.bits.isBranch
    remi.bits.isJAL    := mi.bits.isJAL
    remi.bits.writeRf  := mi.bits.writeRf
    remi.bits.rd       := mi.bits.rd
    remi.bits.pc       := mi.bits.pc
    remi.bits.targetPC := mi.bits.targetPC
    remi.bits.rdTag    := mi.bits.rdTag
    remi.bits.retired  := mi.bits.retired
    remi.bits.flushed  := mi.bits.flushed | io.flush
    remi.bits.arfTag   := mi.bits.arfTag
    remi.bits.arfValid := mi.bits.arfValid
    remi.bits.inst     := mi.bits.inst
    remi
  }

  q.io.flush := 0.B // must return each tag back to freelist

  (0 until config.nWide).foreach(j => {
    pRegs(j).io.valids(0)       := q.io.out(j).valid
    pRegs(j).io.in              <> q.io.out(j)
    pRegs(j).io.in.bits.flushed := q.io.out(j).bits.flushed | io.flush | (savedCounts(j) =/= 0.U)
    pRegs(j).io.in.bits.writeRf := Mux((io.flush | (savedCounts(j) =/= 0.U)), 0.U, q.io.out(j).bits.writeRf)

    pRegs(j).io.flush := 0.U // never lose tags

    io.out(j) <> pRegs(j).io.out
  })

  io.firstPC.bits  := q.io.out(0).bits.pc
  io.firstPC.valid := q.io.out(0).valid
}
