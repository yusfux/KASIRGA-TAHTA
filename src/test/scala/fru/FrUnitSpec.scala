package wood.fru

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wood.util.{GenerateVerilog, GetBackendAnnotation}
import wood.WoodConfig
import wood.std.{DecoupledSyncReadBlockRAM}
import wood.std.BlockRAMParams
import scala.io.Source
import os._
import scala.collection.mutable.ListBuffer

class FrUnitDut(config: WoodConfig) extends Module {
  val io = IO(new Bundle() {
    val instruction = Vec(config.nWide, DecoupledIO(UInt(config.xlen.W)))
    val pc = Vec(config.nWide, UInt(config.xlen.W))

    val exception_en = Input(Bool())
    val exception_pc = Input(UInt(config.pcWidth.W))

    val data = Input(UInt(config.memDataWidth.W))
    val addr = Input(UInt(config.addrWidth.W))
    val valid = Input(Bool())
    val ready = Output(Bool())
    val frreset = Input(Bool())
  })

  val frunit = Module(new FrUnit(config))
  frunit.reset := io.frreset
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

  frunit.io.in.exception.en := io.exception_en
  frunit.io.in.exception.pc := io.exception_pc
  frunit.io.in.mispred.en := false.B
  frunit.io.in.mispred.pc := 0.U
  frunit.io.in.mispred.targetpc := 0.U
  frunit.io.in.mispred.taken := false.B
  frunit.io.in.mispred.en := false.B

  frunit.io.out.instruction <> io.instruction
  io.pc.zipWithIndex.foreach { case (pc, i) => pc := frunit.io.out.pc(i) }
}

class FrUnitSpec extends AnyFlatSpec with ChiselScalatestTester {
  val mainmem = Source.fromFile(s"${os.pwd}/src/test/c/build/main.hex").getLines().toList
  val json = ujson.read(os.read(os.pwd / RelPath("src/test/c/build/spike_trace.json")))
  val config = new WoodConfig(nWide = 8, memDepth = mainmem.length / 4, pcInitAddr = "h8000_0000")
  var pc = BigInt(config.pcInitAddr.stripPrefix("h").replace("_", ""), 16)

  //val TEST_SIZE = json.arr.length
  val TEST_SIZE = json.arr.length
  val pclist = ListBuffer[String]()
  val instlist = ListBuffer[String]()


