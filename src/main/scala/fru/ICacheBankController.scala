package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{CorePort, MemPort}
import wood.std.{DCArbiter, DCShifter}


//IMPORTTANT TODO: we can buffer the memory response and its address to match the following unaligned requests
class ICacheBankController(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val core = Vec(config.nWide, new CorePort(config))
    val mem  = new MemPort(config)
  })

  val icachebank = for(i <- 0 until config.nWide) yield {
    val icache = Module(new ICacheBank(config))
    icache
  }

  val icachebankio = new Bundle {
    val core = VecInit(icachebank.map(_.io.core))
    val mem = VecInit(icachebank.map(_.io.mem))
  }

  val shamt = io.core(0).req.bits.addr(config.bankOffset + config.byteOffset - 1, config.byteOffset)
  val corereqshifter  = Module(new DCShifter(io.core(0).req.bits.cloneType)(config.nWide))
  val corerespshifter = Module(new DCShifter(io.core(0).resp.bits.cloneType)(config.nWide))
  val memreqshifter   = Module(new DCShifter(io.mem.req.bits.cloneType)(config.nWide))
  val arbiter = Module(new DCArbiter(io.mem.req.bits.cloneType)(config.nWide, 1))

  corereqshifter.io.in <> io.core.map(_.req)
  corereqshifter.io.shamt := shamt
  (0 until config.nWide) foreach { i => icachebankio.core(i).req <> corereqshifter.io.out(i) }

  corerespshifter.io.in <> icachebankio.core.map(_.resp)
  corerespshifter.io.shamt := config.nWide.U - shamt
  (0 until config.nWide) foreach { i => io.core(i).resp <> corerespshifter.io.out(i) }

  memreqshifter.io.in <> icachebankio.mem.map(_.req)
  memreqshifter.io.shamt := config.nWide.U - shamt
  (0 until config.nWide) foreach { i =>
    arbiter.io.in(i).bits := memreqshifter.io.out(i).bits
    arbiter.io.in(i).valid := memreqshifter.io.out(i).valid
    memreqshifter.io.out(i).ready := io.mem.req.ready &&
      (memreqshifter.io.out(i).bits.addr(config.pcWidth - 1, log2Ceil(config.memDataWidth / config.dataWidth) + config.byteOffset) === arbiter.io.out(0).bits.addr(config.pcWidth - 1, log2Ceil(config.memDataWidth / config.dataWidth) + config.byteOffset))
  }
  
  //-----------------------------------------------------------------------------------------------------------------

  //TODO this is not parametric, it will brake anything other than 128 bit 4 wide config
  val memresp = VecInit(Seq.tabulate(config.memDataWidth / config.dataWidth)(i => io.mem.resp.bits.data((i + 1) * config.dataWidth - 1, i * config.dataWidth)))
  (0 until config.nWide) foreach { i => icachebankio.mem(i).resp.bits.data := memresp(i % (config.memDataWidth / config.dataWidth)) }

  icachebankio.mem.foreach(_.resp.bits.addr <> io.mem.resp.bits.addr)
  icachebankio.mem.foreach(_.resp.valid <> io.mem.resp.valid)
  io.mem.resp.ready := true.B
  io.mem.req <> arbiter.io.out(0)
}