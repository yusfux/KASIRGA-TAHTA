package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{ReadPortI, ReadPortO}

case class PCInst(config: WoodConfig) extends Bundle {
  val pc   = UInt(config.pcWidth.W)
  val inst = UInt(config.xlen.W) // TODO
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in = Input(new Bundle {
      val exception = new Bundle {
        val en = Bool()                 // exception happened
        val pc = UInt(config.pcWidth.W) // new pc to fetch after the exception (i.e., exception handler pc from mepc)
      }

      val mispred = new Bundle {
        val pc = UInt(config.pcWidth.W)       // pc of the mispredicted branch
        val targetpc = UInt(config.pcWidth.W) // target pc of the mispredicted branch
        val en = Bool()                       // misprediction happened
        val taken = Bool()                    // mispredicted branch was taken
      }
    })

    val mem = new Bundle() {
      val req = DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth))
      val resp = Flipped(DecoupledIO(new ReadPortO(UInt(config.memDataWidth.W))(config.addrWidth)))
    }

    val out = new Bundle {
      val instruction = Vec(config.nWide, DecoupledIO(UInt(config.xlen.W)))
    }
  })

  val f1stage = Module(new Fetch1Stage(config))
  val f2stage = Module(new Fetch2Stage(config))

  f1stage.io.in <> io.in
  f2stage.io.in <> f1stage.io.out
  io.out <> f2stage.io.out

  f2stage.io.mem <> io.mem
}
