package wood.periph

import chisel3._
import chisel3.util._

class UartTx extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(16.W))
    val we       = Input(Bool())
    val stall    = Input(Bool())
    val data_in  = Input(UInt(8.W))
    val full     = Output(Bool())
    val empty    = Output(Bool())
    val tx       = Output(Bool())
  })

  val sIdle :: sStartBit :: sData0 :: sData1 :: sData2 :: sData3 :: sData4 :: sData5 :: sData6 :: sData7 :: sStopBit0 :: Nil = Enum(11)
  val state                                                                                                                  = RegInit(sIdle)
  val next                                                                                                                   = Wire(chiselTypeOf(state))

  val queue    = Reg(Vec(32, UInt(8.W)))
  val readPtr  = RegInit(0.U(5.W))
  val writePtr = RegInit(0.U(5.W))
  val limit    = readPtr - 1.U

  val counter      = RegInit(0.U(16.W))
  val uartClkPulse = RegInit(false.B)

  io.full  := limit === writePtr
  io.empty := readPtr === writePtr

  when(reset.asBool) {
    state        := sIdle
    readPtr      := 0.U
    writePtr     := 0.U
    counter      := 0.U
    uartClkPulse := false.B
  }.otherwise {
    when(io.we) {
      writePtr        := writePtr + 1.U
      queue(writePtr) := io.data_in
    }

    when(uartClkPulse) {
      state := next
      when(state === sStopBit0 && next === sIdle) {
        readPtr := readPtr + 1.U
      }
    }

    when(counter === io.baud_div) {
      counter      := 0.U
      uartClkPulse := true.B
    }.otherwise {
      counter      := counter + 1.U
      uartClkPulse := false.B
    }
  }

  next := MuxCase(
    sIdle,
    Array(
      (state(sIdle))     -> Mux(!io.empty && !io.stall, sStartBit, sIdle),
      (state(sStartBit)) -> sData0,
      (state(sData0))    -> sData1,
      (state(sData1))    -> sData2,
      (state(sData2))    -> sData3,
      (state(sData3))    -> sData4,
      (state(sData4))    -> sData5,
      (state(sData5))    -> sData6,
      (state(sData6))    -> sData7,
      (state(sData7))    -> sStopBit0,
      (state(sStopBit0)) -> sIdle
    ).toIndexedSeq
  )

  io.tx := MuxCase(
    true.B,
    Array(
      (state(sIdle))     -> true.B,
      (state(sStartBit)) -> false.B,
      (state(sData0))    -> queue(readPtr)(0),
      (state(sData1))    -> queue(readPtr)(1),
      (state(sData2))    -> queue(readPtr)(2),
      (state(sData3))    -> queue(readPtr)(3),
      (state(sData4))    -> queue(readPtr)(4),
      (state(sData5))    -> queue(readPtr)(5),
      (state(sData6))    -> queue(readPtr)(6),
      (state(sData7))    -> queue(readPtr)(7),
      (state(sStopBit0)) -> true.B
    ).toIndexedSeq
  )
}

class UartRx extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(16.W))
    val re       = Input(Bool())
    val stall    = Input(Bool())
    val data_out = Output(UInt(8.W))
    val full     = Output(Bool())
    val empty    = Output(Bool())
    val rx       = Input(Bool())
  })

  val sIdle :: sStartBit :: sData0 :: sData1 :: sData2 :: sData3 :: sData4 :: sData5 :: sData6 :: sData7 :: sStopBit :: Nil = Enum(11)
  val state                                                                                                                 = RegInit(sIdle)
  val next                                                                                                                  = Wire(chiselTypeOf(state))

  val queue        = Reg(Vec(32, UInt(8.W)))
  val readPtr      = RegInit(0.U(5.W))
  val writePtr     = RegInit(0.U(5.W))
  val counter      = RegInit(0.U(16.W))
  val uartClkPulse = RegInit(false.B)

  val limit = readPtr - 1.U
  io.full     := limit === writePtr
  io.empty    := readPtr === writePtr
  io.data_out := queue(readPtr)

  val startPattern = RegInit(0.U(4.W))
  val startR       = RegInit(false.B)

  when(reset.asBool) {
    state        := sIdle
    readPtr      := 0.U
    writePtr     := 0.U
    counter      := 0.U
    uartClkPulse := false.B
    startPattern := 0.U
    startR       := false.B
  }.otherwise {
    when(io.re) {
      readPtr := readPtr + 1.U
    }

    when(uartClkPulse) {
      state := next
      when(state === sStopBit && next === sIdle) {
        writePtr := writePtr + 1.U
        startR   := false.B
      }
    }

    when(counter === io.baud_div) {
      counter      := 0.U
      uartClkPulse := true.B
    }.otherwise {
      when(startR) { counter := counter + 1.U }
      uartClkPulse := false.B
    }

    startPattern := Cat(startPattern(2, 0), io.rx)

    when(startPattern === "b1100".U) {
      startR := true.B
      when(!startR) {
        uartClkPulse := true.B
        counter      := io.baud_div
      }
    }
  }

  next := MuxCase(
    sIdle,
    Array(
      (state(sIdle))     -> Mux(startR && !io.stall, sStartBit, sIdle),
      (state(sStartBit)) -> sData0,
      (state(sData0))    -> sData1,
      (state(sData1))    -> sData2,
      (state(sData2))    -> sData3,
      (state(sData3))    -> sData4,
      (state(sData4))    -> sData5,
      (state(sData5))    -> sData6,
      (state(sData6))    -> sData7,
      (state(sData7))    -> sStopBit,
      (state(sStopBit))  -> sIdle
    ).toIndexedSeq
  )

  when(uartClkPulse) {
    switch(state) {
      is(sData0) { queue(writePtr)(0) := io.rx }
      is(sData1) { queue(writePtr)(1) := io.rx }
      is(sData2) { queue(writePtr)(2) := io.rx }
      is(sData3) { queue(writePtr)(3) := io.rx }
      is(sData4) { queue(writePtr)(4) := io.rx }
      is(sData5) { queue(writePtr)(5) := io.rx }
      is(sData6) { queue(writePtr)(6) := io.rx }
      is(sData7) { queue(writePtr)(7) := io.rx }
    }
  }
}

