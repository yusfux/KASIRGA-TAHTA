package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class LSDummyCache(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSMI(config)))
    val out = Decoupled(new LSMI(config))
  })

  val sramOut = Wire(UInt(config.dataWidth.W))

  val sram = dontTouch(SRAM(config.dcacheDepth, UInt(config.dataWidth.W), 0, 0, 1))

  sram.readwritePorts(0).address   := io.in.bits.addr
  sram.readwritePorts(0).isWrite   := io.in.bits.wStrobe.asUInt.orR
  sram.readwritePorts(0).writeData := io.in.bits.data.asUInt
  sram.readwritePorts(0).enable    := io.in.valid
  sramOut                          := sram.readwritePorts(0).readData

  io.out           <> io.in
  io.out.bits.data := VecInit(Seq.tabulate(4)(j => sramOut(8 * j + 7, 8 * j)))
}
