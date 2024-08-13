package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.BranchPredictorBus

class BranchPredictor(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val pred  = Output(new Bundle {
      val fetchpc = UInt(config.pcWidth.W)
      val mask    = Vec(config.nWide, Bool())
      val en      = Bool()
    })
  })

  val pclen = config.pcWidth
  val bidxlen = log2Ceil(config.nWide)

  val btb = RegInit(VecInit(Seq.fill(config.btbdepth)(0.U((pclen + bidxlen + pclen).W)))) //fetchpc, pcidx, targetpc
  val ghr = RegInit(UInt(config.ghrWidth.W), 0.U)
  val bim = RegInit(VecInit(Seq.fill(1 << config.ghrWidth)(3.U(2.W))))

  io.pred.en             := false.B
  io.pred.mask.foreach(_ := false.B)
  io.pred.fetchpc        := 0.U

}
