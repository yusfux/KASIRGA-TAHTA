package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig
import wood.exu.DecodeConfig.{OPSRC1_IRF, OPSRC1_PC, OPSRC2_IMM, OPSRC2_IRF}

// format: off
object ALUOp extends ChiselEnum {
  val sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass, beq, bne, blt, bge, bltu, bgeu, jal, jalr,
      andn, bclr, bclri, bext, bexti, binv, binvi, bset, bseti, clmul, clmulh, clmulr, clz, cpop, ctz, max, 
      maxu, min, minu, orc_b, orn, rev8, rol, ror, rori, sext_b, sext_h, sh1add, sh2add, sh3add, xnor, zext_h = Value

  val values = IndexedSeq(sub, add, xor, or, and, sll, srl, sra, slt, sltu, pass, beq, bne, blt, bge, bltu, bgeu, jal, jalr,
                          andn, bclr, bclri, bext, bexti, binv, binvi, bset, bseti, clmul, clmulh, clmulr, clz, cpop, ctz, max, 
                          maxu, min, minu, orc_b, orn, rev8, rol, ror, rori, sext_b, sext_h, sh1add, sh2add, sh3add, xnor, zext_h)

  def toBitpat(op: ALUOp.Type): BitPat =
    BitPat(op.litValue.U(getWidth.W))

  def str(op: ALUOp.Type): String =
    toBitpat(op).rawString
}
// format: on

