package wood.exu

import chisel3._
import chisel3.util._
import wood.WoodConfig

class BlackBoxFPU(config: WoodConfig) extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clk_i  = Input(Clock())
    val rst_ni = Input(Bool())
    
    val operands_i     = Input(Vec(3, UInt(config.dataWidth.W)))
    val rnd_mode_i     = Input(UInt(3.W))
    val op_i           = Input(UInt(4.W))
    val op_mod_i       = Input(Bool())
    val src_fmt_i      = Input(UInt(3.W))
    val dst_fmt_i      = Input(UInt(3.W))
    val int_fmt_i      = Input(UInt(2.W))
    val vectorial_op_i = Input(Bool())
    val tag_i          = Input(Bool())
    val simd_mask_i    = Input(UInt(1.W))
    
    val in_valid_i = Input(Bool())
    val in_ready_o = Output(Bool())
    val flush_i    = Input(Bool())
    
    val result_o = Output(UInt(config.dataWidth.W))
    val status_o = Output(UInt(5.W))
    val tag_o    = Output(UInt(config.dataWidth.W))
    
    val out_valid_o = Output(Bool())
    val out_ready_i = Input(Bool())
    
    val busy_o = Output(Bool())
  })

  addResource("cvfpu/src/fpnew_top.sv")
}