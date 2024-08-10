package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog}
import wood.WoodConfig
import wood.std.{DecoupledSyncReadBlockRAM}
import wood.std.BlockRAMParams

class Fetch2SpecDut(config: WoodConfig) extends Module {
  val io = IO( new Bundle {
    val in = Flipped(DecoupledIO(new Bundle {
      val controller = Vec(config.nWide, new Bundle() {
        val pc = UInt(config.pcWidth.W)
        val mask = Bool()
      })
    }))

    val out = new Bundle {
      val instruction = Vec(config.nWide, DecoupledIO(UInt(config.xlen.W)))
    }

    val data = Input(UInt(config.memDataWidth.W))
    val addr = Input(UInt(config.addrWidth.W))
    val valid = Input(Bool())
    val ready = Output(Bool())
  })

  val fetch2stage = Module(new Fetch2Stage(config))
  val mem = Module(new DecoupledSyncReadBlockRAM(UInt(config.memDataWidth.W))(new BlockRAMParams(config.memDepth, 1, 1)))

  fetch2stage.io.in <> io.in
  fetch2stage.io.out <> io.out

  mem.io.rip(0).valid := fetch2stage.io.mem.req.valid
  mem.io.rip(0).bits.addr := fetch2stage.io.mem.req.bits.addr >> (2 + log2Ceil(config.memDataWidth / 32))
  fetch2stage.io.mem.req.ready := mem.io.rip(0).ready
  mem.io.rop(0) <> fetch2stage.io.mem.resp

  mem.io.wp(0).valid := io.valid
  mem.io.wp(0).bits.enable := io.valid
  mem.io.wp(0).bits.addr := io.addr
  mem.io.wp(0).bits.data := io.data
  io.ready := mem.io.wp(0).ready
}

class Fetch2Spec extends AnyFlatSpec with ChiselScalatestTester {

  "Fetch2Stage" should "emit Verilog" in {
    GenerateVerilog(new Fetch2Stage(new WoodConfig))
  }
}