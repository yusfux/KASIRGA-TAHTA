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
    val aluOut       = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val overrideForward   = Module(new OverrideRsFromBuses(config))
  val overrideWriteBack = Module(new OverrideRsFromBuses(config))
  val crossbar          = Module(new DCCrossbar(new MI(config))(config.nWide, config.listExUnits))
  val aluPRegs          = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))

  val aluCrossbarIndex = 0 // TODO: move to WoodConfig

  val prf = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(config.dataWidth.W)))) // TODO: remove reset

  val overridenRFData = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRFData <> io.in

  (0 until config.nWide).foreach(j => {
    io.wakeupBus(j).bits.tag := io.in(j).bits.rdTag
    io.wakeupBus(j).valid    := io.in(j).bits.wakeup

    overridenRFData(j).bits.rs1Data := prf(io.in(j).bits.rs1Tag)
    overridenRFData(j).bits.rs2Data := prf(io.in(j).bits.rs2Tag)

    when(io.writebackBus(j).valid) {
      prf(io.writebackBus(j).bits.tag) := io.writebackBus(j).bits.data
    }
    crossbar.io.sel(j) := io.in(j).bits.exEngine === ExEngine.alu.asUInt

    aluPRegs(j).io.valids(0) := io.in(j).valid

    aluPRegs(j).io.in <> crossbar.io.out(aluCrossbarIndex)(j)
    io.aluOut(j)      <> aluPRegs(j).io.out
  })

  overrideForward.io.inBus   <> io.forwardBus
  overrideWriteBack.io.inBus <> io.writebackBus
  overrideWriteBack.io.in    <> overridenRFData
  overrideForward.io.in      <> overrideWriteBack.io.out
  crossbar.io.in             <> overrideForward.io.out

  println("bonkers reg", Integer.parseInt(DecodeConfig.OPERAND1_REG, 2))
  println("bonkers", Integer.parseInt(DecodeConfig.OPERAND1_X0, 2))

  (0 until config.nWide).foreach(j => {
    val rs1AdrX0      = dontTouch(io.in(j).bits.rs1 === 0.U)
    val rs1IndirectX0 = dontTouch(io.in(j).bits.operand1 === Integer.parseInt(DecodeConfig.OPERAND1_REG, 2).U)
    val rs1DirectX0   = dontTouch(io.in(j).bits.operand1 === Integer.parseInt(DecodeConfig.OPERAND1_X0, 2).U)

    val rs1ValidZero = dontTouch((rs1AdrX0 && rs1IndirectX0) || (rs1DirectX0))

    val rs2AdrX0      = io.in(j).bits.rs2 === 0.U
    val rs2IndirectX0 = io.in(j).bits.operand2 === Integer.parseInt(DecodeConfig.OPERAND2_REG, 2).U
    val rs2ValidZero  = (rs2AdrX0 && rs2IndirectX0)

    crossbar.io.in(j).bits.rs1Data := Mux(rs1ValidZero, 0.U, overrideForward.io.out(j).bits.rs1Data)
    crossbar.io.in(j).bits.rs2Data := Mux(rs2ValidZero, 0.U, overrideForward.io.out(j).bits.rs2Data)
  })

}
