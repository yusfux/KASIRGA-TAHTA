package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, MI}
import wood.std.DCPipelineRegister

class RenameStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(config.nWide, Decoupled(new MI(config))))
    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val pRegs                = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new MI(config))(1)))
  val frontEndRegisterFile = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))
  val self                 = Wire(Vec(config.nWide, Decoupled(new MI(config))))
  val allInValid           = Wire(Vec(config.nWide, Bool())).suggestName("allInValid")
  allInValid := io.in.map(_.valid)

  val selfReady = Wire(Vec(config.nWide, Bool()))
  selfReady := self.map(_.ready)
  val outReady = Wire(Vec(config.nWide, Bool()))
  outReady := io.out.map(_.ready)

  (0 until config.nWide).foreach(j => {
    when((io.in(j).bits.writeRf === Integer.parseInt(DecodeConfig.WRITE_RF_1, 2).U) & io.in(j).fire) {
      frontEndRegisterFile(io.in(j).bits.rd) := io.in(j).bits.rdTag
    }
  })

  (0 until config.nWide).foreach(j => {
    self(j).bits := io.in(j).bits

    val (rs1HasOverride, overrideRs1Tag) = (0 until j).foldLeft((0.B, 0.U)) { (acc, k) =>
      val rs1Match = (io.in(j).bits.rs1 === io.in(k).bits.rd)
      val rs1Valid = (io.in(j).bits.operand1 === Integer.parseInt(DecodeConfig.OPERAND1_REG, 2).U)

      val rdValid    = (io.in(k).bits.writeRf === Integer.parseInt(DecodeConfig.WRITE_RF_1, 2).U)
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
      val rs2Valid = (io.in(j).bits.operand2 === Integer.parseInt(DecodeConfig.OPERAND2_REG, 2).U)

      val rdValid    = (io.in(k).bits.writeRf === Integer.parseInt(DecodeConfig.WRITE_RF_1, 2).U)
      val matchFound = rs2Match & rs2Valid & rdValid

      (acc._1 || matchFound, Mux(matchFound, io.in(k).bits.rdTag, acc._2))
    }

    when(rs2HasOverride) {
      self(j).bits.rs2Tag := overrideRs2Tag
    }.otherwise {
      self(j).bits.rs2Tag := frontEndRegisterFile(io.in(j).bits.rs2)
    }

    self(j).valid := allInValid.asUInt.andR & outReady.asUInt.andR

    pRegs(j).io.valids(0) := io.in(j).valid

    io.in(j).ready := selfReady.asUInt.andR

    pRegs(j).io.in        <> self(j)
    io.out(j)             <> pRegs(j).io.out
    pRegs(j).io.out.ready := outReady.asUInt.andR // always fire together
  })
}
