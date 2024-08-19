package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{ALU, MI}

class LSAtom(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val inPass  = Flipped(Decoupled(new LSMI(config)))
    val inCache = Flipped(Decoupled(new LSMI(config)))
    val outSQ   = Decoupled(new LSMI(config)) // TODO ready
    val outEx   = Decoupled(new MI(config))
  })

  val alu        = Module(new ALU(config))
  val selfMerged = Wire(Decoupled(new LSMI(config)))

  selfMerged       <> io.inCache
  selfMerged.ready := io.outSQ.ready & io.outEx.ready
  alu.io.out.ready := selfMerged.ready

  (0 until config.dataWidth / 8).foreach(j => {
    val atomByteUpdate = io.inPass.bits.wStrobe(j) && (io.inPass.bits.addr === io.inCache.bits.addr)

    selfMerged.bits.data(j) := MuxCase(
      io.inCache.bits.data(j),
      Array(
        (io.inCache.bits.wStrobe(j)) -> io.inCache.bits.data(j),
        (atomByteUpdate)             -> io.inPass.bits.data(j)
      ).toIndexedSeq
    )
    selfMerged.bits.wStrobe(j) := io.inCache.bits.wStrobe(j) | atomByteUpdate
  })

  alu.io.in.bits         := DontCare
  alu.io.in.bits.inst    := selfMerged.bits.inst // debug only
  alu.io.in.bits.exOp    := selfMerged.bits.exOp
  alu.io.in.bits.rs1Data := selfMerged.bits.result.asUInt
  alu.io.in.bits.rs2Data := selfMerged.bits.data.asUInt
  alu.io.in.valid        := selfMerged.valid

  io.inPass.ready  := alu.io.in.ready
  io.inCache.ready := alu.io.in.ready

  io.outSQ.bits      := selfMerged.bits
  io.outSQ.valid     := selfMerged.valid
  io.outSQ.bits.addr := selfMerged.bits.addr

  val tmp = Wire(DecoupledIO(new MI(config)))
  tmp              := DontCare
  tmp.bits.rdData  := selfMerged.bits.data.asUInt
  tmp.bits.rdTag   := selfMerged.bits.rdTag
  tmp.bits.writeRf := selfMerged.bits.wStrobe.asUInt.orR
  io.outEx         <> tmp

  dontTouch(io.inPass.bits.inst) //  testbench only
  dontTouch(io.inPass.bits.pc) //  testbench only
  dontTouch(io.inCache.bits.inst) //  testbench only
  dontTouch(io.inCache.bits.pc) //  testbench only
}
