package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.std.{DecoupledSyncReadBlockRAM}
import wood.std.BlockRAMParams

class FrUnitDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle() {
    val instruction = Vec(config.nWide, DecoupledIO(UInt(config.xlen.W)))

    val data = Input(UInt(config.memDataWidth.W))
    val addr = Input(UInt(config.addrWidth.W))
    val valid = Input(Bool())
    val ready = Output(Bool())
  })

  val frunit = Module(new FrUnit(config))
  val mem = Module(new DecoupledSyncReadBlockRAM(UInt(config.memDataWidth.W))(new BlockRAMParams(config.memDepth, 1, 1)))

  mem.io.rip(0).valid := frunit.io.mem.req.valid
  mem.io.rip(0).bits.addr := frunit.io.mem.req.bits.addr >> (2 + log2Ceil(config.memDataWidth / 32))
  frunit.io.mem.req.ready := mem.io.rip(0).ready
  mem.io.rop(0) <> frunit.io.mem.resp

  mem.io.wp(0).valid := io.valid
  mem.io.wp(0).bits.enable := io.valid
  mem.io.wp(0).bits.addr := io.addr
  mem.io.wp(0).bits.data := io.data
  io.ready := mem.io.wp(0).ready

  frunit.io.in.exception.en := false.B
  frunit.io.in.exception.pc := 0.U
  frunit.io.in.mispred.en := false.B
  frunit.io.in.mispred.pc := 0.U
  frunit.io.in.mispred.targetpc := 0.U
  frunit.io.in.mispred.taken := false.B
  frunit.io.in.mispred.en := false.B

  frunit.io.out.instruction <> io.instruction
}

class FrUnitSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig
  val TEST_SIZE = 64
  val hexArray: Array[String] = (0 until config.memDepth * 4).map { i =>
    f"${i}%08x"
  }.toArray

  val scalaMem = (0 until config.memDepth * 4).map { i =>
    s"h${hexArray(i)}"
  }.toArray

  "FrUnit" should "work" in {
    test(new FrUnitDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.clock.setTimeout(3000)

      for(i <- 0 until config.memDepth) {
        dut.io.data.poke(s"h${hexArray(i * 4 + 3)}_${hexArray(i * 4 + 2)}_${hexArray(i * 4 + 1)}_${hexArray(i * 4 + 0)}".U)
        dut.io.addr.poke((i).U)
        dut.io.valid.poke(true.B)
        while(!dut.io.ready.peekBoolean()) {
          step()
        }
        step()
      }

      for(i <- 0 until TEST_SIZE) {
        while(!dut.io.instruction.map(_.valid.peekBoolean()).reduce(_ && _)) {
          dut.io.instruction.foreach(_.ready.poke(false.B))
          step()
        }
        dut.io.instruction.foreach(_.ready.poke(true.B))
        (0 until config.nWide).foreach { j =>
          dut.io.instruction(j).bits.expect(scalaMem(i * config.nWide + j).U)
        }
        step()
      }
    }
  }

  "FrUnit" should "emit Verilog" in {
    GenerateVerilog(new FrUnit(new WoodConfig))
  }
}
