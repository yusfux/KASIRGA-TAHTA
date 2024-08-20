package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.{CorePort, MemPortW}

class ICacheControllerDut(config: WoodConfig, mmDepth: Int, cacheDataWidth: Int) extends Module {
  val io = IO(new Bundle() {
    val core = new CorePort(config)
    val mem  = new MemPortW(config)
  })

  val controller = Module(new ICacheController(config))
  val cache      = SRAM(config.iCacheDepth, UInt(cacheDataWidth.W), 0, 0, 1)
  val mem        = SyncReadMem(mmDepth, UInt(config.mmInterfaceWidth.W))

  controller.io.core  <> io.core
  controller.io.cache <> cache

  controller.io.mem.req.ready      := true.B
  controller.io.mem.resp.valid     := true.B
  controller.io.mem.resp.bits.data := mem.read(controller.io.mem.req.bits.addr >> (config.byteOffset))

  when(io.mem.req.valid) {
    mem.write(io.mem.req.bits.addr, io.mem.req.bits.data)
  }

  io.mem.req.ready := true.B
}

class ICacheControllerSpec extends AnyFlatSpec with ChiselScalatestTester {

  val TEST_SIZE    = 1 << scala.util.Random.nextInt(16)
  val ICACHE_DEPTH = 1 << scala.util.Random.nextInt(16)
  val MEM_DEPTH    = 1 << scala.util.Random.nextInt(16)

  println(s"[LOG] TEST SIZE: ${TEST_SIZE}")
  println(s"[LOG] CACHE DEPTH: ${ICACHE_DEPTH}")
  println(s"[LOG] MAIN MEM DEPTH: ${MEM_DEPTH}")

  val config = new WoodConfig(iCacheDepth = ICACHE_DEPTH, nWide = 1)

  // TODO: make these parameters parametric on the Wood.scala after discussing with emre
  val taglen         = 32 - (log2Ceil(config.iCacheDepth) + config.byteOffset)
  val datalen        = 32
  val validlen       = 1
  val cacheDataWidth = taglen + datalen + validlen
  val mmDepth        = MEM_DEPTH

  val dataseq     = Seq.fill(MEM_DEPTH)(scala.util.Random.nextInt(Math.pow(2, 32).toInt))
  val addrseq     = Seq.range(0, TEST_SIZE * 4, 4)
  val randaddrseq = Seq.fill(TEST_SIZE)(scala.util.Random.nextInt(MEM_DEPTH * 4))

  def initMem(dut: ICacheControllerDut, seq: Seq[Int]) = {
    for (i <- 0 until MEM_DEPTH) {
      dut.io.mem.req.bits.addr.poke(i.U)
      dut.io.mem.req.valid.poke(true.B)
      dut.io.mem.req.bits.data.poke(seq(i).U)
      step()
    }
    dut.io.mem.req.valid.poke(false.B)
    step()
  }

  def read(dut: ICacheControllerDut, addrseq: Seq[Int], dataseq: Seq[Int], rand: Boolean = false) = {
    dut.io.core.req.bits.addr.poke(0.U)
    dut.io.core.req.valid.poke(true.B)
    dut.io.core.resp.ready.poke(true.B)

    fork
      .withRegion(Monitor) {
        for (i <- 0 until addrseq.size) {
          if (rand) {
            dut.io.core.req.valid.poke(false.B)
            step(scala.util.Random.nextInt(4) + 1)
            dut.io.core.req.valid.poke(true.B)
          }
          dut.io.core.req.bits.addr.poke(addrseq(i).U)
          while (!(dut.io.core.req.ready.peekBoolean() && dut.io.core.req.valid.peekBoolean())) {
            step()
          }
          step()
        }
      }
      .fork {
        for (i <- 0 until addrseq.size) {
          if (rand) {
            dut.io.core.resp.ready.poke(false.B)
            step(scala.util.Random.nextInt(4) + 1)
            dut.io.core.resp.ready.poke(true.B)
          }

          while (!(dut.io.core.resp.ready.peekBoolean() && dut.io.core.resp.valid.peekBoolean())) {
            step()
          }
          dut.io.core.resp.bits.data.expect(dataseq((addrseq(i) / 4) % MEM_DEPTH).U)
          step()
        }
      }
      .joinAndStep()

  }

  "ICacheController" should "work for each word in order" in {
    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq, dataseq)
    }
  }

  "ICacheController" should "work for each byte in order" in {
    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq.map(x => x / 4), dataseq)
    }
  }

  "ICacheController" should "work for each word in random" in {
    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq, dataseq)
    }
  }

  "ICacheController" should "work for each byte in random" in {
    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq)
    }
  }

  "ICacheController" should "work with random latencies" in {
    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq, dataseq, true)
    }

    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq.map(x => x / 4), dataseq, true)
    }

    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq, true)
    }

    test(new ICacheControllerDut(config, mmDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq, true)
    }
  }

  "ICacheController" should "emit Verilog" in {
    GenerateVerilog(new ICacheController(new WoodConfig))
  }
}
