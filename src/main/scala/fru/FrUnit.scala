package wood.fru

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import wood.WoodConfig
import wood.exu.Bus
import wood.fru.{DecodeConfig, DecodeStage, DistributeStage, MIStage}

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
  val rs1TagValid = UInt(1.W)
  val rs2Tag      = UInt(config.tagWidth.W)
  val rs2TagValid = UInt(1.W)
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
      _.rs1TagValid -> overrides.getOrElse("rs1TagValid", value),
      _.rs2Tag      -> overrides.getOrElse("rs2Tag", value),
      _.rs2TagValid -> overrides.getOrElse("rs2TagValid", value),
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

class DCPipelineRegister[T <: Data](gen: T)(numPorts: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Vec(numPorts, Decoupled(gen.cloneType)))
    val out = Vec(numPorts, Decoupled(gen.cloneType))
  })

  val stall = Wire(Vec(numPorts, Bool()))
  stall := io.out.map(!_.ready)

  (0 until numPorts).foreach(j => {
    io.out(j).bits  := RegEnable(io.in(j).bits, 0.U.asTypeOf(gen.cloneType), !stall(j).asBool)
    io.out(j).valid := RegEnable(io.in(j).valid, 1.B, !stall(j).asBool)
    io.in(j).ready  := io.out(j).ready
  })
}

class FrUnit(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in         = Flipped(Vec(config.nWide, Decoupled(UInt(32.W))))
    val pcIdx      = Flipped(Decoupled(UInt(config.pcIndexWidth.W)))
    val forwardBus = Flipped(Vec(config.nWide, Decoupled(new Bus(config))))

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
    io.forwardBus(j).ready      := 1.U // TODO
  })
}
