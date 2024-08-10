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
    io.arfBus(j).bits.rd  := io.in(j).bits.rd
    io.arfBus(j).bits.tag := io.in(j).bits.rdTag

    io.in(j).ready := allReady.asUInt.andR

    rdOverriden(j) := ((j + 1) until config.nWide).foldRight(false.B) { (k, acc) =>
      val rdM = (io.in(j).bits.rd === io.in(k).bits.rd) & io.in(k).bits.writeRf.asBool
      acc || rdM
    }

    val attemptArfWrite = (io.in(j).valid & io.in(j).bits.writeRf.asBool)
    val arfWrite        = !rdOverriden(j) & attemptArfWrite
    io.arfBus(j).valid := arfWrite

    val arfTagToCommitBus = io.in(j).bits.arfValid & arfWrite

    io.commitedBus(j).bits.tag := Mux(arfTagToCommitBus, io.in(j).bits.arfTag, io.in(j).bits.rdTag)
    io.commitedBus(j).valid    := Mux(arfTagToCommitBus, io.in(j).bits.arfValid, attemptArfWrite & io.in(j).bits.arfValid)

    dontTouch(io.in(j).bits.inst) // for testbench only
    dontTouch(io.in(j).bits.pc) // for testbench only
    dontTouch(io.in(j).bits.flushed) // for testbench only
  })
}
