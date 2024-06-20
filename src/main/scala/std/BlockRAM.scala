package wood.std

import chisel3._
import chisel3.util._

class ReadPortI(dataWidth: Int, addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
}
class ReadPortO(dataWidth: Int, addrWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val addr = UInt(addrWidth.W)
}

class WritePortI(dataWidth: Int, addrWidth: Int) extends Bundle {
  val enable = Bool()
  val addr   = UInt(addrWidth.W)
  val data   = UInt(dataWidth.W)
}

class BlockRAMIO(dataWidth: Int, addrWidth: Int, numReadPorts: Int, numWritePorts: Int) extends Bundle {
  val rip = Vec(numReadPorts, Input(new ReadPortI(dataWidth, addrWidth)))
  val rop = Vec(numReadPorts, Output(new ReadPortO(dataWidth, addrWidth)))
  val wp  = Vec(numWritePorts, Input(new WritePortI(dataWidth, addrWidth)))
}

class DecoupledBlockRAMIO(dataWidth: Int, addrWidth: Int, numReadPorts: Int, numWritePorts: Int) extends Bundle {
  val rip = Vec(numReadPorts, Flipped(Decoupled(new ReadPortI(dataWidth, addrWidth))))
  val rop = Vec(numReadPorts, Decoupled(new ReadPortO(dataWidth, addrWidth)))
  val wp  = Vec(numWritePorts, Flipped(Decoupled(new WritePortI(dataWidth, addrWidth))))
}

case class BlockRAMParams(dataWidth: Int, depth: Int, numReadPorts: Int, numWritePorts: Int)

class BlockRAM(params: BlockRAMParams) extends Module {
  val io  = IO(new BlockRAMIO(params.dataWidth, log2Ceil(params.depth), params.numReadPorts, params.numWritePorts))
  val mem = Mem(params.depth, UInt(params.dataWidth.W))

  for (i <- 0 until params.numWritePorts) {
    when(io.wp(i).enable) {
      mem.write(io.wp(i).addr, io.wp(i).data)
    }
  }

  for (i <- 0 until params.numReadPorts) {
    io.rop(i).data := mem.read(io.rip(i).addr)
    io.rop(i).addr := io.rip(i).addr
  }
}

class DecoupledBlockRAM(params: BlockRAMParams) extends Module {
  val io = IO(
    new DecoupledBlockRAMIO(params.dataWidth, log2Ceil(params.depth), params.numReadPorts, params.numWritePorts)
  )
  val mem = Mem(params.depth, UInt(params.dataWidth.W))

  for (i <- 0 until params.numWritePorts) {
    when(io.wp(i).bits.enable & io.wp(i).valid) {
      mem.write(io.wp(i).bits.addr, io.wp(i).bits.data)
    }
    io.wp(i).ready := true.B
  }

  for (i <- 0 until params.numReadPorts) {
    io.rop(i).bits.data := mem.read(io.rip(i).bits.addr)
    io.rop(i).bits.addr := io.rip(i).bits.addr
    io.rop(i).valid     := io.rip(i).valid
    io.rip(i).ready     := true.B
  }
}
