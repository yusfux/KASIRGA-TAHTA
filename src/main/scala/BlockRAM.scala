package blockram

import chisel3._
import chisel3.util._

class ReadPortIO(dataWidth: Int, addrWidth: Int) extends Bundle {
  val addr = Input(UInt(addrWidth.W))
  val data = Output(UInt(dataWidth.W))
}

class WritePortIO(dataWidth: Int, addrWidth: Int) extends Bundle {
  val enable = Input(Bool())
  val addr = Input(UInt(addrWidth.W))
  val data = Input(UInt(dataWidth.W))
}

class BlockRAMIO(dataWidth: Int, addrWidth: Int, numReadPorts: Int, numWritePorts: Int) extends Bundle {
  val readPorts = Vec(numReadPorts, new ReadPortIO(dataWidth, addrWidth))
  val writePorts = Vec(numWritePorts, new WritePortIO(dataWidth, addrWidth))
}

class BlockRAM(dataWidth: Int, depth: Int, numReadPorts: Int, numWritePorts: Int) extends Module {
  val io = IO(new BlockRAMIO(dataWidth, log2Ceil(depth), numReadPorts, numWritePorts))
  val mem = Mem(depth, UInt(dataWidth.W))

  for (i <- 0 until numWritePorts) {
    when(io.writePorts(i).enable) {
      mem.write(io.writePorts(i).addr, io.writePorts(i).data)
    }
  }

  for (i <- 0 until numReadPorts) {
    io.readPorts(i).data := mem.read(io.readPorts(i).addr)
  }
}
