#include "encoding.h"

#if __riscv_xlen == 64
  # define LREG ld
  # define LREGU lwu
  # define SREG sd
  # define REGBYTES 8
  # define FMV fmv.d.x
#else
  # define LREG lw
  # define SREG sw
  # define LREGU lw
  # define REGBYTES 4
  # define FMV fmv.w.x
#endif

  .section ".text.init"
  .globl _start
_start:
  # init arch RF, specific to wood
  addi x1, zero  , 0
  addi x2, zero  , 0
  addi x3, zero  , 0
  addi x4, zero  , 0
  addi x5, zero  , 0
  addi x6, zero  , 0
  addi x7, zero  , 0
  addi x8, zero  , 0
  addi x9, zero  , 0
  addi x10, zero  , 0
  addi x11, zero  , 0
  addi x12, zero  , 0
  addi x13, zero  , 0
  addi x14, zero  , 0
  addi x15, zero  , 0
  addi x16, zero  , 0
  addi x17, zero  , 0
  addi x18, zero  , 0
  addi x19, zero  , 0
  addi x20, zero  , 0
  addi x21, zero  , 0
  addi x22, zero  , 0
  addi x23, zero  , 0
  addi x24, zero  , 0
  addi x25, zero  , 0
  addi x26, zero  , 0
  addi x27, zero  , 0
  addi x28, zero  , 0
  addi x29, zero  , 0
  addi x30, zero  , 0
  addi x31, zero  , 0

  la sp, begin_signature
  LREG  x1, 0*REGBYTES(sp)
  LREG  x3, 1*REGBYTES(sp)
  LREG  x4, 2*REGBYTES(sp)
  LREG  x5, 3*REGBYTES(sp)
  LREG  x6, 4*REGBYTES(sp)
  LREG  x7, 5*REGBYTES(sp)
  LREG  x8, 6*REGBYTES(sp)
  LREG  x9, 7*REGBYTES(sp)
  LREG  x10,8*REGBYTES(sp)
  LREG  x11,9*REGBYTES(sp)
  LREG  x12,10*REGBYTES(sp)
  LREG  x13,11*REGBYTES(sp)
  LREG  x14,12*REGBYTES(sp)
  LREG  x15,13*REGBYTES(sp)
  LREG  x16,14*REGBYTES(sp)
  LREG  x17,15*REGBYTES(sp)
  LREG  x18,16*REGBYTES(sp)
  LREG  x19,17*REGBYTES(sp)
  LREG  x20,18*REGBYTES(sp)
  LREG  x21,19*REGBYTES(sp)
  LREG  x22,20*REGBYTES(sp)
  LREG  x23,21*REGBYTES(sp)
  LREG  x24,22*REGBYTES(sp)
  LREG  x25,23*REGBYTES(sp)
  LREG  x26,24*REGBYTES(sp)
  LREG  x27,25*REGBYTES(sp)
  LREG  x28,26*REGBYTES(sp)
  LREG  x29,27*REGBYTES(sp)
  LREG  x30,28*REGBYTES(sp)
  LREG  x31,29*REGBYTES(sp)

  # enable FPU and accelerator if present
  li t0, MSTATUS_FS | MSTATUS_XS

  # make sure XLEN agrees with compilation choice
  li t0, 1
  slli t0, t0, 31
#if __riscv_xlen == 64
  bgez t0, 1f
#else
  bltz t0, 1f
#endif
2:
  li a0, 1
  sw a0, tohost, t0
  j 2b
1:

#ifdef __riscv_flen
  # initialize FPU if we have one
  la t0, 1f

  fssr    x0
  FMV  f0, x1
  FMV  f1, x1
  FMV  f2, x2
  FMV  f3, x3
  FMV  f4, x4
  FMV  f5, x5
  FMV  f6, x6
  FMV  f7, x7
  FMV  f8, x8
  FMV  f9, x9
  FMV  f10, x10
  FMV  f11, x11
  FMV  f12, x12
  FMV  f13, x13
  FMV  f14, x14
  FMV  f15, x15
  FMV  f16, x16
  FMV  f17, x17
  FMV  f18, x18
  FMV  f19, x19
  FMV  f20, x20
  FMV  f21, x21
  FMV  f22, x22
  FMV  f23, x23
  FMV  f24, x24
  FMV  f25, x25
  FMV  f26, x26
  FMV  f27, x27
  FMV  f28, x28
  FMV  f29, x29
  FMV  f30, x30
  FMV  f31, x31
  .align 4
