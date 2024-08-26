package wood.std

import chisel3._
import chisel3.util._

/**
  * DCQueueWriter is a queue writer which writes to the queue with selected pattern for a given depth.
  *
  * @param numPorts: number of memory write ports
  * @param depth: memory depth
  * @param dataPattern: "zero", "one" or "addr"
  *
  * @example{{{
  * Writer for a queue with 2 write ports, 32 rows and 8 bit data width. Writes a count sequence.
  *  new DCWriter(UInt(8.W))(2,32,"count")
  * }}}
  */
class DCQueueWriter[T <: Data](gen: T)(numPorts: Int, depth: Int, dataPattern: String = "zero") extends Module {
  val io = IO(new Bundle {
    val out  = Vec(numPorts, Decoupled(gen.cloneType))
    val done = Output(Bool())
  })

  val oddNumberOfPorts = numPorts & 1

  var counterInitialValue = 0.U(log2Ceil(depth).W)
  if (dataPattern == "count+1")
    counterInitialValue = 1.U(log2Ceil(depth).W)

  val counter     = RegInit(counterInitialValue)
  val initialized = RegInit(false.B)

  val out_ready = Wire(Vec(numPorts, Bool()))
  out_ready := io.out.map(_.ready)

  val ready = out_ready.asUInt.andR

  val stopCount = Wire(UInt(log2Ceil(depth).W))
  stopCount := depth.asUInt - numPorts.U
  if (dataPattern == "count+1")
    if (oddNumberOfPorts == 1)
      stopCount := depth.asUInt - numPorts.U + 1.U
    else
      stopCount := depth.asUInt - numPorts.U

  when(!initialized && ready) {
    counter := counter + numPorts.U
  }

  when((counter >= stopCount) && !initialized) {
    initialized := 1.U
  }

  io.done := initialized

  (0 until numPorts).foreach(j => {
    io.out(j).valid := !initialized

    if (dataPattern == "count") {
      io.out(j).bits.asUInt := counter + j.asUInt
    } else if (dataPattern == "count+1") {
      io.out(j).bits.asUInt := counter + j.asUInt
    } else if (dataPattern == "one") {
      io.out(j).bits := 1.U
    } else {
      io.out(j).bits := 0.U
    }
  })
}
