{ pkgs }:
pkgs.writers.writePython3Bin "asmgen" { } ''
  import argparse
  from typing import List
  import random


  class RiscVTestGenerator:
      def __init__(self, num_registers: int = 32) -> None:
          self.num_registers = num_registers

      def generate_inst(self, inst_type: str) -> str:
          if inst_type == "li":
              register = f"x{random.randint(0, self.num_registers - 1)}"
              value = random.randint(0, 65535)  # 16-bit value for li
              return f"li {register}, {value}"
          elif inst_type == "add":
              register1 = f"x{random.randint(0, self.num_registers - 1)}"
              register2 = f"x{random.randint(0, self.num_registers - 1)}"
              register3 = f"x{random.randint(0, self.num_registers - 1)}"
              return f"add {register3}, {register1}, {register2}"
          elif inst_type == "addi":
              register1 = f"x{random.randint(0, self.num_registers - 1)}"
              register2 = f"x{random.randint(0, self.num_registers - 1)}"
              immediate = random.randint(-2048, 2047)  # 12-bit sign imm
              return f"addi {register2}, {register1}, {immediate}"
          else:
              raise ValueError(f"Unsupported inst type: {inst_type}")

      def generate_order_test(self, num_insts: int, inst_type: str) -> str:
          asm_code: List[str] = []
          c = 0
          if inst_type == "li":
              for _ in range(num_insts):
                  for i in range(self.num_registers):
                      asm_code.append(f"   li x{i}, {c}")
                      c += 1
          elif inst_type == "add":
              for _ in range(num_insts):
                  for i in range(self.num_registers - 2):
                      asm_code.append(f"   add x{i+2}, x{i}, x{i+1}")
          elif inst_type == "addi":
              for _ in range(num_insts):
                  for i in range(self.num_registers - 1):
                      asm_code.append(f"   addi x{i+1}, x{i}, {c}")
                      c = (c + 1) % 2048  # imm within 12-bit signed range
          else:
              raise ValueError(f"Unsupported inst type: {inst_type}")
          return "\n".join(asm_code)

      def generate_random_test(self, num_insts: int, inst_type: str) -> str:
          asm_code = []
          for _ in range(num_insts):
              inst = self.generate_inst(inst_type)
              asm_code.append(inst)
          return "\n".join(asm_code)


  if __name__ == "__main__":
      parser = argparse.ArgumentParser(description="RISC-V Test Generator")
      parser.add_argument(
          "--inst",
          type=str,
          choices=["li", "add", "addi"],
          required=True,
          help="inst type to generate (li, add, or addi)",
      )
      parser.add_argument(
          "--num-insts", type=int, default=32, help="Num of insts to generate"
      )
      parser.add_argument(
          "--type",
          type=str,
          choices=["random", "order"],
          default="random",
          help="Test type (random or order)",
      )
      args = parser.parse_args()

      test_gen = RiscVTestGenerator()
      if args.type == "random":
          test_asm = test_gen.generate_random_test(args.num_insts, args.inst)
      elif args.type == "order":
          test_asm = test_gen.generate_order_test(args.num_insts, args.inst)
      else:
          raise ValueError(f"Unsupported test type: {args.type}")

      print(f"\n{args.inst}_{args.type}_test:")
      print(test_asm)
''
