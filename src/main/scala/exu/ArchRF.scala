package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.std.DCPipelineRegister

class ArchRegisterFileStage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in     = Flipped(Vec(config.nWide, Decoupled(new RetireMI(config))))
    val arfBus = Input(Vec(config.nWide, ValidIO(new ARFBus(config))))
    val archRF = Output(Vec(32, UInt(config.tagWidth.W)))
    val out    = Vec(config.nWide, Decoupled(new RetireMI(config)))
  })

  val archRegisterFile      = RegInit(VecInit(Seq.fill(32)(0.U(config.tagWidth.W))))
  val archRegisterFileValid = RegInit(VecInit(Seq.fill(32)(0.U(1.W))))
  val arfOverriders         = Seq.fill(config.nWide)(Module(new OverrideArfTagFromBus(config)))
  val pRegs                 = Seq.fill(config.nWide)(Module(new DCPipelineRegister(new RetireMI(config))(1)))

  val overridenRF = Wire(Vec(config.nWide, Decoupled(new RetireMI(config))))

  (0 until config.nWide).foreach(j => {
    overridenRF(j)               <> io.in(j)
    overridenRF(j).bits.arfTag   := archRegisterFile(io.in(j).bits.rd)
    overridenRF(j).bits.arfValid := archRegisterFileValid(io.in(j).bits.rd)
    overridenRF(j).valid         := io.in(j).valid

    arfOverriders(j).io.inBus <> io.arfBus
    arfOverriders(j).io.in    <> overridenRF(j)

    when(io.arfBus(j).valid) {
      archRegisterFile(io.arfBus(j).bits.rd)      := io.arfBus(j).bits.tag
      archRegisterFileValid(io.arfBus(j).bits.rd) := 1.U
      archRegisterFileValid(0.U)                  := 1.U
    }

    pRegs(j).io.valids(0) := io.in(j).valid // READ ARCH RF VALID
    pRegs(j).io.flush     := 0.B

    io.in(j).ready := arfOverriders(j).io.out.ready

    pRegs(j).io.in <> arfOverriders(j).io.out
    io.out(j)      <> pRegs(j).io.out
  })
  (0 until 32).foreach(j => {
    io.archRF(j) := archRegisterFile(j)
  })

}
