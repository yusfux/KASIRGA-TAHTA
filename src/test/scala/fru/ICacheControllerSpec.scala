package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.std.DecoupledSyncReadBlockRAM
import wood.std.BlockRAMParams
import wood.std.{ReadPortI, ReadPortO, WritePortI}

case class ICacheControllerTestParams(
  coreAddrWidth:  Int,
  memAddrWidth:   Int,
  cacheAddrWidth: Int,
  coreDataWidth:  Int,
  memDataWidth:   Int,
  cacheDataWidth: Int,

  cacheDepth:     Int,
  memDepth:       Int
)

class ICacheControllerDut(p: ICacheControllerTestParams) extends Module {
  val cache = Module(new DecoupledSyncReadBlockRAM(UInt((p.cacheDataWidth).W))(new BlockRAMParams(p.cacheDepth, 1, 1)))
  val mem = Module(new DecoupledSyncReadBlockRAM(UInt((32).W))(new BlockRAMParams(p.memDepth, 1, 1)))
  val controller = Module(new ICacheController(new WoodConfig(
    iCacheDepth = p.cacheDepth
  )))

  val io = IO(new Bundle() {
    val core = new Bundle() {
      val req = Flipped(DecoupledIO(new ReadPortI(UInt(p.coreDataWidth.W))(p.coreAddrWidth)))
      val resp = DecoupledIO(new ReadPortO(UInt(p.coreDataWidth.W))(p.coreAddrWidth))
    }

    val mem = new Bundle {
      val wp = Flipped(Decoupled(new WritePortI(UInt(p.memDataWidth.W))(p.memAddrWidth)))
    }
  })

  cache.io.rip(0) <> controller.io.cache.req.read
  cache.io.rop(0) <> controller.io.cache.resp
  cache.io.wp(0) <>  controller.io.cache.req.write

  mem.io.rip(0).valid := controller.io.mem.req.valid
  mem.io.rip(0).bits.addr := controller.io.mem.req.bits.addr >> 2
  controller.io.mem.req.ready := mem.io.rip(0).ready

  mem.io.rop(0) <> controller.io.mem.resp
  mem.io.wp(0).valid := io.mem.wp.valid
  mem.io.wp(0).bits.enable := io.mem.wp.bits.enable
  mem.io.wp(0).bits.addr := io.mem.wp.bits.addr
  mem.io.wp(0).bits.data := io.mem.wp.bits.data
  io.mem.wp.ready := mem.io.wp(0).ready


  controller.io.core.req <> io.core.req
  io.core.resp <> controller.io.core.resp 
}

class ICacheControllerSpec extends AnyFlatSpec with ChiselScalatestTester {

  val ICACHE_DEPTH = 1024
  val MEM_DEPTH = 1024
  val TEST_SIZE = 1024

  val wc = new WoodConfig(
    iCacheDepth = ICACHE_DEPTH
  )

  val icp = new ICacheControllerTestParams(
    coreAddrWidth  = wc.addrWidth,
    memAddrWidth   = wc.addrWidth,
    cacheAddrWidth = log2Ceil(wc.iCacheDepth),
    coreDataWidth  = wc.dataWidth,
    memDataWidth   = wc.dataWidth,
    cacheDataWidth = 1 + (wc.xlen - log2Ceil(wc.iCacheDepth) - log2Ceil(wc.xlen >> 3)) + wc.dataWidth,
    cacheDepth     = wc.iCacheDepth,
    memDepth       = MEM_DEPTH
  )

  "ICacheController" should "work" in {
    test(new ICacheControllerDut(icp)).withAnnotations(GetBackendAnnotation()) { dut =>

      def initMem(dataseq: Seq[Int]) = {
        for(i <- 0 until icp.memDepth) {
          dut.io.mem.wp.valid.poke(true.B)
          dut.io.mem.wp.bits.addr.poke((i).U)
          dut.io.mem.wp.bits.data.poke(dataseq(i).U)
          dut.io.mem.wp.bits.enable.poke(true.B)
          while(!dut.io.mem.wp.ready.peekBoolean()) {
            step()
          }
          step()
        }
        dut.io.mem.wp.valid.poke(false.B)
        dut.io.mem.wp.bits.addr.poke(0.U)
        dut.io.mem.wp.bits.data.poke(0.U)
        dut.io.mem.wp.bits.enable.poke(false.B)
        step()
    
      }

      def read(addr: Int, data: Int): UInt = {
        dut.io.core.req.bits.addr.poke(addr)
        dut.io.core.req.valid.poke(true.B)
        while(!dut.io.core.req.ready.peekBoolean()) {
          step()
        }
        step()
        dut.io.core.req.valid.poke(false.B)
        dut.io.core.resp.ready.poke(true.B)

        while(!dut.io.core.resp.valid.peekBoolean()) {
          step()
        }
        step()
        dut.io.core.resp.ready.poke(false.B)
        dut.io.core.resp.bits.data.expect(data.U)
        val dataout = dut.io.core.resp.bits.data.peek()

        dataout
      }

      dut.io.core.req.valid.poke(false.B)
      dut.io.core.req.bits.addr.poke(0.U)
      dut.io.core.resp.ready.poke(false.B)
      step()

      val dataseq = Seq.fill(icp.memDepth)(scala.util.Random.nextInt(Math.pow(2, 32).toInt))
      val idx = Seq.fill(TEST_SIZE)(scala.util.Random.nextInt(icp.memDepth))

      initMem(dataseq)
      for(i <- idx) {
        val expected = dataseq(i % icp.memDepth)
        val addr = i << 2
        read(addr, expected)
      }
  }
}

  "ICacheController" should "emit Verilog" in {
    GenerateVerilog(new ICacheController(new WoodConfig))
    
  }
}