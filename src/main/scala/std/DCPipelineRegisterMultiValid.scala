package wood.std

import chisel3._
import chisel3.util._

/**
  * DCPipelineRegisterMultiValid is a simple pipeline register with combinationally coupled ready signal.
  *
  * @param numPorts:  number of ports
  *
  * @example{{{
  *  * Pipeline register for 2 port MI.
  *   new DCPipelineRegister(new MI(config))(2)
  * }}}
  */
class DCPipelineRegisterMultiValid[T <: Data](gen: T)(numPorts: Int, numExtraValids: Int) extends Module {
  val io = IO(new Bundle {
    val in     = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val valids = Input(Vec(numExtraValids, Vec(numPorts, UInt(1.W))))
    val out    = Vec(numPorts, Decoupled(gen.cloneType))
  })
  require(numExtraValids > 0)

  val numValids = numExtraValids + 1
  val allValids = Wire(Vec(numValids, Vec(numPorts, UInt(1.W))))
  val inValids  = Wire(Vec(numPorts, Bool()))

  val stall           = Wire(Vec(numPorts, Bool()))
  val validsTranspose = Wire(Vec(numPorts, Vec(numValids, UInt(1.W))))

  stall := io.out.map(!_.ready)

  (0 until numPorts).foreach(j => {
    inValids(j) := io.in(j).valid
    (0 until numExtraValids).foreach(k => {
      allValids(k)(j) := io.valids(k)(j)
    })
    (0 until numValids).foreach(k => {
      validsTranspose(j)(k) := allValids(k)(j)
    })
  })
  allValids(numValids - 1) := inValids

  (0 until numPorts).foreach(j => {
    val valid = validsTranspose(j).asUInt.andR
    io.out(j).bits  := RegEnable(io.in(j).bits, 0.U.asTypeOf(gen.cloneType), !stall(j).asBool)
    io.out(j).valid := RegEnable(valid, 0.B, !stall(j).asBool)
    io.in(j).ready := MuxCase(
      0.U,
      Array(
        (!(validsTranspose(j).asUInt.orR))  -> 1.U,
        (!(validsTranspose(j).asUInt.andR)) -> 0.U,
        (validsTranspose(j).asUInt.andR)    -> io.out(j).ready
      ).toIndexedSeq
    )

  })
}
