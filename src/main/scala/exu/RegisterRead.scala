package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.{DCCrossbar, DCPipelineRegister}

class RegisterReadStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val writebackBus = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val forwardBus   = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val wakeupBus    = Vec(config.nWide, ValidIO(new TagBus(config)))
    val out          = MixedVec(config.listExUnits.map(length => Vec(length, Decoupled(new MI(config)))))
  })

  val overrideForward   = Module(new OverrideRsFromBuses(config))
  val overrideWriteBack = Module(new OverrideRsFromBuses(config))
  val crossbar          = Module(new DCCrossbar(new MI(config))(config.nWide, config.listExUnits))
  val aluPRegs          = Module(new DCPipelineRegister(new MI(config))(config.nWide))

  val aluCrossbarIndex = 0 // TODO: move to WoodConfig

  val prf = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(config.dataWidth.W)))) // TODO: remove reset

  val overridenRFData = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRFData <> io.in

  (0 until config.nWide).foreach(j => {
    io.wakeupBus(j).bits.tag := io.in(j).bits.rdTag
    io.wakeupBus(j).valid    := io.in(j).bits.wakeup

    val rs1ValidZero = (io.in(j).bits.rs1 === 0.U) &
      ((io.in(j).bits.operand === DecodeConfig.OPERAND_REG.toInt.U) ||
        (io.in(j).bits.operand === DecodeConfig.OPERAND_IMM.toInt.U) ||
        (io.in(j).bits.operand === DecodeConfig.OPERAND_PC.toInt.U))

    val rs2ValidZero = (io.in(j).bits.rs2 === 0.U) &
      (io.in(j).bits.operand === DecodeConfig.OPERAND_REG.toInt.U)

    overridenRFData(j).bits.rs1Data := Mux(rs1ValidZero, 0.U, prf(io.in(j).bits.rs1Tag))
    overridenRFData(j).bits.rs2Data := Mux(rs2ValidZero, 0.U, prf(io.in(j).bits.rs2Tag))

    when(io.writebackBus(j).valid) {
      prf(io.writebackBus(j).bits.tag) := io.writebackBus(j).bits.data
    }
    crossbar.io.sel(j) := io.in(j).bits.exEngine === ExEngine.alu.asUInt
  })

  overrideForward.io.inBus   <> io.forwardBus
  overrideWriteBack.io.inBus <> io.writebackBus
  overrideForward.io.in      <> overridenRFData
  overrideWriteBack.io.in    <> overrideForward.io.out
  crossbar.io.in             <> overrideWriteBack.io.out

  aluPRegs.io.in           <> crossbar.io.out(aluCrossbarIndex)
  io.out(aluCrossbarIndex) <> aluPRegs.io.out
}
