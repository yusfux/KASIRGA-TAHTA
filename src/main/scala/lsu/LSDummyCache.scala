package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCPipelineRegister

class LSDummyCache(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSMI(config)))
    val out = Decoupled(new LSMI(config))
  })

  val numBytes = config.dCacheLineWidth / 8

  val pReg      = Module(new DCPipelineRegister(new LSMI(config))(1))
  val pRegCache = Module(new DCPipelineRegister(new LSMI(config))(1))
  val sram      = dontTouch(SRAM(config.dcacheDepth, UInt(config.mmInterfaceWidth.W), 0, 0, 1))
  val self      = Wire(Decoupled(new LSMI(config)))

  val sramOut = Wire(UInt(config.mmInterfaceWidth.W))

  sram.readwritePorts(0).address   := io.in.bits.addr(log2Ceil(config.dcacheDepth), log2Ceil(config.mmInterfaceWidth) - 3)
  sram.readwritePorts(0).isWrite   := io.in.bits.wStrobe.asUInt.orR
  sram.readwritePorts(0).writeData := io.in.bits.memDataWrite.asUInt
  sram.readwritePorts(0).enable    := io.in.valid
  sramOut                          := sram.readwritePorts(0).readData

  pRegCache.io.valids(0) := io.in.valid
  pReg.io.valids(0)      := pRegCache.io.out.valid
  pRegCache.io.flush     := 0.B // TODO: think
  pReg.io.flush          := 0.B // TODO: think

  pRegCache.io.in       <> io.in
  self                  <> pRegCache.io.out
  self.bits.memDataRead := VecInit(Seq.tabulate(numBytes)(j => sramOut(8 * j + 7, 8 * j)))
  pReg.io.in            <> self
  io.out                <> pReg.io.out
}
