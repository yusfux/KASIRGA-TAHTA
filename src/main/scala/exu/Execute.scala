package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.MI
import wood.std.{DCArbiter, DCPipelineRegister}

class ExecuteStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val aluIn      = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val forwardBus = Vec(config.nWide, ValidIO(new DataBus(config)))
    val out        = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pRegs = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  val arbiter = Module(new DCArbiter(new MI(config))(config.listExUnits.sum, config.nWide))
  val alus = Seq.tabulate(config.nWide) { _ =>
    Module(new ALU(config))
  }

  val aluOutputs = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val aluInputs  = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  aluInputs  <> alus.map(_.io.in)
  aluOutputs <> alus.map(_.io.out)

  aluInputs     <> io.aluIn
  arbiter.io.in <> aluOutputs

  (0 until config.nWide).foreach(j => {
    io.forwardBus(j).bits.data := arbiter.io.out(j).bits.rdData
    io.forwardBus(j).bits.tag  := arbiter.io.out(j).bits.rdTag
    io.forwardBus(j).valid     := arbiter.io.out(j).valid

    pRegs(j).io.valids(0) := io.aluIn(j).valid

    pRegs(j).io.in <> arbiter.io.out(j)
    io.out(j)      <> pRegs(j).io.out
  })
}
