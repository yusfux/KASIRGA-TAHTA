package wood.std

import chisel3._
import chisel3.util._

class ReadPortI[T <: Data](gen: T)(addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
}
class ReadPortO[T <: Data](gen: T)(addrWidth: Int) extends Bundle {
  val data = gen.cloneType
  val addr = UInt(addrWidth.W)
}

class WritePortI[T <: Data](gen: T)(addrWidth: Int) extends Bundle {
  val enable = Bool()
  val addr   = UInt(addrWidth.W)
  val data   = gen.cloneType
}

class BlockRAMIO[T <: Data](gen: T)(addrWidth: Int, numReadPorts: Int, numWritePorts: Int) extends Bundle {
  val rip = Vec(numReadPorts, Input(new ReadPortI(gen.cloneType)(addrWidth)))
  val rop = Vec(numReadPorts, Output(new ReadPortO(gen.cloneType)(addrWidth)))
  val wp  = Vec(numWritePorts, Input(new WritePortI(gen.cloneType)(addrWidth)))
}

class DecoupledBlockRAMIO[T <: Data](gen: T)(addrWidth: Int, numReadPorts: Int, numWritePorts: Int) extends Bundle {
  val rip = Vec(numReadPorts, Flipped(Decoupled(new ReadPortI(gen.cloneType)(addrWidth))))
  val rop = Vec(numReadPorts, Decoupled(new ReadPortO(gen.cloneType)(addrWidth)))
  val wp  = Vec(numWritePorts, Flipped(Decoupled(new WritePortI(gen.cloneType)(addrWidth))))
}

case class BlockRAMParams(depth: Int, numReadPorts: Int, numWritePorts: Int)

class BlockRAM[T <: Data](gen: T)(params: BlockRAMParams) extends Module {
  val io  = IO(new BlockRAMIO(gen.cloneType)(log2Ceil(params.depth), params.numReadPorts, params.numWritePorts))
  val mem = Mem(params.depth, gen.cloneType)

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

class DecoupledBlockRAM[T <: Data](gen: T)(params: BlockRAMParams) extends Module {
  val io = IO(
    new DecoupledBlockRAMIO(gen.cloneType)(log2Ceil(params.depth), params.numReadPorts, params.numWritePorts)
  )
  val mem = Mem(params.depth, gen.cloneType)

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
    io.rip(i).ready     := io.rop(i).ready
  }
}
