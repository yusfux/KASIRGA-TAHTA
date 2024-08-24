{ pkgs }:
pkgs.writers.writePython3Bin "genstarts" { } ''
  import argparse


  def main():
      parser = argparse.ArgumentParser(
          description="Generate generic start.S assembly file."
      )
      parser.add_argument(
          "-f",
          "--file",
          type=str,
          required=True,
          help="Path to the output file (with .S extension)",
      )
      args = parser.parse_args()

      filename = args.file

      with open(filename, "w") as assembly_file:
          assembly_file.write("""
  .section .init
  .global _start
  .type   _start, @function
  _start:
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

      # initialize stack pointer
      la sp, _sp
      jal main
  # a0 exit code
  tohost_exit:
      li a0, 0
      slli a0, a0, 1
      ori a0, a0, 1
      la t0, tohost
      sw a0, 0(t0)
  1:  j 1b # wait for termination
  .section ".tohost","aw",@progbits
  .align 6
  .globl tohost
  tohost: .dword 0
  .align 6
  .globl fromhost
  fromhost: .dword 0
  """)


  if __name__ == "__main__":
      main()
''
