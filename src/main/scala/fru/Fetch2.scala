package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.{ReadPortI, ReadPortO}

//TODO: we only support for mask equals all 1 for now, it should be easy to implement in couple of lines
class Fetch2IO(config: WoodConfig) extends Bundle {
  val in = Flipped(DecoupledIO(new Bundle {
    val controller = Vec(config.nWide, new Bundle() {
      val pc = UInt(config.pcWidth.W)
      val mask = Bool()
    })
  }))

  val mem = new Bundle() {
    val req = DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth))
    val resp = Flipped(DecoupledIO(new ReadPortO(UInt(config.memDataWidth.W))(config.addrWidth)))
  }

  val out = new Bundle {
    val instruction = Vec(config.nWide, DecoupledIO(UInt(config.xlen.W)))
  }
}

class Fetch2Stage(config: WoodConfig) extends Module {
  val io = IO(new Fetch2IO(config))

  val icachebankcont = Module(new ICacheBankController(config))

  icachebankcont.io.mem <> io.mem
  icachebankcont.io.core.zipWithIndex.foreach { case (core, i) =>
    core.req.bits.addr := io.in.bits.controller(i).pc
    core.req.valid := io.in.valid && io.in.bits.controller(i).mask

    core.resp.ready := io.out.instruction(i).ready
    io.out.instruction(i).bits := core.resp.bits.data
  }
  io.out.instruction.map(_.valid := icachebankcont.io.core.map(_.resp.valid).reduce(_ & _))
  io.in.ready := icachebankcont.io.core.map(_.req.ready).reduce(_ & _)
}

