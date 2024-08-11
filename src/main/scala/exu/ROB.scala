package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{DCPipelineRegister, DCRRQueue}

class ROBStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in      = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val flush   = Input(Bool())
    val out     = Vec(config.nWide, Decoupled(new MI(config)))
    val firstPC = Valid(UInt(config.pcWidth.W))
  })

  val q     = Module(new DCRRQueue(new MI(config))(config.nWide, config.robDepth))
  val pRegs = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  val savedCounts = RegInit(VecInit(Seq.fill(config.nWide)(0.U(log2Ceil(config.robDepth).W))))

  when(io.flush) {
    (0 until config.nWide).foreach(j => {
      savedCounts(j) := q.io.count(j) + io.in(j).valid
    })
  }

  q.io.in    <> io.in
  q.io.flush := 0.B // must return each tag back to freelist

  (0 until config.nWide).foreach(j => {
    pRegs(j).io.valids(0) := q.io.out(j).valid
    pRegs(j).io.flush     := io.flush
    pRegs(j).io.in        <> q.io.out(j)

    pRegs(j).io.flush := io.flush

    when(savedCounts(j) > 0.U) {
      io.out(j)              <> pRegs(j).io.out
      savedCounts(j)         := savedCounts(j) - 1.U
      io.out(j).bits.writeRf := false.B
      io.out(j).bits.flushed := true.B
    }.otherwise {
      io.out(j)              <> pRegs(j).io.out
      io.out(j).bits.flushed := false.B
    }
  })

  io.firstPC.bits  := q.io.out(0).bits.pc
  io.firstPC.valid := q.io.out(0).valid
}
