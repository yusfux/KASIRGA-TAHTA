package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DataBus

class LSWriteback(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new LSCMI(config)))
    val out = Output(Vec(1, ValidIO(new DataBus(config))))
  })

  val extender   = Module(new LSExtend(config))
  val mergedData = Wire(Vec(config.numBytes, UInt(8.W)))

  extender.io.inlsOp := io.in.bits.lsOp

  (0 until config.numBytes).foreach(j => {
    mergedData(j) := MuxCase(
      io.in.bits.cacheData(j),
      Array(
        (io.in.bits.wStrobe(j)) -> io.in.bits.sqData(j)
      ).toIndexedSeq
    )
  })

  extender.io.inData  := mergedData.asUInt
  io.out(0).bits.data := extender.io.out
  io.out(0).bits.tag  := io.in.bits.rdTag
  io.out(0).valid     := io.in.valid && !io.in.bits.store

  io.in.ready := 1.B // TODO: arbitrate offchip bus

  dontTouch(io.in.bits.inst) //  testbench only
  dontTouch(io.in.bits.pc) //  testbench only
}