class ALU(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MI(config)))
    val out = Decoupled(new MI(config))
  })

  val shamt = if (config.xlen > 1) log2Ceil(config.xlen) - 1 else 0 // Shift amount.

  val rawOp = Wire(UInt(ALUOp.getWidth.W))
  rawOp := io.in.bits.exOp
  val (control, valid) = ALUOp.safe(rawOp)

  // assert(valid, "Enum state must be valid, got %d!", io.mi.bits.exOp) // https://github.com/llvm/circt/issues/6970

  val data1 = MuxCase(
    io.in.bits.rs1Data,
    Array(
      (io.in.bits.opsrc1 === Integer.parseInt(OPSRC1_IRF, 2).U) -> io.in.bits.rs1Data,
      (io.in.bits.opsrc1 === Integer.parseInt(OPSRC1_PC, 2).U)  -> io.in.bits.pc
    ).toIndexedSeq
  )

  val isStore = (io.in.bits.lsType === Integer.parseInt(DecodeConfig.LS_T_S, 2).U)

  val data2 = MuxCase(
    io.in.bits.rs2Data,
    Array(
      (isStore)                                                 -> io.in.bits.imm,
      (io.in.bits.opsrc2 === Integer.parseInt(OPSRC2_IRF, 2).U) -> io.in.bits.rs2Data,
      (io.in.bits.opsrc2 === Integer.parseInt(OPSRC2_IMM, 2).U) -> io.in.bits.imm
    ).toIndexedSeq
  )

  val arithmeticData1 = Mux(control === ALUOp.sub, Cat(data1, 1.U(1.W)), Cat(data1, 0.U(1.W)))
  val arithmeticData2 = Mux(control === ALUOp.sub, Cat(~data2, 1.U(1.W)), Cat(data2, 0.U(1.W)))
  val resultAdd       = arithmeticData1 + arithmeticData2

  val result = Wire(UInt(config.xlen.W))
  val pc     = Wire(UInt(config.xlen.W))

  // format: off
  result := 0.U
  switch(control) {
    is(ALUOp.sub, ALUOp.add)  { result := resultAdd(config.xlen, 1)                }
    is(ALUOp.jal, ALUOp.jalr) { result := io.in.bits.pc + 4.U                      }
    is(ALUOp.xor)             { result := data1 ^ data2                            }
    is(ALUOp.or)              { result := data1 | data2                            }
    is(ALUOp.and)             { result := data1 & data2                            }
    is(ALUOp.sll)             { result := data1 << data2(shamt, 0)                 }
    is(ALUOp.srl)             { result := data1 >> data2(shamt, 0)                 }
    is(ALUOp.sra)             { result := (data1.asSInt >> data2(shamt, 0)).asUInt }
    is(ALUOp.slt)             { result := (data1.asSInt < data2.asSInt)            }
    is(ALUOp.sltu)            { result := (data1 < data2).asUInt                   }
    is(ALUOp.pass)            { result := data2                                    }

    is(ALUOp.andn)            { result := data1 & ~data2                           }
    is(ALUOp.bclr)            { result := data1 & ~(1.U << data2(shamt, 0))        }
    is(ALUOp.bclri)           { result := data1 & ~(1.U << data2(shamt, 0))        }
    is(ALUOp.bext)            { result := (data1 >> data2(shamt, 0)) & 1.U         }
    is(ALUOp.bexti)           { result := (data1 >> data2(shamt, 0)) & 1.U         }
    is(ALUOp.binv)            { result := data1 ^ (1.U << data2(shamt, 0))         }
    is(ALUOp.binvi)           { result := data1 ^ (1.U << data2(shamt, 0))         }
    is(ALUOp.bset)            { result := data1 | (1.U << data2(shamt, 0))         }
    is(ALUOp.bseti)           { result := data1 | (1.U << data2(shamt, 0))         }
    is(ALUOp.clmul)           { result := (0 until 32).foldLeft(0.U(32.W)) { (output, i) => Mux(data2(i), output ^ (data1 << i)                      , output) }}
    is(ALUOp.clmulh)          { result := (0 until 32).foldLeft(0.U(32.W)) { (output, i) => Mux(data2(i), output ^ (data1 >> (32 - i))               , output) }}
    is(ALUOp.clmulr)          { result := (0 until 32).foldLeft(0.U(32.W)) { (output, i) => Mux(data2(i), output ^ (data1 >> (32 - i - 1))           , output) }}
    is(ALUOp.clz)             { result := Mux(data1 === 0.U, config.xlen.U, PriorityEncoder(data1.asBools.reverse)) }
    is(ALUOp.cpop)            { result := PopCount(data1)                                                           }
    is(ALUOp.ctz)             { result := Mux(data1 === 0.U, config.xlen.U, PriorityEncoder(data1.asBools))         }
    is(ALUOp.max)             { result := Mux(data1.asSInt > data2.asSInt, data1, data2)                            }
    is(ALUOp.maxu)            { result := Mux(data1.asUInt > data2.asUInt, data1, data2)                            }
    is(ALUOp.min)             { result := Mux(data1.asSInt > data2.asSInt, data2, data1)                            }
    is(ALUOp.minu)            { result := Mux(data1.asUInt > data2.asUInt, data2, data1)                            }
    is(ALUOp.orc_b)           { result := Cat(Seq.tabulate(4)(i => Mux(data1(8 * i + 7, 8 * i).orR, 0xFF.U(8.W), 0.U(8.W))).reverse) }
    is(ALUOp.orn)             { result := data1 | ~data2                                                            }
    is(ALUOp.rev8)            { result := Cat(Seq.tabulate(4)(i => data1(8 * i + 7, 8 * i)))                        }
    is(ALUOp.rol)             { result := (data1 << data2(shamt, 0)) | (data1 >> (config.xlen.U - data2(shamt, 0))) }
    is(ALUOp.ror)             { result := (data1 >> data2(shamt, 0)) | (data1 << (config.xlen.U - data2(shamt, 0))) }
    is(ALUOp.rori)            { result := (data1 >> data2(shamt, 0)) | (data1 << (config.xlen.U - data2(shamt, 0))) }
    is(ALUOp.sext_b)          { result := Cat(Fill(24, data1(7)), data1(7, 0))                                      }
    is(ALUOp.sext_h)          { result := Cat(Fill(16, data1(15)), data1(15, 0))                                    }
    is(ALUOp.sh1add)          { result := (data2 + (data1 << 1))                                                    }
    is(ALUOp.sh2add)          { result := (data2 + (data1 << 2))                                                    }
    is(ALUOp.sh3add)          { result := (data2 + (data1 << 3))                                                    }
    is(ALUOp.xnor)            { result := ~(data1 ^ data2)                                                          }
    is(ALUOp.zext_h)          { result := Cat(Fill(16, 0.U), data1(15, 0))                                          }

  }

  pc := 0.U
  switch(control) {
    is(ALUOp.beq)  { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.bne)  { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.blt)  { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.bge)  { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.bltu) { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.bgeu) { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.jal)  { pc := io.in.bits.pc + io.in.bits.imm }
    is(ALUOp.jalr) { pc := io.in.bits.rs1Data + io.in.bits.imm }
  }

  io.out.bits          := io.in.bits
  io.out.bits.rdData   := result
  io.out.bits.targetPC := pc
  io.out.valid         := io.in.valid
  io.in.ready          := io.out.ready

  switch(control) {
    is(ALUOp.beq)  { io.out.bits.taken := io.in.bits.rs1Data        === io.in.bits.rs2Data }
    is(ALUOp.bne)  { io.out.bits.taken := io.in.bits.rs1Data        =/= io.in.bits.rs2Data }
    is(ALUOp.blt)  { io.out.bits.taken := io.in.bits.rs1Data.asSInt <   io.in.bits.rs2Data.asSInt }
    is(ALUOp.bge)  { io.out.bits.taken := io.in.bits.rs1Data.asSInt >=  io.in.bits.rs2Data.asSInt }
    is(ALUOp.bltu) { io.out.bits.taken := io.in.bits.rs1Data        <   io.in.bits.rs2Data }
    is(ALUOp.bgeu) { io.out.bits.taken := io.in.bits.rs1Data        >=  io.in.bits.rs2Data }
    is(ALUOp.jal)  { io.out.bits.taken := 1.U }
    is(ALUOp.jalr) { io.out.bits.taken := 1.U }
  }
// format: on
}
