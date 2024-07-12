package wood.fru

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import wood.WoodConfig
import wood.fru.{DecodeConfig, DecodeStage}

case class MI(config: WoodConfig) extends Bundle {
  val isFloat     = UInt(DecodeConfig.subWidths(0).W)
  val wakeup      = UInt(DecodeConfig.subWidths(1).W)
  val operand     = UInt(DecodeConfig.subWidths(2).W)
  val writeRf     = UInt(DecodeConfig.subWidths(3).W)
  val exEngine    = UInt(DecodeConfig.subWidths(4).W)
  val exOp        = UInt(DecodeConfig.subWidths(5).W)
  val imm         = UInt(32.W) // TODO
  val rs1         = UInt(5.W)
  val rs2         = UInt(5.W)
  val rd          = UInt(5.W)
  val pcIdx       = UInt(config.pcIndexWidth.W)
  val rs1Tag      = UInt(config.tagWidth.W)
  val rs1TagReady = UInt(1.W)
  val rs2Tag      = UInt(config.tagWidth.W)
  val rs2TagReady = UInt(1.W)
  val rdTag       = UInt(config.tagWidth.W)
  val rs1Data     = UInt(config.dataWidth.W)
  val rs2Data     = UInt(config.dataWidth.W)
  val rdData      = UInt(config.dataWidth.W)
  val retired     = UInt(1.W)
  val inst        = UInt(32.W) // for testbench only
}

object MI { // for testbench only, set all to value except overrides
  def apply(config: WoodConfig, value: UInt, overrides: Map[String, UInt] = Map.empty): MI = {
    val mi = new MI(config).Lit(
      _.isFloat     -> overrides.getOrElse("isFloat", value),
      _.wakeup      -> overrides.getOrElse("wakeup", value),
      _.operand     -> overrides.getOrElse("operand", value),
      _.writeRf     -> overrides.getOrElse("writeRf", value),
      _.exEngine    -> overrides.getOrElse("exEngine", value),
      _.exOp        -> overrides.getOrElse("exOp", value),
      _.imm         -> overrides.getOrElse("imm", value),
      _.rs1         -> overrides.getOrElse("rs1", value),
      _.rs2         -> overrides.getOrElse("rs2", value),
      _.rd          -> overrides.getOrElse("rd", value),
      _.pcIdx       -> overrides.getOrElse("pcIdx", value),
      _.rs1Tag      -> overrides.getOrElse("rs1Tag", value),
      _.rs1TagReady -> overrides.getOrElse("rs1TagReady", value),
      _.rs2Tag      -> overrides.getOrElse("rs2Tag", value),
      _.rs2TagReady -> overrides.getOrElse("rs2TagReady", value),
      _.rdTag       -> overrides.getOrElse("rdTag", value),
      _.rs1Data     -> overrides.getOrElse("rs1Data", value),
      _.rs2Data     -> overrides.getOrElse("rs2Data", value),
      _.rdData      -> overrides.getOrElse("rdData", value),
      _.retired     -> overrides.getOrElse("retired", value),
      _.inst        -> overrides.getOrElse("inst", value)
    )
    mi
  }
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val out   = Vec(config.numPortsInt, Decoupled(new MI(config)))
  })

  val destage = Module(new DecodeStage(config))

  destage.io.in    <> io.in
  destage.io.pcIdx <> io.pcIdx
  io.out           <> destage.io.out
}
