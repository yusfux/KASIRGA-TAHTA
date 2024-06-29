package wood.std

import chisel3._
import chisel3.util._

/*
@note DCInitializer is a memory initializer which resets the memory.
@param numPorts: number of memory write ports
@examples
 * Initializer for 2 write port memory
  new DCInitializer(UInt(8.W))(2)
 */
class DCInitializer(numPorts: Int, depth: Int, dataWidth: Int, dataPattern: String = "zero") extends Module {
  val io = IO(new Bundle {
    val out = Vec(numPorts, Decoupled(new WritePortI(UInt(dataWidth.W))(log2Ceil(depth))))
  })

  val counter     = RegInit(0.U(dataWidth.W))
  val initialized = RegInit(false.B)

  val out_ready = Wire(Vec(numPorts, Bool()))
  out_ready := io.out.map(_.ready)

  val ready     = out_ready.asUInt.andR
  val was_ready = RegNext(ready)

  when(!initialized && ready) {
    counter := counter + numPorts.U
  }

  val stopCount = Wire(UInt(log2Ceil(depth).W))
  stopCount := ((1.U << depth.asUInt) - 1.U)

  when((counter === stopCount) && !initialized) {
    initialized := 1.U
  }

  (0 until numPorts).foreach(j => {
    io.out(j).bits.addr   := counter + j.asUInt
    io.out(j).bits.enable := 1.U
    io.out(j).valid       := !initialized

    if (dataPattern == "addr") {
      io.out(j).bits.data := counter + j.asUInt
    } else if (dataPattern == "one") {
      io.out(j).bits.data := 1.U
    } else {
      io.out(j).bits.data := 0.U
    }
  })
}
