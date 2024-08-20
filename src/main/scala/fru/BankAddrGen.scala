package wood.fru

import chisel3._
import wood.WoodConfig

class BankAddrGenIO(config: WoodConfig) extends Bundle {
  val in = Input(new Bundle {
    val fetchpc = UInt(config.xlen.W)
  })

  val out = Output(new Bundle {
    val controller = Vec(
      config.nWide,
      new Bundle() {
        val pc = UInt(config.xlen.W)
      }
    )
  })
}

class BankAddrGen(config: WoodConfig) extends Module {
  val io = IO(new BankAddrGenIO(config))

  /*
  val bankCnt = config.nWide
  val bankOffset = log2Ceil(bankCnt)
  val addrOffset = log2Ceil(config.xlen >> 3)

  val bankSelect = io.in.fetchpc(bankOffset + addrOffset - 1, addrOffset)
  val bankIndex = Wire(Vec(bankCnt, UInt(1.W)))
  for (i <- 0 until bankCnt) {
    bankIndex(i) := Mux(bankSelect > i.U, 1.U, 0.U)
  }

  io.out.controller.zipWithIndex.foreach { case (controller, i) =>
    controller.pc := Cat(io.in.fetchpc(31, bankOffset + addrOffset) + bankIndex(i), io.in.fetchpc(bankOffset + addrOffset - 1, 0))
  }
   */

  for (i <- 0 until config.nWide) {
    io.out.controller(i).pc := io.in.fetchpc + (i * 4).U
  }
}
