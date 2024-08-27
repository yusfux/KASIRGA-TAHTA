package wood.lsu

import chiseltest._
import chisel3._
import chisel3.util._

import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import scala.collection.mutable.ArrayBuffer

class DCacheDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = new Bundle {
      val req = Flipped(DecoupledIO(new Bundle {
        val addr  = UInt(32.W)
        val data  = UInt(128.W)
        val wstrb = Vec(16, Bool())
        val wen   = Bool()
      }))
      val resp = DecoupledIO(new Bundle {
        val data = UInt(128.W)
      })
    }

    val debug = new Bundle {
      val valid = Output(Bool())
      val addr  = Output(UInt(32.W))
      val data  = Output(UInt(128.W))
    }
  })

  val dcache = Module(new DCache(config))
  val mem    = SyncReadMem(config.mmDepth, UInt(config.mmInterfaceWidth.W))

  dcache.io.core <> io.core

  dcache.io.mem.req.ready      := true.B
  dcache.io.mem.resp.valid     := Mux(RegNext(dcache.io.mem.req.valid && dcache.io.mem.req.bits.wen), false.B, true.B)
  dcache.io.mem.resp.bits.data := mem.read(dcache.io.mem.req.bits.addr >> (config.byteOffset + config.memOffset))

  when(dcache.io.mem.req.valid && dcache.io.mem.req.bits.wen) {
    mem.write(dcache.io.mem.req.bits.addr >> (config.byteOffset + config.memOffset), dcache.io.mem.req.bits.data)
  }

  io.debug.valid := dcache.io.mem.req.valid && dcache.io.mem.req.bits.wen
  io.debug.addr  := dcache.io.mem.req.bits.addr
  io.debug.data  := dcache.io.mem.req.bits.data
}

class DCacheSpec extends AnyFlatSpec with ChiselScalatestTester {
  val config = new WoodConfig

  val MEM_DEPTH = 4096
  val TEST_SIZE = 4096

  val bytes   = Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")
  val dataseq = ArrayBuffer.fill(MEM_DEPTH)(scala.util.Random.shuffle(bytes.toSeq).mkString + scala.util.Random.shuffle(bytes.toSeq).mkString)

  "DCache" should "work" in {
    test(new DCacheDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.core.req.bits.wstrb.foreach(_.poke(true.B))
      for (i <- 0 until TEST_SIZE) {
        dut.io.core.req.valid.poke(true.B)
        dut.io.core.req.bits.addr.poke((i * 16).U)
        dut.io.core.req.bits.data.poke(s"h${dataseq(i)}".U)
        dut.io.core.req.bits.wen.poke(true.B)
        while (!dut.io.core.req.ready.peekBoolean()) {
          step()
        }
        step()
      }

      for (i <- 0 until TEST_SIZE) {
        dut.io.core.req.valid.poke(true.B)
        dut.io.core.req.bits.addr.poke((i * 16).U)
        dut.io.core.req.bits.data.poke(s"h${dataseq(i)}".U)
        dut.io.core.req.bits.wen.poke(false.B)
        while (!dut.io.core.req.ready.peekBoolean()) {
          step()
        }
        step()

        dut.io.core.resp.ready.poke(true.B)
        while (!dut.io.core.resp.valid.peekBoolean()) {
          step()
        }
        dut.io.core.resp.bits.data.expect(s"h${dataseq(i)}".U)
        step()
      }
    }
  }

  "DCache" should "work random" in {
    test(new DCacheDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      dut.io.core.req.bits.wstrb.foreach(_.poke(true.B))
      for (i <- 0 until MEM_DEPTH) {
        dut.io.core.req.valid.poke(true.B)
        dut.io.core.req.bits.addr.poke((i * 16).U)
        dut.io.core.req.bits.data.poke(s"h${dataseq(i)}".U)
        dut.io.core.req.bits.wen.poke(true.B)
        while (!dut.io.core.req.ready.peekBoolean()) {
          step()
        }
        step()
      }

      dut.io.core.req.valid.poke(false.B)
      dut.io.core.req.bits.addr.poke(0.U)
      dut.io.core.req.bits.data.poke(0.U)
      dut.io.core.req.bits.wen.poke(false.B)
      step()

      for (i <- 0 until TEST_SIZE) {
        val readcnt  = scala.util.Random.nextInt(10) + 1
        val writecnt = scala.util.Random.nextInt(10) + 1
        for (i <- 0 until readcnt) {
          val addr = scala.util.Random.nextInt(MEM_DEPTH)
          dut.io.core.req.valid.poke(true.B)
          dut.io.core.req.bits.addr.poke((addr * 16).U)
          dut.io.core.req.bits.wen.poke(false.B)
          while (!dut.io.core.req.ready.peekBoolean()) {
            step()
          }
          step()

          dut.io.core.resp.ready.poke(true.B)
          while (!dut.io.core.resp.valid.peekBoolean()) {
            step()
          }
          dut.io.core.resp.bits.data.expect(s"h${dataseq(addr)}".U)
          step()
        }

        for (i <- 0 until writecnt) {
          val wstrb = scala.util.Random.nextInt(1 << 16 - 1).toBinaryString.padTo(16, '0')
          val addr  = scala.util.Random.nextInt(MEM_DEPTH)
          val data  = scala.util.Random.shuffle(bytes.toSeq).mkString + scala.util.Random.shuffle(bytes.toSeq).mkString
          dataseq(addr) = updateData(wstrb, data, dataseq(addr))
          dut.io.core.req.valid.poke(true.B)
          dut.io.core.req.bits.addr.poke((addr * 16).U)
          for (i <- 0 until 16) dut.io.core.req.bits.wstrb(i).poke((wstrb(15 - i) == '1').B)
          dut.io.core.req.bits.data.poke(s"h${data}".U)
          dut.io.core.req.bits.wen.poke(true.B)
          while (!dut.io.core.req.ready.peekBoolean()) {
            step()
          }
          step()
        }
      }
    }

    def updateData(wstrb: String, data0: String, data1: String): String = {
      var updatedData = ""
      for (i <- 0 until 16) {
        if (wstrb(i) == '1')
          updatedData += data0.substring(2 * i, 2 * i + 2)
        else
          updatedData += data1.substring(2 * i, 2 * i + 2)
      }
      println(s"wstrb: ${wstrb}, data: ${data0}, data: ${data1}, updatedData: ${updatedData}")
      return updatedData
    }
  }

  "DCache" should "emit Verilog" in {
    GenerateVerilog(new DCache(config))
  }
}
