package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCCrossbar
import wood.util.WoodMIPipelineRegister

class RegisterReadStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val lsuIn        = Flipped(Vec(1, ValidIO(new DataBus(config))))
    val flush        = Input(Bool())
    val writebackBus = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val forwardBus   = Flipped(Vec(config.nWide, ValidIO(new DataBus(config))))
    val wakeupBus    = Vec(config.nWide, ValidIO(new TagBus(config)))
    val aluOut       = Vec(config.listExUnits(config.aluCrossbarIndex), Decoupled(new MI(config)))
    val imuOut       = Vec(config.listExUnits(config.imuCrossbarIndex), Decoupled(new MI(config)))
    val iduOut       = Vec(config.listExUnits(config.iduCrossbarIndex), Decoupled(new MI(config)))
  })

  val overrideForward   = Module(new OverrideRsFromBuses(config))
  val overrideWriteBack = Module(new OverrideRsFromBuses(config))
  val crossbar          = Module(new DCCrossbar(new MI(config))(config.nWide, config.listExCrossbarUnits))
  val aluPRegs          = Seq.fill(config.listExUnits(config.aluCrossbarIndex))(Module(new WoodMIPipelineRegister(config, 1)))
  val imuPRegs          = Seq.fill(config.listExUnits(config.imuCrossbarIndex))(Module(new WoodMIPipelineRegister(config, 1)))
  val iduPRegs          = Seq.fill(config.listExUnits(config.iduCrossbarIndex))(Module(new WoodMIPipelineRegister(config, 1)))

  val prf = RegInit(VecInit(Seq.fill(config.prfDepth)(0.U(config.xlen.W)))) // TODO: remove reset

  val overridenRFData = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  overridenRFData <> io.in

  when(io.lsuIn(0).valid) {
    prf(io.lsuIn(0).bits.tag) := io.lsuIn(0).bits.data
  }

  (0 until config.nWide).foreach(j => {
    io.wakeupBus(j).bits.tag := io.in(j).bits.rdTag
    io.wakeupBus(j).valid    := io.in(j).bits.wakeup & io.in(j).valid

    overridenRFData(j).bits.rs1Data := prf(io.in(j).bits.rs1Tag)
    overridenRFData(j).bits.rs2Data := prf(io.in(j).bits.rs2Tag)

    when(io.writebackBus(j).valid) {
      prf(io.writebackBus(j).bits.tag) := io.writebackBus(j).bits.data
    }

    crossbar.io.sel(j) := MuxCase(
      config.aluCrossbarIndex.U,
      Array(
        (io.in(j).bits.exEngine === ExEngine.alu.asUInt) -> config.aluCrossbarIndex.U,
        (io.in(j).bits.exEngine === ExEngine.idu.asUInt) -> config.iduCrossbarIndex.U,
        (io.in(j).bits.exEngine === ExEngine.imu.asUInt) -> config.imuCrossbarIndex.U
      ).toIndexedSeq
    )
  })

  (0 until config.listExUnits(config.aluCrossbarIndex)).foreach(j => {
    aluPRegs(j).io.in         <> crossbar.io.out(config.aluCrossbarIndex)(j)
    aluPRegs(j).io.valids(0)  := crossbar.io.out(config.aluCrossbarIndex)(j).valid
    io.aluOut(j)              <> aluPRegs(j).io.out
    aluPRegs(j).io.flush      := io.flush
    aluPRegs(j).io.setflushed := io.flush

  })

  (0 until config.listExUnits(config.imuCrossbarIndex)).foreach(j => {
    imuPRegs(j).io.in         <> crossbar.io.out(config.imuCrossbarIndex)(j)
    imuPRegs(j).io.valids(0)  := crossbar.io.out(config.imuCrossbarIndex)(j).valid
    io.imuOut(j)              <> imuPRegs(j).io.out
    imuPRegs(j).io.flush      := io.flush
    imuPRegs(j).io.setflushed := io.flush
  })

  (0 until config.listExUnits(config.iduCrossbarIndex)).foreach(j => {
    iduPRegs(j).io.in         <> crossbar.io.out(config.iduCrossbarIndex)(j)
    iduPRegs(j).io.valids(0)  := crossbar.io.out(config.iduCrossbarIndex)(j).valid
    io.iduOut(j)              <> iduPRegs(j).io.out
    iduPRegs(j).io.flush      := io.flush
    iduPRegs(j).io.setflushed := io.flush
  })

  overrideForward.io.inBus   <> io.forwardBus
  overrideWriteBack.io.inBus <> io.writebackBus
  overrideWriteBack.io.in    <> overridenRFData
  overrideForward.io.in      <> overrideWriteBack.io.out
  crossbar.io.in             <> overrideForward.io.out

  (0 until config.nWide).foreach(j => {
    val rs1AdrX0      = io.in(j).bits.rs1 === 0.U
    val rs1IndirectX0 = io.in(j).bits.operand1 === Integer.parseInt(DecodeConfig.OPSRC1_IRF, 2).U
    val rs1DirectX0   = io.in(j).bits.operand1 === Integer.parseInt(DecodeConfig.OPSRC1_X0, 2).U

    val rs1ValidZero = (rs1AdrX0 && rs1IndirectX0) || (rs1DirectX0)

    val rs2AdrX0      = io.in(j).bits.rs2 === 0.U
    val rs2IndirectX0 = io.in(j).bits.operand2 === Integer.parseInt(DecodeConfig.OPSRC2_IRF, 2).U
    val rs2ValidZero  = (rs2AdrX0 && rs2IndirectX0)

    crossbar.io.in(j).bits.rs1Data := Mux(rs1ValidZero, 0.U, overrideForward.io.out(j).bits.rs1Data)
    crossbar.io.in(j).bits.rs2Data := Mux(rs2ValidZero, 0.U, overrideForward.io.out(j).bits.rs2Data)
  })

}
