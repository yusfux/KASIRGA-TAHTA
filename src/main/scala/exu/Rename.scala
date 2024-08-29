package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.lsu.LSOperandBus
import wood.util.{WoodLSRetireMIPipelineRegister, WoodMIPipelineRegister}

class RenameStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val flush        = Input(Bool())
    val archRF       = Input(Vec(32, UInt(config.tagWidth.W)))
    val frontRetired = Input(Vec(config.nWide, Bool()))
    val lsOperandBus = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))
    val out0         = Vec(config.nWide, Decoupled(new MI(config)))
    val out1         = Vec(config.nWide, Decoupled(new MI(config)))
    val out2         = Vec(config.nWide, Decoupled(new MI(config)))
  })
  val pRegs0 = Seq.fill(config.nWide)(Module(new WoodMIPipelineRegister(config, 1)))
  val pRegs1 = Seq.fill(config.nWide)(Module(new WoodMIPipelineRegister(config, 1)))
  val pRegs2 = Seq.fill(config.nWide)(Module(new WoodLSRetireMIPipelineRegister(config)))

  val frontEndRegisterFile = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))
  val self                 = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val flushDelayed         = RegNext(RegNext(io.flush, false.B), false.B) // Flush to last Arch RF update delay
  val allInValid           = Wire(Vec(config.nWide, Bool())).suggestName("allInValid")
  allInValid := io.in.map(_.valid)

  val out0Ready = Wire(Vec(config.nWide, Bool()))
  out0Ready := io.out0.map(_.ready)
  val out1Ready = Wire(Vec(config.nWide, Bool()))
  out1Ready := io.out1.map(_.ready)
  val out2Ready = Wire(Vec(config.nWide, Bool()))
  out2Ready := io.out2.map(_.ready)

  (0 until config.nWide).foreach(j => {
    when((io.in(j).bits.writeRf === Integer.parseInt(DecodeConfig.W_RF_I, 2).U) & io.in(j).fire & !io.flush & !io.in(j).bits.flushed) {
      frontEndRegisterFile(io.in(j).bits.rd) := io.in(j).bits.rdTag
    }
  })

  (0 until config.nWide)
    .foreach(j => {
      self(j).bits  := io.in(j).bits
      self(j).valid := io.in(j).valid

      val (rs1HasOverride, overrideRs1Tag) = (0 until j).foldLeft((0.B, 0.U)) { (acc, k) =>
        val rs1Match = (io.in(j).bits.rs1 === io.in(k).bits.rd)
        val rs1Valid = (io.in(j).bits.opsrc1 === Integer.parseInt(DecodeConfig.OPSRC1_IRF, 2).U)

        val rdValid    = (io.in(k).bits.writeRf === Integer.parseInt(DecodeConfig.W_RF_I, 2).U)
        val matchFound = rs1Match & rs1Valid & rdValid

        (acc._1 || matchFound, Mux(matchFound, io.in(k).bits.rdTag, acc._2))
      }

      when(rs1HasOverride) {
        self(j).bits.rs1Tag := overrideRs1Tag
      }.otherwise {
        self(j).bits.rs1Tag := frontEndRegisterFile(io.in(j).bits.rs1)
      }

      val (rs2HasOverride, overrideRs2Tag) = (0 until j).foldLeft((0.B, 0.U)) { (acc, k) =>
        val rs2Match = (io.in(j).bits.rs2 === io.in(k).bits.rd)
        val rs2Valid = (io.in(j).bits.opsrc2 === Integer.parseInt(DecodeConfig.OPSRC2_IRF, 2).U)

        val rdValid    = (io.in(k).bits.writeRf === Integer.parseInt(DecodeConfig.W_RF_I, 2).U)
        val matchFound = rs2Match & rs2Valid & rdValid

        (acc._1 || matchFound, Mux(matchFound, io.in(k).bits.rdTag, acc._2))
      }

      when(rs2HasOverride) {
        self(j).bits.rs2Tag := overrideRs2Tag
      }.otherwise {
        self(j).bits.rs2Tag := frontEndRegisterFile(io.in(j).bits.rs2)
      }

      self(j).bits.flushed := io.flush | io.in(j).bits.flushed

      pRegs0(j).io.valids(0) := io.in(j).valid
      pRegs1(j).io.valids(0) := io.in(j).valid
      // pRegs2(j).io.valids(0) := io.in(j).valid

      io.in(j).ready := out2Ready.asUInt.andR & out1Ready.asUInt.andR & out0Ready.asUInt.andR

      pRegs0(j).io.flush      := 0.U // never lose tags
      pRegs0(j).io.setflushed := io.flush

      pRegs1(j).io.flush      := 0.U // never lose tags
      pRegs1(j).io.setflushed := io.flush

      // pRegs2(j).io.flush        := 0.U // never lose tags
      pRegs2(j).io.setflushed   := io.flush
      pRegs2(j).io.frontRetired := io.frontRetired(j)
      pRegs2(j).io.lsOperandBus <> io.lsOperandBus

      pRegs0(j).io.in       <> self(j)
      pRegs0(j).io.in.valid := self(j).valid & (out2Ready.asUInt.andR & out1Ready.asUInt.andR & out0Ready.asUInt.andR)
      io.out0(j)            <> pRegs0(j).io.out

      pRegs1(j).io.in       <> self(j)
      pRegs1(j).io.in.valid := self(j).valid & (out2Ready.asUInt.andR & out1Ready.asUInt.andR & out0Ready.asUInt.andR)
      io.out1(j)            <> pRegs1(j).io.out

      val isStore = (self(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_T_S, 2).U)
      val isLoad  = (self(j).bits.lsType === Integer.parseInt(DecodeConfig.LS_T_L, 2).U)
      pRegs2(j).io.in       <> self(j)
      pRegs2(j).io.in.valid := self(j).valid & (out2Ready.asUInt.andR & out1Ready.asUInt.andR & out0Ready.asUInt.andR) & (isLoad | isStore)
      io.out2(j)            <> pRegs2(j).io.out
    })

  (0 until 32).foreach(j => {
    when(flushDelayed) {
      frontEndRegisterFile(j) := io.archRF(j)
    }
  })
}
