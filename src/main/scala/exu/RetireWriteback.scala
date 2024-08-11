package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class RetireWritebackStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in          = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val arfBus      = Vec(config.nWide, ValidIO(new ARFBus(config)))
    val commitedBus = Vec(config.nWide, Decoupled(new Tag(config)))
  })

  val allReady = Wire(Vec(config.nWide, Bool())).suggestName("allInValid")
  allReady := io.commitedBus.map(_.ready)

  val rdOverriden = Wire(Vec(config.nWide, Bool()))

  (0 until config.nWide).foreach(j => {
    rdOverriden(j) := ((j + 1) until config.nWide).foldRight(false.B) { (k, acc) =>
      val rdM = (io.in(j).bits.rd === io.in(k).bits.rd) & io.in(k).bits.writeRf.asBool & io.in(k).valid
      acc || rdM
    }

    io.arfBus(j).bits.rd  := io.in(j).bits.rd
    io.arfBus(j).bits.tag := io.in(j).bits.rdTag
    io.arfBus(j).valid := MuxCase(
      0.U,
      Array(
        (!io.in(j).valid)                                -> 0.U,
        (io.in(j).bits.flushed)                          -> 0.U,
        (!rdOverriden(j) & io.in(j).bits.writeRf.asBool) -> 1.U
      ).toIndexedSeq
    )

    io.commitedBus(j).bits.tag := MuxCase(
      io.in(j).bits.rdTag,
      Array(
        (!io.in(j).valid)        -> DontCare,
        (io.in(j).bits.flushed)  -> io.in(j).bits.rdTag,
        (rdOverriden(j))         -> io.in(j).bits.rdTag,
        (io.in(j).bits.arfValid) -> io.in(j).bits.arfTag
      ).toIndexedSeq
    )

    io.commitedBus(j).valid := MuxCase(
      1.U,
      Array(
        (!io.in(j).valid)         -> 0.U,
        (io.in(j).bits.flushed)   -> 1.U,
        (!io.in(j).bits.arfValid) -> 0.U
      ).toIndexedSeq
    )

    io.in(j).ready := allReady.asUInt.andR

    dontTouch(io.in(j).bits.inst) // for testbench only
    dontTouch(io.in(j).bits.pc) // for testbench only
    dontTouch(io.in(j).bits.flushed) // for testbench only
  })
}
