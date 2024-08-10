package wood.fru

import chisel3._
import chisel3.util.{log2Ceil, _}
import wood.WoodConfig

class BranchPredictorIO(config: WoodConfig) extends Bundle {
  val in = Input(new Bundle {
    val fetchpc = UInt(config.pcWidth.W)
    val mispred = new Bundle {
      val fetchpc = UInt(config.pcWidth.W)
      val pcidx = UInt(log2Ceil(config.nWide).W)
      val targetpc = UInt(config.pcWidth.W)
      val en = Bool()
      val taken = Bool()
    }
  })

  val out = Output(new Bundle {
    val pred = new Bundle {
      val fetchpc = UInt(config.pcWidth.W)
      val mask = Vec(config.nWide, Bool())
      val en = Bool()
    }
  })
}

/* 
  TODO: THIS MAY BE VERY PROBLEMATIC, NEED TO CHECK IF IT CREATES A HUGE DESIGN
  SINCE WE USE CAM FOR BTB
 */
class BranchPredictor(config: WoodConfig) extends Module {
  val io = IO(new BranchPredictorIO(config))

  val pclen = config.pcWidth
  val bidxlen = log2Ceil(config.nWide)

  val btb = RegInit(VecInit(Seq.fill(config.btbdepth)(0.U((pclen + bidxlen + pclen).W)))) //fetchpc, pcidx, targetpc
  val ghr = RegInit(UInt(config.ghrWidth.W), 0.U)
  val bim = RegInit(VecInit(Seq.fill(1 << config.ghrWidth)(3.U(2.W))))

  /***************************************
                PREDICTION
   ***************************************/
  val rbimidx = io.in.fetchpc(config.ghrWidth - 1, 0) ^ ghr
  val rbtbidx = btb.indexWhere(x => x(pclen + bidxlen + pclen - 1, pclen + bidxlen) === io.in.fetchpc)
  val ishit = btb.map(x => x(pclen + bidxlen + pclen - 1, pclen + bidxlen) === io.in.fetchpc).reduce(_ | _)
  val istaken = bim(rbimidx)(1)

  /***************************************
                  MISPRED
   ***************************************/
  val wbimidx = io.in.mispred.fetchpc(config.ghrWidth - 1, 0) ^ ghr
  val (wbtbidx, _) = Counter(io.in.mispred.en, config.btbdepth)

  when(io.in.mispred.en) {
    btb(wbtbidx) := Cat(io.in.mispred.fetchpc, io.in.mispred.pcidx, io.in.mispred.targetpc)
    ghr := Cat(ghr << 1, io.in.mispred.taken)

    /* 
     idk why but if i do the above lines with multiple Mux statements, it gives an stackoverflow error somehow
     */
    when(io.in.mispred.taken && bim(wbimidx) =/= "b11".U) {
      bim(wbimidx) := bim(wbimidx) + 1.U
    }.elsewhen(~io.in.mispred.taken && bim(wbimidx) =/= "b00".U) {
      bim(wbimidx) := bim(wbimidx) - 1.U
    }
  }

  io.out.pred.fetchpc := btb(rbtbidx)(pclen + bidxlen - 1, 0)
  for(i <- 0 until config.nWide) {
    io.out.pred.mask(i) := btb(rbtbidx)(pclen + bidxlen - 1, pclen) >= i.U
  }
  io.out.pred.en := ishit && istaken
}
