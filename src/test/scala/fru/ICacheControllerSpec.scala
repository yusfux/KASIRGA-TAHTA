package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.std.{DecoupledSyncReadBlockRAM}
import wood.std.BlockRAMParams
import wood.std.{ReadPortI, ReadPortO, WritePortI}

class ICacheControllerDut(config: WoodConfig, memDepth: Int, cacheDataWidth: Int) extends Module {
  val io = IO(new Bundle() {
    val core = new Bundle() {
      val req = Flipped(DecoupledIO(new ReadPortI(UInt(config.dataWidth.W))(config.addrWidth)))
      val resp = DecoupledIO(new ReadPortO(UInt(config.dataWidth.W))(config.addrWidth))
    }

    val mem = new Bundle {
      val wp = Flipped(Decoupled(new WritePortI(UInt(config.dataWidth.W))(config.addrWidth)))
    }
  })

  val cache = SRAM(config.icacheDepth, UInt(cacheDataWidth.W), 0, 0, 1)
  val mem = Module(new DecoupledSyncReadBlockRAM(UInt(config.dataWidth.W))(new BlockRAMParams(memDepth, 1, 1)))
  val controller = Module(new ICacheController(config))

  controller.io.cache <> cache

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

  val TEST_SIZE    = 1 << scala.util.Random.nextInt(16)
  val ICACHE_DEPTH = 1 << scala.util.Random.nextInt(16)
  val MEM_DEPTH    = 1 << scala.util.Random.nextInt(16)
  //val TEST_SIZE    = 4096
  //val ICACHE_DEPTH = 512
  //val MEM_DEPTH    = 2048

  println(s"[LOG] TEST SIZE: ${TEST_SIZE}")
  println(s"[LOG] CACHE DEPTH: ${ICACHE_DEPTH}")
  println(s"[LOG] MAIN MEM DEPTH: ${MEM_DEPTH}")
                 
  val config = new WoodConfig(icacheDepth = ICACHE_DEPTH)

  // TODO: make these parameters parametric on the Wood.scala after discussing with emre
  val taglen   = 32 - (log2Ceil(ICACHE_DEPTH) + log2Ceil(32 >> 3))
  val datalen  = 32
  val validlen = 1
  val cacheDataWidth =  taglen + datalen + validlen
  val memDepth = MEM_DEPTH

  val dataseq = Seq.fill(MEM_DEPTH)(scala.util.Random.nextInt(Math.pow(2, 32).toInt))
  val addrseq = Seq.range(0, TEST_SIZE * 4, 4)
  val randaddrseq = Seq.fill(TEST_SIZE)(scala.util.Random.nextInt(MEM_DEPTH * 4))

  def initMem(dut: ICacheControllerDut, seq: Seq[Int]) = {
    for(i <- 0 until MEM_DEPTH) {
      dut.io.mem.wp.valid.poke(true.B)
      dut.io.mem.wp.bits.addr.poke((i).U)
      dut.io.mem.wp.bits.data.poke(seq(i).U)
      dut.io.mem.wp.bits.enable.poke(true.B)
      while(!dut.io.mem.wp.ready.peekBoolean()) {
        step()
      }
      step()
    }

    dut.io.mem.wp.valid.poke(false.B)
    step()
  }

  def read(dut: ICacheControllerDut, addrseq: Seq[Int], dataseq: Seq[Int], rand: Boolean = false) = {
    dut.io.core.req.bits.addr.poke(0.U)
    dut.io.core.req.valid.poke(true.B)
    dut.io.core.resp.ready.poke(true.B)

    fork.withRegion(Monitor) {
      for(i <- 0 until addrseq.size) {
        if(rand) {
          dut.io.core.req.valid.poke(false.B)
          step(scala.util.Random.nextInt(4) + 1)
          dut.io.core.req.valid.poke(true.B)
        }
        dut.io.core.req.bits.addr.poke(addrseq(i).U)
        while(!(dut.io.core.req.ready.peekBoolean() && dut.io.core.req.valid.peekBoolean())) {
          step()
        }
        step()
      }
    }.fork {
      for(i <- 0 until addrseq.size) {
        if(rand) {
          dut.io.core.resp.ready.poke(false.B)
          step(scala.util.Random.nextInt(4) + 1)
          dut.io.core.resp.ready.poke(true.B)
        }

        while(!(dut.io.core.resp.ready.peekBoolean() && dut.io.core.resp.valid.peekBoolean())) {
          step()
        }
        dut.io.core.resp.bits.data.expect(dataseq((addrseq(i) / 4) % MEM_DEPTH).U)
        step()
      }
    }.joinAndStep()

  }


  "ICacheController" should "work for each word in order" in {
    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq, dataseq)
    }
  }

  "ICacheController" should "work for each byte in order" in {
    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq.map(x => x / 4), dataseq)
    }
  }

  "ICacheController" should "work for each word in random" in {
    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq, dataseq)
    }
  }

  "ICacheController" should "work for each byte in random" in {
    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq)
    }
  }

  "ICacheController" should "work with random latencies" in {
    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq, dataseq, true)
    }

    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, addrseq.map(x => x / 4), dataseq, true)
    }

    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq, true)
    }

    test(new ICacheControllerDut(config, memDepth, cacheDataWidth)).withAnnotations(GetBackendAnnotation()) { dut =>
      initMem(dut, dataseq)
      read(dut, randaddrseq.map(x => x / 4), dataseq, true)
    }
  }

  "ICacheController" should "emit Verilog" in {
    GenerateVerilog(new ICacheController(new WoodConfig))
  }
}