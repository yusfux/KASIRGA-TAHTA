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
