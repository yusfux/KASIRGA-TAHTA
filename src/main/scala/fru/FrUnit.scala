package wood.fru

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import wood.WoodConfig
import wood.exu.ForwardBus
import wood.fru.{DecodeConfig, DecodeStage, DistributeStage, MIStage}

case class MI(config: WoodConfig) extends Bundle {
  val isFloat       = UInt(DecodeConfig.subWidths(0).W)
  val operand       = UInt(DecodeConfig.subWidths(1).W)
  val write_rf      = UInt(DecodeConfig.subWidths(2).W)
  val exEngine      = UInt(DecodeConfig.subWidths(3).W)
  val exOp          = UInt(DecodeConfig.subWidths(4).W)
  val imm           = UInt(32.W) // TODO
  val rs1           = UInt(5.W)
  val rs2           = UInt(5.W)
  val rd            = UInt(5.W)
  val pc_idx        = UInt(config.pcIndexWidth.W)
  val rs1_tag       = UInt(config.tagWidth.W)
  val rs1_tag_valid = UInt(1.W)
  val rs2_tag       = UInt(config.tagWidth.W)
  val rs2_tag_valid = UInt(1.W)
  val rd_tag        = UInt(config.tagWidth.W)
  val rs1_data      = UInt(config.dataWidth.W)
  val rs2_data      = UInt(config.dataWidth.W)
  val rd_data       = UInt(config.dataWidth.W)
  val retired       = UInt(1.W)
  val inst          = UInt(32.W) // for testbench only
}

object MI { // for testbench only, set all values with specific overrides
  def apply(config: WoodConfig, value: UInt, overrides: Map[String, UInt] = Map.empty): MI = {
    val mi = new MI(config).Lit(
      _.isFloat       -> overrides.getOrElse("isFloat", value),
      _.operand       -> overrides.getOrElse("operand", value),
      _.write_rf      -> overrides.getOrElse("write_rf", value),
      _.exEngine      -> overrides.getOrElse("exEngine", value),
      _.exOp          -> overrides.getOrElse("exOp", value),
      _.imm           -> overrides.getOrElse("imm", value),
      _.rs1           -> overrides.getOrElse("rs1", value),
      _.rs2           -> overrides.getOrElse("rs2", value),
      _.rd            -> overrides.getOrElse("rd", value),
      _.pc_idx        -> overrides.getOrElse("pc_idx", value),
      _.rs1_tag       -> overrides.getOrElse("rs1_tag", value),
      _.rs1_tag_valid -> overrides.getOrElse("rs1_tag_valid", value),
      _.rs2_tag       -> overrides.getOrElse("rs2_tag", value),
      _.rs2_tag_valid -> overrides.getOrElse("rs2_tag_valid", value),
      _.rd_tag        -> overrides.getOrElse("rd_tag", value),
      _.rs1_data      -> overrides.getOrElse("rs1_data", value),
      _.rs2_data      -> overrides.getOrElse("rs2_data", value),
      _.rd_data       -> overrides.getOrElse("rd_data", value),
      _.retired       -> overrides.getOrElse("retired", value),
      _.inst          -> overrides.getOrElse("inst", value)
    )
    mi
  }
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in           = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx        = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val forwardBuses = Flipped(Vec(config.nWide, Decoupled(new ForwardBus(config))))

    val out = Vec(config.nWide, Decoupled(new MI(config)))
  })

  val destage = Module(new DecodeStage(config))
  val distage = Module(new DistributeStage(config))
  val mistage = Module(new MIStage(config))

  destage.io.in    <> io.in
  destage.io.pcIdx <> io.pcIdx
  destage.io.out   <> distage.io.in
  distage.io.toInt <> mistage.io.in
  mistage.io.out   <> io.out

  (0 until config.nWide).foreach(j => {
    distage.io.toFloat(j).ready := 1.U // TODO
    io.forwardBuses(j).ready    := 1.U // TODO
  })
}
