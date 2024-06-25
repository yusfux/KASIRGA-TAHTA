package wood.exu

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.DCCrossbar

class ExecuteStage(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(numPorts, Decoupled(new MI())))
    val forwardBuses = Vec(numPorts, Decoupled(new ForwardBus()))

    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val crossbarInterfaceList = List(ExConfig.numALUs)
  val crossbar              = Module(new DCCrossbar(new MI())(numPorts, crossbarInterfaceList))

  val alus = Seq.tabulate(numPorts) { j =>
    Module(new ALU())
  }

  (0 until numPorts).foreach(j => {
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
