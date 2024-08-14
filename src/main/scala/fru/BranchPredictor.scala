package wood.fru

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.BranchPredictorBus

class BranchPredictor(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val bpBus = Vec(config.nWide, Flipped(ValidIO(new BranchPredictorBus(config))))
    val pc    = Input(UInt(config.pcWidth.W))
    val pred  = Output(new Bundle {
      val fetchpc = UInt(config.pcWidth.W)
      val mask    = Vec(config.nWide, Bool())
      val en      = Bool()
    })
  })

  val taglen  = config.pcWidth - (log2Ceil(config.btbdepth) + log2Ceil(config.byteOffset))
  val pclen   = config.pcWidth
  val idxlen  = log2Ceil(config.nWide)

  object btbfield {
    val tagH = taglen + idxlen + pclen - 1
    val tagL =          idxlen + pclen
    val pciH = idxlen + pclen - 1
    val pciL =          pclen
    val tpcH = pclen - 1
    val tpcL =         0

  }

  object pcfield {
    val idxH = config.byteOffset + log2Ceil(config.btbdepth) - 1
    val idxL = log2Ceil(config.btbdepth)
    val tagH = config.pcWidth - 1
    val tagL = config.byteOffset + log2Ceil(config.btbdepth)
    val pciH = config.bankOffset + config.byteOffset - 1
    val pciL = config.byteOffset
  }

  val btb  = RegInit(VecInit(Seq.fill(config.btbdepth)(0.U((taglen + idxlen + pclen).W))))
  val ghr  = RegInit(UInt(config.ghrWidth.W), 0.U)
  val bim  = RegInit(VecInit(Seq.fill(1 << config.ghrWidth)(0.U(2.W))))
  val sghr = Wire(Vec(config.nWide, UInt(config.ghrWidth.W)))

  val idx = PriorityEncoder(io.bpBus.map(bp => bp.valid && bp.bits.mispredict))
  val mpreden    = io.bpBus.map(bp => bp.valid && bp.bits.mispredict).reduce(_ || _)
  val mpredtaken = io.bpBus(idx).bits.taken
  val mpredidx   = io.bpBus(idx).bits.pc(pcfield.idxH, pcfield.idxL) ^ sghr(idx)
  val mpredtag   = io.bpBus(idx).bits.pc(pcfield.tagH, pcfield.tagL)
  val mpredtpc   = io.bpBus(idx).bits.targetPC
  val mpredpci   = io.bpBus(idx).bits.pc(pcfield.pciH, pcfield.pciL)

  val predidx    = io.pc(pcfield.idxH, pcfield.idxL) ^ ghr
  val predtag    = io.pc(pcfield.tagH, pcfield.tagL)
  val predhit    = btb(predidx)(btbfield.tagH, btbfield.tagL) === predtag
  val predtaken  = bim(predidx)(1).asBool
  val predpc     = btb(predidx)(btbfield.tpcH, btbfield.tpcL)
  val predmask   = ((1.U << btb(predidx)(btbfield.pciH, btbfield.pciL)) - 1.U)(config.nWide - 1, 0) //TODO: THIS DOES NOT WORK

  sghr(0) := ghr
  when(io.bpBus(0).valid && ~io.bpBus(0).bits.exception) { sghr(0) := ghr << 1 | io.bpBus(0).bits.taken }
  (1 until config.nWide) foreach { i =>
    val valid = io.bpBus(i).valid && ~io.bpBus(i).bits.exception
    sghr(i) := Mux(valid, sghr(i - 1) << 1 | io.bpBus(i).bits.taken, sghr(i - 1))
  }
  ghr := Mux(mpreden, sghr(idx), sghr(config.nWide - 1))

  (0 until config.nWide) foreach {i =>
    val cpreden    = io.bpBus(i).valid && !io.bpBus(i).bits.mispredict && !io.bpBus(i).bits.exception
    val cpredidx   = io.bpBus(i).bits.pc(pcfield.idxH, pcfield.idxL) ^ sghr(i)
    val cpredtaken = io.bpBus(i).bits.taken

    when(cpreden && (~mpreden || i.U < idx)) {
      bim(cpredidx) := Mux(cpredtaken, Mux(bim(cpredidx) =/= "b11".U, bim(cpredidx) + 1.U, bim(cpredidx)),
                                        Mux(bim(cpredidx) =/= "b00".U, bim(cpredidx) - 1.U, bim(cpredidx)))
    }
  }

  when(mpreden) {
    btb(mpredidx) := Cat(mpredtag, mpredpci, mpredtpc)
    bim(mpredidx) := Mux(mpredtaken, Mux(bim(mpredidx) =/= "b11".U, bim(mpredidx) + 1.U, bim(mpredidx)),
                                        Mux(bim(mpredidx) =/= "b00".U, bim(mpredidx) - 1.U, bim(mpredidx)))
  }

  io.pred.en      := predhit && predtaken
  io.pred.mask    := VecInit(predmask.asBools)
  io.pred.fetchpc := predpc
}
