package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DataBus

class LSWriteback(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(new LSCMI(config)))
    val inPeriph = Flipped(Decoupled(new LSCMI(config)))
    val out      = Output(Vec(1, ValidIO(new DataBus(config))))
  })

  val extender   = Module(new LSExtend(config))
  val arbiter    = Module(new Arbiter(new LSCMI(config), 2))
  val mergedData = Wire(Vec(config.numBytes, UInt(8.W)))
  arbiter.io.in(0) <> io.in
  arbiter.io.in(1) <> io.inPeriph

  extender.io.inlsOp := arbiter.io.out.bits.lsOp

  (0 until config.numBytes).foreach(j => {
    mergedData(j) := MuxCase(
      arbiter.io.out.bits.cacheData(j),
      Array(
        (arbiter.io.out.bits.sqwStrobe(j)) -> arbiter.io.out.bits.sqData(j)
      ).toIndexedSeq
    )
  })

  val dataShiftAmount = arbiter.io.out.bits.addr(1, 0) * 8.U
  val shiftedMerged   = (mergedData.asUInt >> dataShiftAmount)

  extender.io.inData  := shiftedMerged
  io.out(0).bits.data := extender.io.out
  io.out(0).bits.tag  := arbiter.io.out.bits.rdTag
  io.out(0).valid     := arbiter.io.out.valid && !arbiter.io.out.bits.store

  io.in.ready          := 1.B // TODO: think
  io.inPeriph.ready    := 1.B // TODO: think
  arbiter.io.out.ready := 1.B // TODO: think

  dontTouch(io.in.bits.inst) //  testbench only
  dontTouch(io.in.bits.pc) //  testbench only
  dontTouch(io.inPeriph.bits.inst) //  testbench only
  dontTouch(io.inPeriph.bits.pc) //  testbench only
}
