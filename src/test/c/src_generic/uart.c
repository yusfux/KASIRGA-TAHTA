#include "uart.h"

//-----------------------------------------------
// print a single character.
//-----------------------------------------------
int uart_txfull() {
  uart_status uart_stat;
  uart_stat.bits = UART_STATUS;
  return uart_stat.fields.tx_full;
}

void zputchar(char c) {
  while (uart_txfull())
    ;
  // if (c == '\n')
  //	zputchar('\r');
  UART_WDATA = c;
}

//-----------------------------------------------
// print a string (char*).
//-----------------------------------------------

void print(const char *p) {
  while (*p)
    zputchar(*(p++));
}

//-----------------------------------------------
// scan a single character.
//-----------------------------------------------

int uart_rxempty() {
  uart_status uart_stat;
  uart_stat.bits = UART_STATUS;
  return uart_stat.fields.rx_empty;
}

char zgetchar() {
  while (1) {
    if (!uart_rxempty()) {
      return (char)UART_RDATA;
    }
  }
}

//-----------------------------------------------
// scan multiple characters.
//-----------------------------------------------

int zscan(char *buffer, int max_size, int echo) {
  char c = 0;
  int length = 0;

  while (1) {
    c = zgetchar();
    if (c == '\b') { // BACKSPACE
      if (length != 0) {
        if (echo) {
          print("\b \b"); // delete last char in console
        }
        buffer--;
        length--;
      }
    } else if (c == '\r') // carriage return
      break;
    else if ((c >= ' ') && (c <= '~') && (length < (max_size - 1))) {
      if (echo) {
        zputchar(c); // echo
      }
      *buffer++ = c;
      length++;
    }
  }
  *buffer = '\0'; // terminate string
  print("\n");

  return length;
}

//-----------------------------------------------
// string compare.
// compares all chars in two strings.
//-----------------------------------------------

int strcmp(const char *p1, const char *p2) {
  const unsigned char *s1 = (const unsigned char *)p1;
  const unsigned char *s2 = (const unsigned char *)p2;
  unsigned char c1, c2;
  do {
    c1 = (unsigned char)*s1++;
    c2 = (unsigned char)*s2++;
    if (c1 == '\0')
      return c1 - c2;
  } while (c1 == c2);
  return c1 - c2;
}

//-----------------------------------------------
// strlen
//-----------------------------------------------

size_t strlen(const char *s) {
  const char *p = s;
  while (*p)
    p++;
  return p - s;
}

void init_uart() {
  uart_ctrl uart_control;
  uart_control.fields.tx_en = 0x1;
  uart_control.fields.rx_en = 0x1;
  uart_control.fields.baud_div = CPU_CLK / BAUD_RATE;
  UART_CTRL = uart_control.bits;
}