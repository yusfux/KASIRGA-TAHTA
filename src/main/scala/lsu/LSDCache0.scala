package wood.lsu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.TagBus
import wood.util.WoodLSCMIPipelineRegister

class LSDCache0Stage(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in             = Flipped(Decoupled(new LSCMI(config)))
    val lsAtomBus      = Flipped(DecoupledIO(new LSCMI(config)))
    val lsDCache1Bus   = Flipped(ValidIO(new LSCMI(config)))
    val storeRetireBus = Flipped(Vec(config.nWide, ValidIO(new TagBus(config))))
    val flush          = Input(Bool())
    val frontRetired   = Input(Bool())
    val selfRetired    = Output(Bool())
    val outCache       = Decoupled(new LSCMI(config)) // comb out
    val outPass        = Decoupled(new LSCMI(config))
  })

  val pReg            = Module(new WoodLSCMIPipelineRegister(config))
  val retireOverrider = Module(new LSOverrideRetire(new LSCMI(config))(config))
  val arbiter         = Module(new Arbiter(new LSCMI(config), 2))
  val sq              = Module(new LSStoreQueue(config))
  val selfPass        = Wire(Decoupled(new LSCMI(config)))

  retireOverrider.io.storeRetireBus <> io.storeRetireBus

  retireOverrider.io.in <> io.in
  selfPass              <> retireOverrider.io.out
  selfPass.valid        := !sq.io.out.valid & retireOverrider.io.out.valid
  sq.io.camReadIn       := io.in.bits.addr
  sq.io.flush           := io.flush
  sq.io.in.valid        := io.in.fire
  sq.io.storeRetireBus  := io.storeRetireBus
  sq.io.in              <> io.lsAtomBus

  (0 until config.numDCacheLineBytes).foreach(j => {
    val selfAddr    = io.in.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
    val atomAddr    = io.lsAtomBus.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)
    val dcache1Addr = io.lsAtomBus.bits.addr(config.xlen - 1, config.dCacheAddrStartIndex)

    val atomByteUpdate    = io.lsAtomBus.bits.wStrobe(j) && (atomAddr === selfAddr) && io.lsAtomBus.valid
    val dcache1ByteUpdate = io.lsDCache1Bus.bits.wStrobe(j) && (dcache1Addr === selfAddr) && io.lsDCache1Bus.valid

    selfPass.bits.cacheLine(j) := MuxCase(
      io.in.bits.cacheLine(j),
      Array(
        (io.in.bits.wStrobe(j))       -> io.in.bits.cacheLine(j),
        (dcache1ByteUpdate)           -> io.lsDCache1Bus.bits.cacheLine(j),
        (atomByteUpdate)              -> io.lsAtomBus.bits.cacheLine(j),
        (sq.io.camReadOut.wStrobe(j)) -> sq.io.camReadOut.cacheLine(j)
      ).toIndexedSeq
    )
    selfPass.bits.wStrobe(j) := io.in.bits.wStrobe(j) | atomByteUpdate | dcache1ByteUpdate | sq.io.camReadOut.wStrobe(j)
  })

  io.in.ready := io.outPass.ready & io.outCache.ready & (arbiter.io.chosen === 1.U)

  arbiter.io.in(0) <> sq.io.out
  arbiter.io.in(1) <> io.in
  io.outCache      <> arbiter.io.out

  pReg.io.flush        := io.flush
  pReg.io.frontRetired := io.frontRetired
  io.selfRetired       := retireOverrider.io.out.bits.retired

  pReg.io.in <> selfPass
  io.outPass <> pReg.io.out
}
