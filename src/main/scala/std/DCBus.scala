package wood.std

import chisel3._
import chisel3.util._

/*
@note DCBus connects an interface to multiple interfaces. All recipients of a port has to be ready.
@param numPorts: number of ports
@param numInterfaces: number of interfaces
@examples
 * Bus connecting 8 port single interface to two output interfaces each having 8 ports.
  new DCBus(UInt(8.W))(8,2)
 */
class DCBus[T <: Data](gen: T)(numPorts: Int, numInterfaces: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numInterfaces, Vec(numPorts, Decoupled(gen.cloneType)))
  })

  val outReady = Wire(Vec(numPorts, Vec(numInterfaces, Bool()))) // transpose matrix

  (0 until numInterfaces).foreach(j => {
    (0 until numPorts).foreach(k => {
      io.out(j)(k).bits  := io.in(k).bits
      io.out(j)(k).valid := io.in(k).valid
    })
  })

  (0 until numPorts).foreach(k => {
    (0 until numInterfaces).foreach(j => {
      outReady(k)(j) := io.out(j)(k).ready
    })
  })

  (0 until numPorts).foreach(k => {
    io.in(k).ready := outReady(k).asUInt.andR
  })

}
