package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.std.{DecoupledSyncReadBlockRAM}
import wood.std.BlockRAMParams
import wood.std.{ReadPortI, ReadPortO}

class ICacheBankControllerDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle() {
    val core = Vec(config.nWide, new Bundle() {
      val req = Flipped(DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth)))
      val resp = DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth))
    })

    val data = Input(UInt(config.memDataWidth.W))
    val addr = Input(UInt(config.addrWidth.W))
    val valid = Input(Bool())
    val ready = Output(Bool())
  })

  val icachebankcontroller = Module(new ICacheBankController(config))
  val mem = Module(new DecoupledSyncReadBlockRAM(UInt(config.memDataWidth.W))(new BlockRAMParams(config.memDepth, 1, 1)))

  mem.io.rip(0).valid := icachebankcontroller.io.mem.req.valid
  mem.io.rip(0).bits.addr := icachebankcontroller.io.mem.req.bits.addr >> (2 + log2Ceil(config.memDataWidth / 32))
  icachebankcontroller.io.mem.req.ready := mem.io.rip(0).ready
  mem.io.rop(0) <> icachebankcontroller.io.mem.resp

  mem.io.wp(0).valid := io.valid
  mem.io.wp(0).bits.enable := io.valid
  mem.io.wp(0).bits.addr := io.addr
  mem.io.wp(0).bits.data := io.data
  io.ready := mem.io.wp(0).ready

  icachebankcontroller.io.core <> io.core
}

class ICacheBankControllerSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig
  val TEST_SIZE = 1024
  val hexArray: Array[String] = (0 until config.memDepth * 4).map { i =>
    f"${i}%08x"
  }.toArray

  "ICacheBankController" should "work" in {
    test(new ICacheBankControllerDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
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

      for(i <- 0 until config.nWide) {
        dut.io.core(i).req.valid.poke(false.B)
        dut.io.core(i).req.bits.addr.poke((4 * i).U)
        dut.io.core(i).resp.ready.poke(true.B)
      }

      for(i <- 0 until TEST_SIZE) {
        while(!(dut.io.core.map(_.req.ready.peekBoolean()).reduce(_ && _))) {
          dut.io.core.foreach(_.req.valid.poke(false.B))
          step()
        }

        for(j <- 0 until config.nWide) {
          dut.io.core(j).req.bits.addr.poke((i * 16 + j * 4 + i * 4).U)
          dut.io.core(j).req.valid.poke(true.B)
        }
        step()
      }
    }
  }

  "ICacheBankController" should "emit Verilog" in {
    GenerateVerilog(new ICacheBankController(new WoodConfig))
  }
}