class UartController extends Module {
  val io = IO(new Bundle {
    val wb_adr   = Input(UInt(2.W))
    val wb_dat_i = Input(UInt(32.W))
    val wb_we    = Input(Bool())
    val wb_stb   = Input(Bool())
    val wb_sel   = Input(UInt(4.W))
    val wb_cyc   = Input(Bool())
    val wb_ack   = Output(Bool())
    val wb_dat_o = Output(UInt(32.W))

    val uart_rx = Input(Bool())
    val uart_tx = Output(Bool())
  })

  val baud_div = RegInit(0.U(16.W))
  val tx_en    = RegInit(false.B)
  val tx_we    = RegInit(false.B)
  val rx_en    = RegInit(false.B)
  val rx_re    = RegInit(false.B)

  val uart_tx = Module(new UartTx)
  val uart_rx = Module(new UartRx)

  // Connect UART TX
  uart_tx.io.baud_div := baud_div
  uart_tx.io.we       := tx_we
  uart_tx.io.stall    := !tx_en
  uart_tx.io.data_in  := io.wb_dat_i(7, 0)
  val tx_full  = uart_tx.io.full
  val tx_empty = uart_tx.io.empty
  io.uart_tx := uart_tx.io.tx

  // Connect UART RX
  uart_rx.io.baud_div := baud_div
  uart_rx.io.re       := rx_re
  uart_rx.io.stall    := !rx_en
  val rx_data  = uart_rx.io.data_out
  val rx_full  = uart_rx.io.full
  val rx_empty = uart_rx.io.empty
  uart_rx.io.rx := io.uart_rx

  val wb_ack = RegInit(false.B)
  io.wb_ack := wb_ack

  val wb_dat_o = RegInit(0.U(32.W))
  io.wb_dat_o := wb_dat_o

  when(io.wb_cyc) {
    wb_ack := io.wb_stb & !wb_ack

    switch(io.wb_adr) {
      is(0.U) {
        when(io.wb_stb & io.wb_we & !wb_ack) {
          when(io.wb_sel(0)) {
            tx_en := io.wb_dat_i(0)
            rx_en := io.wb_dat_i(1)
          }
          when(io.wb_sel(3) & io.wb_sel(2)) {
            baud_div := io.wb_dat_i(31, 16)
          }
        }
        wb_dat_o := Cat(baud_div, 0.U(14.W), rx_en, tx_en)
      }
      is(1.U) {
        wb_dat_o := Cat(0.U(28.W), rx_empty, rx_full, tx_empty, tx_full)
      }
      is(2.U) {
        when(io.wb_stb & !wb_ack) {
          when(!rx_empty) {
            wb_dat_o := Cat(0.U(24.W), rx_data)
            rx_re    := true.B
          }
        }
      }
      is(3.U) {
        when(io.wb_stb & io.wb_we & !wb_ack) {
          when(!tx_full) {
            tx_we := io.wb_sel(0)
          }
        }
      }
    }
  }

  when(reset.asBool) {
    wb_ack   := false.B
    baud_div := 0.U
    rx_en    := false.B
    tx_en    := false.B
    rx_re    := false.B
    tx_we    := false.B
  }.otherwise {
    rx_re := false.B
    tx_we := false.B
  }
}
