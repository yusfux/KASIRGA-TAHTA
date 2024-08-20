package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig

class PCListIO(config: WoodConfig) extends Bundle {
  val fetch1 = new Bundle {
    val fetchpc = Flipped(DecoupledIO(UInt(config.xlen.W)))
    val tag     = Output(UInt(log2Ceil(config.pcListDepth).W))
  }

  val exu = new Bundle {
    val tag       = Input(UInt(log2Ceil(config.pcListDepth).W))
    val pc        = Output(UInt(config.xlen.W))
    val retiretag = Input(Bool())
  }
}

class PCList(config: WoodConfig) extends Module {
  val io = IO(new PCListIO(config))

  val depth    = config.pcListDepth
  val pclen    = config.xlen
  val validlen = 1

  val pclist = RegInit(VecInit(Seq.fill(depth)(0.U((validlen + pclen).W))))

  val isfull  = pclist.map(x => x(pclen)).reduce(_ & _)
  val freeIdx = pclist.indexWhere(x => x(pclen) === 0.U)

  when(io.fetch1.fetchpc.fire) {
    pclist(freeIdx) := Cat(1.U, io.fetch1.fetchpc.bits)
  }

  when(io.exu.retiretag) {
    pclist(io.exu.tag) := 0.U
  }

  io.fetch1.fetchpc.ready := !isfull
  io.fetch1.tag           := freeIdx
  io.exu.pc               := pclist(io.exu.tag)(pclen - 1, 0)
}
