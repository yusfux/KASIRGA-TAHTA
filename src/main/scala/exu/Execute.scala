package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCArbiter
import wood.util.WoodMIPipelineRegister

class ExecuteStage(config: WoodConfig) extends Module {
  val totalNumPorts = config.listExUnits.sum
  val numALUs       = config.listExUnits(config.aluCrossbarIndex)
  val numIMUs       = config.listExUnits(config.imuCrossbarIndex)
  val numIDUs       = config.listExUnits(config.iduCrossbarIndex)

  val io = IO(new Bundle {
    val aluIn      = Flipped(Vec(numALUs, Decoupled(new MI(config))))
    val flush      = Input(Bool())
    val imuIn      = Flipped(Vec(numIMUs, Decoupled(new MI(config))))
    val iduIn      = Flipped(Vec(numIMUs, Decoupled(new MI(config))))
    val forwardBus = Vec(config.nWide, ValidIO(new DataBus(config)))
    val out        = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pRegs   = Seq.fill(config.nWide)(Module(new WoodMIPipelineRegister(config, 1)))
  val arbiter = Module(new DCArbiter(new MI(config))(totalNumPorts, config.nWide))
  val alus = Seq.tabulate(numALUs) { _ =>
    Module(new ALU(config))
  }
  val imus = Seq.tabulate(numIMUs) { _ =>
    Module(new IMU(config))
  }
  val idus = Seq.tabulate(numIDUs) { _ =>
    Module(new IDU(config))
  }

  val aluOutputs = Wire(Vec(numALUs, Decoupled(new MI(config))))
  val aluInputs  = Wire(Vec(numALUs, Decoupled(new MI(config))))
  aluInputs  <> alus.map(_.io.in)
  aluOutputs <> alus.map(_.io.out)

  val imuOutputs = Wire(Vec(numIMUs, Decoupled(new MI(config))))
  val imuInputs  = Wire(Vec(numIMUs, Decoupled(new MI(config))))
  imuInputs  <> imus.map(_.io.in)
  imuOutputs <> imus.map(_.io.out)

  val iduOutputs = Wire(Vec(numIDUs, Decoupled(new MI(config))))
  val iduInputs  = Wire(Vec(numIDUs, Decoupled(new MI(config))))
  iduInputs  <> idus.map(_.io.in)
  iduOutputs <> idus.map(_.io.out)

  aluInputs <> io.aluIn
  imuInputs <> io.imuIn
  iduInputs <> io.iduIn

  // alu.foreach(_.io.flush := io.flush)
  imus.foreach(_.io.flush := io.flush)
  idus.foreach(_.io.flush := io.flush)

  val aluRange = (0 until numALUs)
  val imuRange = (numALUs until numALUs + numIMUs)
  val iduRange = (numALUs + numIMUs until numALUs + numIMUs + numIDUs)
  (aluRange).foreach(j => {
    arbiter.io.in(j) <> aluOutputs(j)
  })
  (imuRange).foreach(j => {
    arbiter.io.in(j) <> imuOutputs(j - imuRange(0))
  })
  (iduRange).foreach(j => {
    arbiter.io.in(j) <> iduOutputs(j - iduRange(0))
  })

  (0 until config.nWide).foreach(j => {
    val isStore = (arbiter.io.out(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_T_S, 2).U)
    val isLoad  = (arbiter.io.out(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_T_L, 2).U)
    val isAtom  = (arbiter.io.out(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_T_A, 2).U)

    io.forwardBus(j).bits.data := arbiter.io.out(j).bits.rdData
    io.forwardBus(j).bits.tag  := arbiter.io.out(j).bits.rdTag
    io.forwardBus(j).valid     := arbiter.io.out(j).valid & !isLoad & !isAtom

    pRegs(j).io.valids(0) := io.aluIn(j).valid // TODO: BUG: BUG: BUG:

    pRegs(j).io.flush      := io.flush
    pRegs(j).io.setflushed := io.flush

    pRegs(j).io.in <> arbiter.io.out(j)
    io.out(j)      <> pRegs(j).io.out
  })
}