1:
#endif

  # initialize trap vector
  la t0, trap_entry

  la  tp, _end + 63
  and tp, tp, -64

  # get core id
  # for now, assume only 1 core
  li a1, 1

  # give each core 128KB of stack + TLS
#define STKSHIFT 17
  sll a2, a0, STKSHIFT
  add tp, tp, a2
  add sp, a0, 1
  sll sp, sp, STKSHIFT
  add sp, sp, tp

  j main

  .align 2
trap_entry:
  addi sp, sp, -272

  SREG x1, 1*REGBYTES(sp)
  SREG x2, 2*REGBYTES(sp)
  SREG x3, 3*REGBYTES(sp)
  SREG x4, 4*REGBYTES(sp)
  SREG x5, 5*REGBYTES(sp)
  SREG x6, 6*REGBYTES(sp)
  SREG x7, 7*REGBYTES(sp)
  SREG x8, 8*REGBYTES(sp)
  SREG x9, 9*REGBYTES(sp)
  SREG x10, 10*REGBYTES(sp)
  SREG x11, 11*REGBYTES(sp)
  SREG x12, 12*REGBYTES(sp)
  SREG x13, 13*REGBYTES(sp)
  SREG x14, 14*REGBYTES(sp)
  SREG x15, 15*REGBYTES(sp)
  SREG x16, 16*REGBYTES(sp)
  SREG x17, 17*REGBYTES(sp)
  SREG x18, 18*REGBYTES(sp)
  SREG x19, 19*REGBYTES(sp)
  SREG x20, 20*REGBYTES(sp)
  SREG x21, 21*REGBYTES(sp)
  SREG x22, 22*REGBYTES(sp)
  SREG x23, 23*REGBYTES(sp)
  SREG x24, 24*REGBYTES(sp)
  SREG x25, 25*REGBYTES(sp)
  SREG x26, 26*REGBYTES(sp)
  SREG x27, 27*REGBYTES(sp)
  SREG x28, 28*REGBYTES(sp)
  SREG x29, 29*REGBYTES(sp)
  SREG x30, 30*REGBYTES(sp)
  SREG x31, 31*REGBYTES(sp)

  lhu  a2, 0(a1)                  # load instruction into reg a1.

  # check the lower 2 bits to see if the instruction is 32-bit or 16-bit.
  andi a2, a2, 0x3;
  li t0, 0x3
  bne a2,t0,inst16

  inst32:                           # is 32-bit instruction then increment by 4
  addi a1,a1,0x4
  beqz x0,1f

inst16:
  addi a1,a1,0x2                  # is 16-bit instruction then increment by 2

1: 

  # use mcause to update the number of exceptions encountered in the program.

  # Remain in M-mode after eret
  li t0, MSTATUS_MPP

  LREG x1, 1*REGBYTES(sp)
  LREG x2, 2*REGBYTES(sp)
  LREG x3, 3*REGBYTES(sp)
  LREG x4, 4*REGBYTES(sp)
  LREG x5, 5*REGBYTES(sp)
  LREG x6, 6*REGBYTES(sp)
  LREG x7, 7*REGBYTES(sp)
  LREG x8, 8*REGBYTES(sp)
  LREG x9, 9*REGBYTES(sp)
  LREG x10, 10*REGBYTES(sp)
  LREG x11, 11*REGBYTES(sp)
  LREG x12, 12*REGBYTES(sp)
  LREG x13, 13*REGBYTES(sp)
  LREG x14, 14*REGBYTES(sp)
  LREG x15, 15*REGBYTES(sp)
  LREG x16, 16*REGBYTES(sp)
  LREG x17, 17*REGBYTES(sp)
  LREG x18, 18*REGBYTES(sp)
  LREG x19, 19*REGBYTES(sp)
  LREG x20, 20*REGBYTES(sp)
  LREG x21, 21*REGBYTES(sp)
  LREG x22, 22*REGBYTES(sp)
  LREG x23, 23*REGBYTES(sp)
  LREG x24, 24*REGBYTES(sp)
  LREG x25, 25*REGBYTES(sp)
  LREG x26, 26*REGBYTES(sp)
  LREG x27, 27*REGBYTES(sp)
  LREG x28, 28*REGBYTES(sp)
  LREG x29, 29*REGBYTES(sp)
  LREG x30, 30*REGBYTES(sp)
  LREG x31, 31*REGBYTES(sp)

  addi sp, sp, 272
  mret

