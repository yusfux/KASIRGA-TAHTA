package wood.fru

import chisel3._
import chisel3.util._
import wood.fru.MI
import wood.std.DCRRQueue

class MIStage(numPorts: Int, queueDepth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(new MI())))
    val out = Vec(numPorts, Decoupled(new MI()))
  })

  val q = Module(new DCRRQueue(new MI())(numPorts, queueDepth))
  q.io.in  <> io.in
  q.io.out <> io.out
}
