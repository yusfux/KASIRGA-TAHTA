package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.{Retirable, TagBus}

class LSOverrideRetire[T <: Retirable](gen: T)(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(gen.cloneType))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val out            = Decoupled(gen.cloneType)
  })

  io.out <> io.in

  val retireBusMatches = Wire(Vec(config.nWide, Bool()))

  for (j <- 0 until config.nWide) {
    retireBusMatches(j) := io.storeRetireBus(j).valid & (io.in.bits.rdTag === io.storeRetireBus(j).bits.tag)
  }

  io.out.bits.retired := io.in.bits.retired | retireBusMatches.asUInt.orR
}

class LSOverrideOperands(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val targetAddrI   = Input(UInt(config.xlen.W))
    val rs2DataI      = Input(UInt(config.xlen.W))
    val rdTagI        = Input(UInt(config.tagWidth.W))
    val operandReadyI = Input(Bool())

    val lsOperandBus = Flipped(Vec(config.nWide, ValidIO(new LSOperandBus(config))))

    val targetAddrO   = Output(UInt(config.xlen.W))
    val rs2DataO      = Output(UInt(config.xlen.W))
    val operandReadyO = Output(Bool())
  })

  val operandBusMatches    = Wire(Vec(config.nWide, Bool()))
  val operandBusMatchIndex = PriorityEncoder(operandBusMatches.asUInt)

  val operandTargetAddr = io.lsOperandBus(operandBusMatchIndex).bits.targetAddr
  val operandRs2        = io.lsOperandBus(operandBusMatchIndex).bits.rs2Data

  for (j <- 0 until config.nWide) {
    operandBusMatches(j) := io.lsOperandBus(j).valid & (io.rdTagI === io.lsOperandBus(j).bits.rdTag)
  }

  when(operandBusMatches.asUInt.orR) {
    io.targetAddrO := operandTargetAddr
    io.rs2DataO    := operandRs2
  }.otherwise {
    io.targetAddrO := io.targetAddrI
    io.rs2DataO    := io.rs2DataI
  }

  io.operandReadyO := operandBusMatches.asUInt.orR
}
