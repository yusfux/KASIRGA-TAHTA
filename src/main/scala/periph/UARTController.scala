package wood.periph

import chisel3._
import chisel3.util._
import wood.WoodConfig

/*
 * FROM: Wishbone Interconnect Specification
 *   PERMISSION 3.40
 *   If a MASTER doesn’t generate wait states, then [STB_O] and [CYC_O] MAY be assigned the same signal
 */

class UARTController(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val wb_adr   = Input(UInt(4.W))
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

  val reciever    = Module(new UARTReciever(config))
  val transmitter = Module(new UARTTransmitter(config))

  val uartCtrl = new Bundle {
    val tx_en   = RegInit(false.B)
    val rx_en   = RegInit(false.B)
    val padding = WireInit(0.U(14.W))
    val baudDiv = RegInit(0.U(16.W))
  }

  val uartStatus = new Bundle {
    val tx_full  = transmitter.io.tx_full
    val tx_empty = transmitter.io.tx_empty
    val rx_full  = reciever.io.rx_full
    val rx_empty = reciever.io.rx_empty
    val padding  = WireInit(0.U(28.W))
  }

  val uartRdata = new Bundle {
    val data    = reciever.io.data
    val padding = WireInit(0.U(24.W))
  }

  // why the fuck asUInt does not work with fucking bundles????
  val uartstatus = Cat(uartStatus.padding, uartStatus.rx_empty, uartStatus.rx_full, uartStatus.tx_empty, uartStatus.tx_full)
  val uartctrl   = Cat(uartCtrl.baudDiv, uartCtrl.padding, uartCtrl.rx_en, uartCtrl.tx_en)
  val uartrdata  = Cat(uartRdata.padding, uartRdata.data)

  io.wb_ack   := RegNext(io.wb_stb && io.wb_cyc && !io.wb_ack, false.B)
  io.wb_dat_o := Mux(io.wb_adr === "h0".U, uartstatus, Mux(io.wb_adr === "h4".U, uartctrl, Mux(io.wb_adr === "h8".U, uartrdata, 0.U)))

  when(io.wb_stb && io.wb_cyc && io.wb_we && io.wb_adr === "h0".U) {
    uartCtrl.tx_en   := Mux(io.wb_sel(0), io.wb_dat_i(0), uartCtrl.tx_en)
    uartCtrl.rx_en   := Mux(io.wb_sel(0), io.wb_dat_i(1), uartCtrl.rx_en)
    uartCtrl.baudDiv := Mux(io.wb_sel(2) && io.wb_sel(3), io.wb_dat_i(31, 16), uartCtrl.baudDiv)
  }

  reciever.io.baud_div := uartCtrl.baudDiv
  reciever.io.rx_en    := uartCtrl.rx_en
  reciever.io.ren      := io.wb_cyc && io.wb_stb && io.wb_adr === "h8".U
  reciever.io.uart_rx  := io.uart_rx

  transmitter.io.baud_div := uartCtrl.baudDiv
  transmitter.io.tx_en    := uartCtrl.tx_en
  transmitter.io.wen      := io.wb_cyc && io.wb_stb && io.wb_adr === "hC".U
  transmitter.io.data     := io.wb_dat_i(7, 0)
  io.uart_tx              := transmitter.io.uart_tx
}

class UARTReciever(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(16.W))

    val ren  = Input(Bool())
    val data = Output(UInt(8.W))

    val rx_full  = Output(Bool())
    val rx_empty = Output(Bool())
    val rx_en    = Input(Bool())
    val uart_rx  = Input(Bool())
  })

  val buffer      = Module(new Queue(UInt(8.W), 32))
  val packet      = RegInit(VecInit(Seq.fill(8)(0.U(1.W))))
  val counter     = RegInit(0.U(16.W))
  val pulse       = counter === io.baud_div
  val (idx, wrap) = Counter(pulse, 8)

  io.rx_full          := ~buffer.io.enq.ready
  io.rx_empty         := ~buffer.io.deq.valid
  buffer.io.enq.bits  := packet.asUInt
  buffer.io.enq.valid := wrap
  io.data             := buffer.io.deq.bits
  buffer.io.deq.ready := io.ren

  object RXState extends ChiselEnum {
    val idle, recv = Value
  }

  val state = RegInit(RXState.idle)
  switch(state) {
    is(RXState.idle) {
      counter := 0.U
      when(~io.uart_rx && io.rx_en) {
        counter := counter + 1.U
        when(counter === io.baud_div / 2.U) {
          counter := 0.U
          state   := RXState.recv
        }
      }
    }
    is(RXState.recv) {
      counter := counter + 1.U
      when(pulse) {
        packet(idx) := io.uart_rx
        counter     := 0.U
      }
      when(wrap) {
        state := RXState.idle
      }
    }
  }
}

class UARTTransmitter(config: WoodConfig) extends Module {
  val io = IO(new Bundle {
    val baud_div = Input(UInt(16.W))

    val wen  = Input(Bool())
    val data = Input(UInt(8.W))

    val tx_full  = Output(Bool())
    val tx_empty = Output(Bool())
    val tx_en    = Input(Bool())
    val uart_tx  = Output(Bool())
  })

  val buffer      = Module(new Queue(UInt(8.W), 32))
  val packet      = Cat(1.U, buffer.io.deq.bits, 0.U)
  val counter     = RegInit(0.U(16.W))
  val pulse       = counter === io.baud_div
  val (idx, wrap) = Counter(pulse, 10)

  io.tx_empty         := ~buffer.io.deq.valid
  io.tx_full          := ~buffer.io.enq.ready
  buffer.io.enq.bits  := io.data
  buffer.io.enq.valid := io.wen
  buffer.io.deq.ready := wrap

  io.uart_tx := true.B

  object TXState extends ChiselEnum {
    val idle, send = Value
  }

  val state = RegInit(TXState.idle)
  switch(state) {
    is(TXState.idle) {
      io.uart_tx := true.B
      counter    := 0.U
      when(io.tx_en && !io.tx_empty) {
        state := TXState.send
      }
    }
    is(TXState.send) {
      io.uart_tx := packet(idx)
      counter    := Mux(pulse, 0.U, counter + 1.U)
      when(wrap) {
        state := TXState.idle
      }
    }
  }
}