.section ".tdata.begin"
.globl _tdata_begin
_tdata_begin:

.section ".tdata.end"
.globl _tdata_end
_tdata_end:

.section ".tbss.end"
.globl _tbss_end
_tbss_end:

.section ".tohost","aw",@progbits
.align 6
.globl tohost
tohost: .dword 0
.align 6
.globl fromhost
fromhost: .dword 0

# Exception generation numbers
.align 4
.globl ecause_num
ecause_num:
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0
    .dword 0

# Read only data
.section ".rodata"
.globl rodata
rodata:
    	.dword 0x5b707269762d6d6f
	.dword 0x64655d0a6d6f6465
	.dword 0x203d206d0a5b6765
	.dword 0x6e6572616c5d0a74
	.dword 0x6f74616c5f696e73
	.dword 0x7472756374696f6e
	.dword 0x73203d2031303030
	.dword 0x300a726567735f6e
	.dword 0x6f745f757365203d
	.dword 0x2078312c78320a63
	.dword 0x7573746f6d5f7472
	.dword 0x61705f68616e646c
	.dword 0x6572203d2046616c
	.dword 0x73650a636f64655f
	.dword 0x73746172745f6164
	.dword 0x6472657373203d20
	.dword 0x3231343734383336
	.dword 0x34380a6465666175
	.dword 0x6c745f70726f6772
	.dword 0x616d5f6578697420
	.dword 0x3d20547275650a64
	.dword 0x656c65676174696f
	.dword 0x6e203d2034303935
	.dword 0x0a5b6973612d696e
	.dword 0x737472756374696f
	.dword 0x6e2d646973747269
	.dword 0x627574696f6e5d0a
	.dword 0x72656c5f73797320
	.dword 0x3d20300a72656c5f
	.dword 0x7379732e63737220
	.dword 0x3d20300a72656c5f
	.dword 0x72763332692e6374
	.dword 0x726c203d20302e30
	.dword 0x350a72656c5f7276
	.dword 0x3332692e636f6d70
	.dword 0x757465203d20302e
	.dword 0x32350a72656c5f72
	.dword 0x763332692e646174
	.dword 0x61203d20302e370a
	.dword 0x72656c5f72763332
	.dword 0x692e66656e636520
	.dword 0x3d20300a72656c5f
	.dword 0x72763634692e636f
	.dword 0x6d70757465203d20
	.dword 0x300a72656c5f7276
	.dword 0x3634692e64617461
	.dword 0x203d20300a72656c
	.dword 0x5f72763332692e7a
	.dword 0x6261203d20300a72
	.dword 0x656c5f7276363469
	.dword 0x2e7a6261203d2030
	.dword 0x0a72656c5f727633
	.dword 0x32692e7a6262203d
	.dword 0x20300a72656c5f72
	.dword 0x763634692e7a6262
	.dword 0x203d20300a72656c
	.dword 0x5f72763332692e7a
	.dword 0x6263203d20300a72
	.dword 0x656c5f7276333269
	.dword 0x2e7a6273203d2030
	.dword 0x0a72656c5f727633
	.dword 0x32692e7a6265203d
	.dword 0x20300a72656c5f72
	.dword 0x763634692e7a6265
	.dword 0x203d20300a72656c
	.dword 0x5f72763332692e7a
	.dword 0x6266203d20300a72
	.dword 0x656c5f7276363469
	.dword 0x2e7a6266203d2030
	.dword 0x0a72656c5f727636
	.dword 0x34692e7a626d203d
	.dword 0x20300a72656c5f72
	.dword 0x763332692e7a6270
	.dword 0x203d20300a72656c
	.dword 0x5f72763634692e7a
	.dword 0x6270203d20300a72
	.dword 0x656c5f7276333269
	.dword 0x2e7a6272203d2030
	.dword 0x0a72656c5f727636
	.dword 0x34692e7a6272203d
	.dword 0x20300a72656c5f72
	.dword 0x763332692e7a6274
	.dword 0x203d20300a72656c
	.dword 0x5f72763634692e7a
	.dword 0x6274203d20300a72
	.dword 0x656c5f727633326d
	.dword 0x203d20300a72656c
	.dword 0x5f727636346d203d
	.dword 0x20300a72656c5f72
	.dword 0x76333261203d2030
	.dword 0x0a72656c5f727636
	.dword 0x3461203d20300a72
	.dword 0x656c5f7276333266
	.dword 0x203d20300a72656c
	.dword 0x5f7276363466203d
	.dword 0x20300a72656c5f72
	.dword 0x76333264203d2030
	.dword 0x0a72656c5f727636
	.dword 0x3464203d20300a72
	.dword 0x656c5f7276632e63
	.dword 0x74726c203d20300a
	.dword 0x72656c5f7276632e
	.dword 0x636f6d7075746520
	.dword 0x3d20300a72656c5f
	.dword 0x7276632e7370203d
	.dword 0x20300a72656c5f72
	.dword 0x76632e6461746120
	.dword 0x3d20300a72656c5f
	.dword 0x7276632e66646174
	.dword 0x61203d20300a7265
	.dword 0x6c5f72763332632e
	.dword 0x636f6d7075746520
	.dword 0x3d20300a72656c5f
	.dword 0x72763332632e6374
	.dword 0x726c203d20300a72
	.dword 0x656c5f7276333263
	.dword 0x2e6664617461203d
	.dword 0x20300a72656c5f72
	.dword 0x763634632e636f6d
	.dword 0x70757465203d2030
	.dword 0x0a72656c5f727636
	.dword 0x34632e6461746120
	.dword 0x3d20300a5b666c6f
	.dword 0x61742d726f756e64
	.dword 0x696e675d0a726e65
	.dword 0x203d2031300a7274
	.dword 0x7a203d2031300a72
	.dword 0x646e203d2031300a
	.dword 0x727570203d203130
	.dword 0x0a726d6d203d2031
	.dword 0x300a5b6272616e63
	.dword 0x682d636f6e74726f
	.dword 0x6c5d0a6261636b77
	.dword 0x6172642d70726f62
	.dword 0x6162696c69747920
	.dword 0x3d20302e350a626c
	.dword 0x6f636b2d73697a65
	.dword 0x203d20370a5b7265
	.dword 0x63757273696f6e2d
	.dword 0x6f7074696f6e735d
	.dword 0x0a72656375727369
	.dword 0x6f6e2d656e61626c
	.dword 0x65203d2046616c73
	.dword 0x650a726563757273
	.dword 0x696f6e2d64657074
	.dword 0x68203d2031300a72
	.dword 0x6563757273696f6e
	.dword 0x2d63616c6c73203d
	.dword 0x20350a5b61636365
	.dword 0x73732d7365637469
	.dword 0x6f6e735d0a626567
	.dword 0x696e5f7369676e61
	.dword 0x74757265203d2030
	.dword 0x7838303039313030
	.dword 0x302c307838303039
	.dword 0x353030302c72770a
	.dword 0x5b6373722d736563
	.dword 0x74696f6e735d0a73
	.dword 0x656374696f6e7320
	.dword 0x3d20307830313030
	.dword 0x3a30786466662c20
	.dword 0x30783332352c2030
	.dword 0x783530303a307866
	.dword 0x66660a5b75736572
	.dword 0x2d66756e6374696f
	.dword 0x6e735d0a66756e63
	.dword 0x31203d207b303a22
	.dword 0x6164642078302c78
	.dword 0x302c7830227d0a5b
	.dword 0x7377697463682d70
	.dword 0x7269762d6d6f6465
	.dword 0x735d0a7377697463
	.dword 0x685f6d6f64657320
	.dword 0x3d2046616c73650a
	.dword 0x6e756d5f73776974
	.dword 0x63686573203d2030
	.dword 0x0a5b692d63616368
	.dword 0x655d0a6e756d5f63
	.dword 0x616c6c73203d2030
	.dword 0x0a6e756d5f627974
	.dword 0x65735f7065725f62
	.dword 0x6c6f636b203d2031
	.dword 0x360a6e756d5f626c
	.dword 0x6f636b73203d2038
	.dword 0x0a6e756d5f637963
	.dword 0x6c6573203d203130
	.dword 0x0a5b642d63616368
	.dword 0x655d0a6e756d5f63
	.dword 0x616c6c73203d2030
	.dword 0x0a6e756d5f627974
	.dword 0x65735f7065725f62
	.dword 0x6c6f636b203d2031
	.dword 0x360a6e756d5f626c
	.dword 0x6f636b73203d2038
	.dword 0x0a6e756d5f637963
	.dword 0x6c6573203d203130
	.dword 0x0a5b657863657074
	.dword 0x696f6e2d67656e65
	.dword 0x726174696f6e5d0a
	.dword 0x6563617573653030
	.dword 0x203d20300a656361
	.dword 0x7573653031203d20
	.dword 0x300a656361757365
	.dword 0x3032203d20300a65
	.dword 0x6361757365303320
	.dword 0x3d20300a65636175
	.dword 0x73653034203d2030
	.dword 0x0a65636175736530
	.dword 0x35203d20300a6563
	.dword 0x617573653036203d
	.dword 0x20300a6563617573
	.dword 0x653037203d20300a
	.dword 0x6563617573653038
	.dword 0x203d20300a656361
	.dword 0x7573653039203d20
	.dword 0x300a656361757365
	.dword 0x3130203d20300a65
	.dword 0x6361757365313120
	.dword 0x3d20300a65636175
	.dword 0x73653132203d2030
	.dword 0x0a65636175736531
	.dword 0x33203d20300a6563
	.dword 0x617573653134203d
	.dword 0x20300a5b64617461
	.dword 0x2d68617a61726473
	.dword 0x5d0a7261775f7072
	.dword 0x6f62203d20302e35
	.dword 0x0a7761725f70726f
	.dword 0x62203d20302e350a
	.dword 0x7761775f70726f62
	.dword 0x203d20302e350a6e
	.dword 0x756d5f726567735f
	.dword 0x6c6f6f6b62656869
	.dword 0x6e64203d20380a5b
	.dword 0x70726f6772616d2d
	.dword 0x6d6163726f5d0a70
	.dword 0x72655f70726f6772
	.dword 0x616d5f6d6163726f
	.dword 0x203d206164642078
	.dword 0x302c78302c78300a
	.dword 0x706f73745f70726f
	.dword 0x6772616d5f6d6163
	.dword 0x726f203d20616464
	.dword 0x2078302c78302c78
	.dword 0x300a7072655f6272
	.dword 0x616e63685f6d6163
	.dword 0x726f203d20616464
	.dword 0x2078302c78302c78
	.dword 0x300a706f73745f62
	.dword 0x72616e63685f6d61
	.dword 0x63726f203d206164
	.dword 0x642078302c78302c
	.dword 0x78300a6563617573
	.dword 0x653030203d207261
	.dword 0x6e646f6d0a656361
	.dword 0x7573653031203d20
	.dword 0x72616e646f6d0a65
	.dword 0x6361757365303220
	.dword 0x3d2072616e646f6d
	.dword 0x0a65636175736530
	.dword 0x33203d2072616e64
	.dword 0x6f6d0a6563617573
	.dword 0x653034203d207261
	.dword 0x6e646f6d0a656361
	.dword 0x7573653035203d20
	.dword 0x72616e646f6d0a65
	.dword 0x6361757365303620
	.dword 0x3d2072616e646f6d
	.dword 0x0a65636175736530
	.dword 0x37203d2072616e64
	.dword 0x6f6d0a6563617573
	.dword 0x653038203d207261
	.dword 0x6e646f6d0a656361
	.dword 0x7573653039203d20
	.dword 0x72616e646f6d0a65
	.dword 0x6361757365313020
	.dword 0x3d2072616e646f6d
	.dword 0x0a65636175736531
	.dword 0x31203d2072616e64
	.dword 0x6f6d0a6563617573
	.dword 0x653132203d207261
	.dword 0x6e646f6d0a656361
	.dword 0x7573653133203d20
	.dword 0x72616e646f6d0a65
	.dword 0x6361757365313420
	.dword 0x3d2072616e646f6d
	.dword 0x0a5b73656c662d63
	.dword 0x6865636b696e675d
	.dword 0x0a72617465203d20
	.dword 0x3130300a74657374
	.dword 0x5f706173735f6d61
	.dword 0x63726f203d206c61
	.dword 0x2020202020207370
	.dword 0x2c20626567696e5f
	.dword 0x7369676e61747572
	.dword 0x653b206164646920
	.dword 0x20202073702c2073
	.dword 0x702c20322a524547
	.dword 0x42595445533b206c
	.dword 0x6920202020202074
	.dword 0x312c203078666666
	.dword 0x66663b2053524547
	.dword 0x2020202074312c20
	.dword 0x302a524547425954
	.dword 0x4553287370290a74
	.dword 0x6573745f6661696c
	.dword 0x5f6d6163726f203d
	.dword 0x206164642078302c
	.byte 0x78
	.byte 0x30
	.byte 0x2c
	.byte 0x78
	.byte 0x30
	.byte 0xa
