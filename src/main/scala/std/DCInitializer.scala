package wood.std

import chisel3._
import chisel3.util._

/**
  * DCInitializer is a memory initializer which fills the memory with selected pattern then yields the write port.
  *
  * @param numPorts: number of memory write ports
  * @param depth: memory depth
  * @param dataWidth: data width
  * @param dataPattern: "zero", "one" or "addr"
  *
  * @example{{{
  * Initializer for 2 write port memory with 32 depth and 8 bit data width. Writes addr to every addr then yields the write port.
  *  new DCInitializer(UInt(8.W))(2,32,8,"addr")
  * }}}
  */
class DCInitializer(numPorts: Int, depth: Int, dataWidth: Int, dataPattern: String = "zero") extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(new WritePortI(UInt(dataWidth.W))(log2Ceil(depth)))))
    val out = Vec(numPorts, Decoupled(new WritePortI(UInt(dataWidth.W))(log2Ceil(depth))))
  })

  val writer = Module(new DCWriter(numPorts, depth, dataWidth, dataPattern))
  val arbiter = Module(
    new DCArbiter(new WritePortI(UInt(dataWidth.W))(log2Ceil(depth)))(numPorts * 2, numPorts)
  )

  (0 until numPorts).foreach(j => {
    arbiter.io.in(j)            <> writer.io.out(j)
    arbiter.io.in(j + numPorts) <> io.in(j)
  })
  arbiter.io.out <> io.out
}
