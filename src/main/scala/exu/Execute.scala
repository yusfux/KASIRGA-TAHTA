package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.DCCrossbar

class ExecuteStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBuses = Vec(config.nWide, Decoupled(new ForwardBus(config)))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val crossbarInterfaceList = List(config.numALUs)
  val crossbar              = Module(new DCCrossbar(new MI(config))(config.nWide, crossbarInterfaceList))

  val alus = Seq.tabulate(config.nWide) { j =>
    Module(new ALU(config))
  }

  (0 until config.nWide).foreach(j => {
    crossbar.io.sel(j) := io.in(j).bits.exEngine === ExEngine.alu.asUInt

    crossbar.io.in(j) <> io.in(j)
    alus(j).io.mi     <> crossbar.io.out(crossbarInterfaceList.length - 1)(j)

    io.out(j).bits  := alus(j).io.out.bits
    io.out(j).valid := alus(j).io.out.valid

    io.forwardBuses(j).bits.data := alus(j).io.out.bits.rd_data
    io.forwardBuses(j).bits.tag  := alus(j).io.out.bits.rd_tag
    io.forwardBuses(j).valid     := alus(j).io.out.valid

    alus(j).io.out.ready := io.out(j).ready & io.forwardBuses(j).ready
  })
}
