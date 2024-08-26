package wood.std

import chisel3._
import chisel3.util._

/**
  * Initializes the memory with the selected pattern then yields the inputs.
  *
  * @param numPorts: number of memory write ports
  * @param depth: memory depth
  * @param dataPattern: "zero", "one" or "addr"
  *
  * @example {{{
  * Initializer for a queue with 2 write ports, 32 rows and 8 bit data width. Writes a count sequence.
  *  new DCQueueInitializer(UInt(8.W))(2,32,"count")
  * }}}
  */
class DCQueueInitializer[T <: Data](gen: T)(numPorts: Int, depth: Int, dataPattern: String = "zero") extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val writer = Module(new DCQueueWriter(gen.cloneType)(numPorts, depth, dataPattern))

  (0 until numPorts).foreach(j => {
    io.out(j).bits         := Mux(writer.io.done, io.in(j).bits, writer.io.out(j).bits)
    io.out(j).valid        := Mux(writer.io.done, io.in(j).valid, writer.io.out(j).valid)
    writer.io.out(j).ready := io.out(j).ready

    io.in(j).ready := Mux(writer.io.done, io.out(j).ready, 0.B)
  })
}
