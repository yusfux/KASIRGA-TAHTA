package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.CSRs

object CSROp extends ChiselEnum {
  val csrrw, csrrs, csrrc, csrrwi, csrrsi, csrrci = Value
  val values                                      = IndexedSeq(csrrw, csrrs, csrrc, csrrwi, csrrsi, csrrci)

  def toBitpat(op: CSROp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def str(op: CSROp.Type): String =
    toBitpat(op).rawString
}

object CSRMask {
  val misa          = "b00000000000000000001000000100011".U(32.W)
  val mvendorid     = "b00000000000000000000000000000000".U(32.W)
  val marchid       = "b00000000000000000000000000000000".U(32.W)
  val mimpid        = "b00000000000000000000000000000000".U(32.W)
  val mhartid       = "b00000000000000000000000000000000".U(32.W)
  val mstatus       = "b00000000000000011110000000000000".U(32.W)
  val mstatush      = "b00000000000000000000000000000000".U(32.W)
  val mtvec         = "b11111111111111111111111111111100".U(32.W)
  val mscratch      = "b11111111111111111111111111111111".U(32.W)
  val mcountinhibit = "b11111111111111111111111111111111".U(32.W)
  val mcycle        = "b11111111111111111111111111111111".U(32.W)
  val minstret      = "b11111111111111111111111111111111".U(32.W)
  val cycle         = "b00000000000000000000000000000000".U(32.W)
  val instret       = "b00000000000000000000000000000000".U(32.W)
  val mepc          = "b11111111111111111111111111111100".U(32.W)
  val mcause        = "b01111111111111111111111111111111".U(32.W)
  val mtval         = "b11111111111111111111111111111111".U(32.W)
}

object MCAUSE_CODE {
  val instAddrMisaligned  = 0.U
  val instAccessFault     = 1.U
  val illegalInst         = 2.U
  val breakPoint          = 3.U
  val loadAddrMisaligned  = 4.U
  val loadAccessFault     = 5.U
  val storeAddrMisaligned = 6.U
  val storeAccessFault    = 7.U
  val ecallU              = 8.U
  val ecallS              = 9.U
  val ecallM              = 11.U
  val fetchPageFault      = 12.U
  val loadPageFault       = 13.U
  val storePageFault      = 15.U
  val softwareCheck       = 18.U
  val hardwareError       = 19.U
}

//TODO: in order to support the illegal instruction exception
// decode need to know about MISA and MSTATUS.FS for F extension
class CSRRegisters(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val exception = new Bundle {
      val valid = Input(Bool())
      val code  = Input(UInt(5.W))
      val pc    = Input(UInt(config.xlen.W))
      val inst  = Input(UInt(config.xlen.W))
    }

    val csr = new Bundle {
      val op    = Input(UInt(CSROp.getWidth.W))
      val addr  = Input(UInt(12.W))
      val wen   = Input(Bool())
      val wdata = Input(UInt(config.xlen.W))
      val ren   = Input(Bool())
      val rdata = Output(UInt(config.xlen.W))
    }

    val retired = Input(Bool())
    val fetchpc = Output(UInt(config.xlen.W))
  })

  val mvendorid = Reg(new Bundle {
    val bank   = UInt(25.W)
    val offset = UInt(7.W)
  })
  mvendorid := 0.U.asTypeOf(mvendorid)

  val marchid = Reg(new Bundle {
    val archid = UInt(32.W)
  })
  marchid := 0.U.asTypeOf(marchid)

  val mimpid = Reg(new Bundle {
    val impl = UInt(32.W)
  })
  mimpid := 0.U.asTypeOf(mimpid)

  val mhartid = Reg(new Bundle {
    val hartid = UInt(32.W)
  })
  mhartid := 0.U.asTypeOf(mhartid)

  val misa = Reg(new Bundle {
    val mxl   = UInt(2.W)
    val const = UInt(4.W)
    val extensions = new Bundle {
      val z = Bool(); val y = Bool(); val x = Bool(); val w = Bool()
      val v = Bool(); val u = Bool(); val t = Bool(); val s = Bool()
      val r = Bool(); val q = Bool(); val p = Bool(); val o = Bool()
      val n = Bool(); val m = Bool(); val l = Bool(); val k = Bool()
      val j = Bool(); val i = Bool(); val h = Bool(); val g = Bool()
      val f = Bool(); val e = Bool(); val d = Bool(); val c = Bool()
      val b = Bool(); val a = Bool()
    }
  })
  misa.mxl          := 1.U
  misa.const        := 0.U
  misa.extensions   := false.B.asTypeOf(misa.extensions)
  misa.extensions.a := true.B
  misa.extensions.b := true.B
  misa.extensions.f := true.B
  misa.extensions.i := true.B
  misa.extensions.m := true.B

  val mstatus = Reg(new Bundle {
    val sd    = UInt(1.W); val wpri2 = UInt(8.W); val tsr  = UInt(1.W)
    val tw    = UInt(1.W); val tvm   = UInt(1.W); val mxr  = UInt(1.W)
    val sum   = UInt(1.W); val mprv  = UInt(1.W); val xs   = UInt(2.W)
    val fs    = UInt(2.W); val mpp   = UInt(2.W); val vs   = UInt(2.W)
    val spp   = UInt(1.W); val mpie  = UInt(1.W); val ube  = UInt(1.W)
    val spie  = UInt(1.W); val wpri1 = UInt(1.W); val mie  = UInt(1.W)
    val wpri0 = UInt(1.W); val sie   = UInt(1.W); val wpri = UInt(1.W)
  })
  mstatus    := 0.U.asTypeOf(mstatus)
  mstatus.sd := mstatus.fs === 3.U

  val mstatush = Reg(new Bundle {
    val wpri0 = UInt(26.W)
    val mbe   = UInt(1.W)
    val sbe   = UInt(1.W)
    val wpri1 = UInt(4.W)
  })
  mstatush := 0.U.asTypeOf(mstatush)

  val mtvec = Reg(new Bundle {
    val base = UInt(30.W)
    val mode = UInt(2.W)
  })
  mtvec := 0.U.asTypeOf(mtvec)

  val mscratch = Reg(new Bundle {
    val scratch = UInt(32.W)
  })
  mscratch := 0.U.asTypeOf(mscratch)

  val mepc = Reg(new Bundle {
    val epc = UInt(32.W)
  })
  mepc := 0.U.asTypeOf(mepc)

  val mcause = Reg(new Bundle {
    val interrupt = UInt(1.W)
    val cause     = UInt(31.W)
  })
  mcause := 0.U.asTypeOf(mcause)

  val mtval = Reg(new Bundle {
    val tval = UInt(32.W)
  })
  mtval := 0.U.asTypeOf(mtval)

  val perfmonitors = new Bundle {
    val mcountinhibit = Reg(new Bundle {
      val hpm   = Vec(29, UInt(1.W))
      val ir    = UInt(1.W)
      val const = UInt(1.W)
      val cy    = UInt(1.W)
    })
    mcountinhibit := 0.U.asTypeOf(mcountinhibit)

    val mcycle    = RegInit(0.U(32.W))
    val mcycleh   = RegInit(0.U(32.W))
    val minstret  = RegInit(0.U(32.W))
    val minstreth = RegInit(0.U(32.W))

    val cycle    = RegInit(0.U(32.W))
    val cycleh   = RegInit(0.U(32.W))
    val instret  = RegInit(0.U(32.W))
    val instreth = RegInit(0.U(32.W))
  }

  // format: off
  val CSRList = List(
    (CSRs.misa         , misa                      , CSRMask.misa         ),
    (CSRs.mvendorid    , mvendorid                 , CSRMask.mvendorid    ),
    (CSRs.marchid      , marchid                   , CSRMask.marchid      ),
    (CSRs.mimpid       , mimpid                    , CSRMask.mimpid       ),
    (CSRs.mhartid      , mhartid                   , CSRMask.mhartid      ),
    (CSRs.mstatus      , mstatus                   , CSRMask.mstatus      ),
    (CSRs.mstatush     , mstatush                  , CSRMask.mstatush     ),
    (CSRs.mtvec        , mtvec                     , CSRMask.mtvec        ),
    (CSRs.mcountinhibit, perfmonitors.mcountinhibit, CSRMask.mcountinhibit),
    (CSRs.mcycle       , perfmonitors.mcycle       , CSRMask.mcycle       ),
    (CSRs.mcycleh      , perfmonitors.mcycleh      , CSRMask.mcycle       ),
    (CSRs.minstret     , perfmonitors.minstret     , CSRMask.minstret     ),
    (CSRs.minstreth    , perfmonitors.minstreth    , CSRMask.minstret     ),
    (CSRs.cycle        , perfmonitors.cycle        , CSRMask.cycle        ),
    (CSRs.cycleh       , perfmonitors.cycleh       , CSRMask.cycle        ),
    (CSRs.instret      , perfmonitors.instret      , CSRMask.instret      ),
    (CSRs.instreth     , perfmonitors.instreth     , CSRMask.instret      ),
    (CSRs.mscratch     , mscratch                  , CSRMask.mscratch     ),
    (CSRs.mepc         , mepc                      , CSRMask.mepc         ),
    (CSRs.mcause       , mcause                    , CSRMask.mcause       ),
    (CSRs.mtval        , mtval                     , CSRMask.mtval        )
  )
  // format: on

  when(perfmonitors.mcountinhibit.cy === 0.U) {
    perfmonitors.mcycle := perfmonitors.mcycle + 1.U
    when(perfmonitors.mcycle + 1.U === 0.U) {
      perfmonitors.mcycleh := perfmonitors.mcycleh + 1.U
    }

    perfmonitors.cycle := perfmonitors.cycle + 1.U
    when(perfmonitors.cycle + 1.U === 0.U) {
      perfmonitors.cycleh := perfmonitors.cycleh + 1.U
    }
  }

  when(perfmonitors.mcountinhibit.ir === 0.U && io.retired) {
    perfmonitors.minstret := perfmonitors.minstret + 1.U
    when(perfmonitors.minstret + 1.U === 0.U) {
      perfmonitors.minstreth := perfmonitors.minstreth + 1.U
    }

    perfmonitors.instret := perfmonitors.instret + 1.U
    when(perfmonitors.instret + 1.U === 0.U) {
      perfmonitors.instreth := perfmonitors.instreth + 1.U
    }
  }

  // format: off
  def writeCSR(oldVal: UInt, writeData: UInt, mask:UInt, op: CSROp.Type): UInt = {
    MuxLookup(op, oldVal)(Seq(
      CSROp.csrrw  -> ( (writeData & mask) | (oldVal & ~mask) ),
      CSROp.csrrs  -> ( (writeData & mask) | (oldVal        ) ),
      CSROp.csrrc  -> (~(writeData & mask) & (oldVal        ) ),
      CSROp.csrrwi -> ( (writeData & mask) | (oldVal & ~mask) ),
      CSROp.csrrsi -> ( (writeData & mask) | (oldVal        ) ),
      CSROp.csrrci -> (~(writeData & mask) & (oldVal        ) ),
    ))
  }
  // format: on

  val rawOp = Wire(UInt(CSROp.getWidth.W))
  rawOp := io.csr.op
  val (control, valid) = CSROp.safe(rawOp)

  when(io.csr.wen) {
    CSRList.foreach {
      case (csr, reg, mask) =>
        when(io.csr.addr === csr.U) {
          reg := writeCSR(reg.asUInt, io.csr.wdata, mask, control).asTypeOf(reg)
        }
    }
  }

  io.csr.rdata := 0.U
  when(io.csr.ren) {
    CSRList.foreach {
      case (csr, reg, mask) =>
        when(io.csr.addr === csr.U) {
          io.csr.rdata := reg.asTypeOf(UInt(config.xlen.W))
        }
    }
  }

  io.fetchpc := mepc.epc
  when(io.exception.valid) {
    io.fetchpc   := Cat(mtvec.base, 0.U(2.W))
    mepc.epc     := io.exception.pc
    mcause.cause := io.exception.code
    when(io.exception.code === MCAUSE_CODE.illegalInst) {
      mtval.tval := io.exception.inst
    }.otherwise {
      mtval.tval := io.exception.pc
    }
  }
}
