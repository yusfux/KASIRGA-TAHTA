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

  val pReg                 = Module(new DCPipelineRegister(new MI(config))(config.nWide))
  val frontEndRegisterFile = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))

  (0 until config.nWide).foreach(j => {
    when((io.in(j).bits.writeRf === DecodeConfig.WRITE_RF_1.toInt.U) & io.in(j).valid & io.in(j).valid) {
      frontEndRegisterFile(io.in(j).bits.rd) := io.in(j).bits.rdTag
    }
  })

  val overriden = Wire(Vec(config.nWide, new MI(config)))

  (0 until config.nWide).foreach(j => {
    overriden(j) := io.in(j).bits

    val (rs1HasOverride, overrideRs1Tag) = (0 until j).foldLeft((0.B, 0.U)) { (acc, k) =>
      val rs1Match = (io.in(j).bits.rs1 === io.in(k).bits.rd)
      val rs1Valid = (io.in(j).bits.operand === DecodeConfig.OPERAND_REG.toInt.U) ||
        (io.in(j).bits.operand === DecodeConfig.OPERAND_IMM.toInt.U) ||
        (io.in(j).bits.operand === DecodeConfig.OPERAND_PC.toInt.U)

      val rdValid    = (io.in(k).bits.writeRf === DecodeConfig.WRITE_RF_1.toInt.U)
      val matchFound = rs1Match & rs1Valid & rdValid

      (acc._1 || matchFound, Mux(matchFound, io.in(k).bits.rdTag, acc._2))
    }

    when(rs1HasOverride) {
      overriden(j).rs1Tag := overrideRs1Tag
    }.otherwise {
      overriden(j).rs1Tag := frontEndRegisterFile(io.in(j).bits.rs1)
    }

    val (rs2HasOverride, overrideRs2Tag) = (0 until j).foldLeft((0.B, 0.U)) { (acc, k) =>
      val rs2Match = (io.in(j).bits.rs2 === io.in(k).bits.rd)
      val rs2Valid = (io.in(j).bits.operand === DecodeConfig.OPERAND_REG.toInt.U)

      val rdValid    = (io.in(k).bits.writeRf === DecodeConfig.WRITE_RF_1.toInt.U)
      val matchFound = rs2Match & rs2Valid & rdValid

      (acc._1 || matchFound, Mux(matchFound, io.in(k).bits.rdTag, acc._2))
    }

    when(rs1HasOverride) {
      overriden(j).rs2Tag := overrideRs2Tag
    }.otherwise {
      overriden(j).rs2Tag := frontEndRegisterFile(io.in(j).bits.rs2)
    }

    pReg.io.in(j).bits  := overriden(j)
    pReg.io.in(j).valid := io.in(j).valid
    io.in(j).ready      := pReg.io.in(j).ready
  })

  io.out <> pReg.io.out
}
