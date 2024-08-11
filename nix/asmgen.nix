{ pkgs }:
pkgs.writers.writePython3Bin "asmgen" { } ''
  import argparse
  from typing import List
  import random
  random.seed(42)  # Replace 42 with your desired seed


  class RiscVTestGenerator:
      def __init__(self, num_registers: int = 32) -> None:
          self.num_registers = num_registers
          self.onlyimm_instructions = ["li", "auipc", "lui"]
          self.immediate_instructions = ["addi", "xori", "ori", "andi",
                                         "slli", "srli", "srai",
                                         "slti", "sltiu"]
          self.shift_instructions = ["slli", "srli", "srai"]  # 32 bit imm
          self.register_instructions = ["add", "sub", "xor", "or", "and",
                                        "sll", "srl", "sra", "slt", "sltu",
                                        "mul", "mulh", "mulhu", "mulhsu",
                                        "div", "divu", "rem", "remu"]
          self.branch_instructions = ["beq"]

      def init_regs(self) -> List[str]:
          asm_code = []
          for i in range(7):
              asm_code.append(f"   li x0, {random.randint(0, 1023)}")
          for i in range(7):
              asm_code.append(f"   li x1, {random.randint(0, 1023)}")
          for i in range(32):
              asm_code.append(f"   li x{i}, {random.randint(0, 1023)}")
          return asm_code

      def generate_inst(self, inst_type: str) -> str:
          if inst_type in self.onlyimm_instructions:
              if inst_type == "li":
                  register = f"x{random.randint(0, self.num_registers - 1)}"
                  value = random.randint(0, 65535)  # 16-bit value for li
                  return f"li {register}, {value}"
              else:
                  register2 = f"x{random.randint(0, self.num_registers - 1)}"
                  immediate = random.randint(0, 1048575)
                  return f"{inst_type} {register2}, {immediate}"

          elif inst_type in self.register_instructions:
              register1 = f"x{random.randint(0, self.num_registers - 1)}"
              register2 = f"x{random.randint(0, self.num_registers - 1)}"
              register3 = f"x{random.randint(0, self.num_registers - 1)}"
              return f"{inst_type} {register3}, {register1}, {register2}"
          elif inst_type in self.immediate_instructions:
              return self.generate_immediate_inst(inst_type)
          elif inst_type in self.branch_instructions:
              return self.generate_beq_inst()
          else:
              raise ValueError(f"Unsupported inst type: {inst_type}")

      def generate_immediate_inst(self, inst_type: str) -> str:
          register1 = f"x{random.randint(0, self.num_registers - 1)}"
          register2 = f"x{random.randint(0, self.num_registers - 1)}"
          if inst_type in self.shift_instructions:
              immediate = random.randint(0, 31)
          else:
              immediate = random.randint(-2048, 2047)
          return f"{inst_type} {register2}, {register1}, {immediate}"

      def generate_beq_inst(self) -> str:
          reg1 = f"x{random.randint(0, self.num_registers - 1)}"
          reg2 = f"x{random.randint(0, self.num_registers - 1)}"
          imm1 = random.randint(-1000, 1000)
          imm2 = random.randint(-1000, 1000)
          equal = random.choice([True, False])
          if equal:
              imm2 = imm1

          test_number = random.randint(1, 9999)
          equal_label = f'equal_{test_number}_{random.randint(1000, 9999)}'
          done_label = f'done_{test_number}_{random.randint(1000, 9999)}'

          return f"""
                  li {reg1}, {imm1}
                  li {reg2}, {imm2}
                  beq {reg1}, {reg2}, {equal_label}
                  addi x31, x0, 1
                  j {done_label}
              {equal_label}:
                  addi x31, x0, 2
              {done_label}:
          """.strip()

      def generate_order_test(self, num_insts: int, inst_type: str) -> str:
          asm_code: List[str] = self.init_regs()
          c = 0
          if inst_type == "li":
              for _ in range(num_insts):
                  for i in range(self.num_registers):
                      asm_code.append(f"   li x{i}, {c}")
                      c += 1
          elif inst_type in self.register_instructions:
              for _ in range(num_insts):
                  for i in range(self.num_registers - 2):
                      asm_code.append(f"   {inst_type} x{i+2}, x{i}, x{i+1}")
          elif inst_type in self.shift_instructions:
              for _ in range(num_insts):
                  for i in range(self.num_registers - 1):
                      asm_code.append(f"   {inst_type} x{i+1}, x{i}, {c}")
                      c = (c + 1) % 32  # imm within 5 bits
          elif inst_type in self.immediate_instructions:
              for _ in range(num_insts):
                  for i in range(self.num_registers - 1):
                      asm_code.append(f"   {inst_type} x{i+1}, x{i}, {c}")
                      c = (c + 1) % 2048  # imm within 12-bit signed range
          elif inst_type in self.branch_instructions:
              return self.generate_beq_inst()
          else:
              raise ValueError(f"Unsupported inst type: {inst_type}")
          return "\n".join(asm_code)

      def generate_random_test(self, num_insts: int, inst_type: str) -> str:
          asm_code = self.init_regs()
          for _ in range(num_insts):
              inst = self.generate_inst(inst_type)
              asm_code.append(inst)
          return "\n".join(asm_code)


  if __name__ == "__main__":
      parser = argparse.ArgumentParser(description="RISC-V Test Generator")
      parser.add_argument(
          "--inst",
          type=str,
          choices=["addi", "xori", "ori", "andi", "slli", "srli", "srai",
                   "slti", "sltiu", "add", "sub", "xor", "or",
                   "and", "sll", "srl", "sra", "slt", "sltu",
                   "li", "lui", "auipc",
                   "beq",
                   "mul", "mulh", "mulhu", "mulhsu",
                   "div", "divu", "rem", "remu"],
          required=True,
          help="inst type to generate (li, add, sub, addi, xori, ori, or andi)",
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