  "FrUnit" should "spike" in {
    test(new FrUnitDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      // INITIALIZATION FOR MEMORY AND CACHE ---------------------------
      dut.clock.setTimeout(3000)
      dut.io.frreset.poke(true.B)
      for(i <- 0 until config.memDepth) {
        dut.io.data.poke(s"h${mainmem(i * 4 + 3)}_${mainmem(i * 4 + 2)}_${mainmem(i * 4 + 1)}_${mainmem(i * 4)}".U)
        dut.io.addr.poke((i).U)
        dut.io.valid.poke(true.B)
        while(!dut.io.ready.peekBoolean()) {
          step()
        }
        step()
      }
      dut.io.valid.poke(false.B)
      dut.io.frreset.poke(false.B)
      dut.io.exception_en.poke(false.B)
      dut.io.exception_pc.poke(0.U)
      // INITIALIZATION FOR MEMORY AND CACHE ---------------------------


      // TESTING -------------------------------------------------------
      dut.io.instruction.foreach(_.ready.poke(false.B))
      var idx = 0
      while(idx < TEST_SIZE) {
        dut.io.exception_en.poke(false.B)
        dut.io.exception_pc.poke(0.U)

        while(!dut.io.instruction.map(_.valid.peekBoolean()).reduce(_ || _)) {
          dut.io.instruction.foreach(_.ready.poke(false.B))
          step()
        }

        step(scala.util.Random.nextInt(10) + 1)
        dut.io.instruction.foreach(_.ready.poke(true.B))
        var flag = false
        (0 until config.nWide).foreach { j =>
          val jsonpc = json.arr(idx)("pc").str.stripPrefix("0x")
          if(!flag) {
            if(dut.io.instruction(j).valid.peekBoolean()) {
              //dut.io.instruction(j).bits.expect(s"h${mainmem(i * config.nWide + j)}".U)
              //dut.io.pc(j).expect(pc.U)
              if(!dut.io.pc(j).peek().litValue.toString(16).equals(jsonpc)) {
                flag = true
                dut.io.exception_en.poke(true.B)
                dut.io.exception_pc.poke(("h" + jsonpc).U)
                //println(s"json pc${i * config.nWide + j}: ${jsonpc}")
                //println(s"expected pc: ${pc.toString(16)}")
              } else {
                idx += 1
                pclist += dut.io.pc(j).peek().litValue.toString(16)
                instlist += dut.io.instruction(j).bits.peek().litValue.toString(16).reverse.padTo(8, '0').reverse
              }
              //println(s"peeked value: ${dut.io.pc(j).peek().litValue.toString(16)}")
              //println(s"equalness: ${pc.toString(16).equals(dut.io.pc(j).peek().litValue.toString(16))}")
            }
            if(!flag) {
              pc = pc + 4
            } else {
              pc = BigInt(jsonpc, 16)
            }
          }
        }
        step()
      }
      // TESTING -------------------------------------------------------
      val filepc = os.pwd / RelPath("src/test/c/build/pc_trace.txt")
      val fileinst = os.pwd / RelPath("src/test/c/build/inst_trace.txt")
      os.write.over(filepc, pclist.mkString("\n"))
      os.write.over(fileinst, instlist.mkString("\n"))

      val jsonpc = json.arr.map(x => x("pc").str.stripPrefix("0x")).toList.take(pclist.size)
      val jsoninst = json.arr.map(x => x("inst").str.stripPrefix("0x")).toList.take(instlist.size)
      assert(jsonpc == pclist)
      assert(jsoninst == instlist)

    }
  }

  "FrUnit" should "work" in {
    test(new FrUnitDut(config)).withAnnotations(GetBackendAnnotation()) { dut =>
      // INITIALIZATION FOR MEMORY AND CACHE ---------------------------
      dut.clock.setTimeout(3000)
      dut.io.frreset.poke(true.B)
      for(i <- 0 until config.memDepth) {
        dut.io.data.poke(s"h${mainmem(i * 4 + 3)}_${mainmem(i * 4 + 2)}_${mainmem(i * 4 + 1)}_${mainmem(i * 4)}".U)
        dut.io.addr.poke((i).U)
        dut.io.valid.poke(true.B)
        while(!dut.io.ready.peekBoolean()) {
          step()
        }
        step()
      }
      dut.io.valid.poke(false.B)
      dut.io.frreset.poke(false.B)
      // INITIALIZATION FOR MEMORY AND CACHE ---------------------------


      // TESTING -------------------------------------------------------
      dut.io.instruction.foreach(_.ready.poke(false.B))
      for(i <- 0 until config.memDepth / config.nWide - 1) {
        while(!dut.io.instruction.map(_.valid.peekBoolean()).reduce(_ || _)) {
          dut.io.instruction.foreach(_.ready.poke(false.B))
          step()
        }

        step(scala.util.Random.nextInt(10) + 1)
        dut.io.instruction.foreach(_.ready.poke(true.B))
        (0 until config.nWide).foreach { j =>
          if(dut.io.instruction(j).valid.peekBoolean()) {
            dut.io.instruction(j).bits.expect(s"h${mainmem(i * config.nWide + j)}".U)
            dut.io.pc(j).expect(pc.U)

            //println(s"json pc${i * config.nWide + j}: ${jsonpc}")
            //println(s"expected pc: ${pc.toString(16)}")
            //println(s"peeked value: ${dut.io.pc(j).peek().litValue.toString(16)}")
            //println(s"equalness: ${pc.toString(16).equals(dut.io.pc(j).peek().litValue.toString(16))}")
          }
          pc = pc + 4
        }
        step()
      }
      // TESTING -------------------------------------------------------
    }
  }


  "FrUnit" should "emit Verilog" in {
    GenerateVerilog(new FrUnit(new WoodConfig))
  }
}
