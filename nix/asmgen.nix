{ pkgs }:
pkgs.writers.writePython3Bin "asmgen" { } ''
  import argparse
  from typing import List
  import random
  random.seed(42)  # Replace 42 with your desired seed

  start_part = """
    .section .init
    .globl _start
    .type _start,@function

    _start:
    #.cfi_startproc
    #.cfi_undefined ra
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
  """

  end_part = """
    j tohost_exit # just terminate with exit code 0

    # a0 exit code
    tohost_exit:
      li a0, 0
      slli a0, a0, 1
      ori a0, a0, 1

      la t0, tohost
      sw a0, 0(t0)

      1: j 1b # wait for termination


    # .cfi_endproc

    .align 2
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

  """


  class RiscVTestGenerator:
      def __init__(self, num_registers: int = 32) -> None:
          self.COUNT = 0
          self.num_registers = num_registers
          self.onlyimm_instructions = ["li", "auipc", "lui"]
          self.immediate_instructions = ["addi", "xori", "ori", "andi",
                                         "slli", "srli", "srai", "slti", "sltiu",
                                         "rori", "bclri", "bexti", "binvi",
                                         "bseti"]
          self.onlyregister_instructions = ["clz", "cpop", "ctz", "orc.b",
                                            "rev8", "sext.b", "sext.h", "zext.h"]
          self.shift_instructions = ["slli", "srli", "srai", "rori", "bclri",
                                     "bexti", "binvi", "bseti"]
          self.register_instructions = ["add", "sub", "xor", "or", "and",
                                        "sll", "srl", "sra", "slt", "sltu",
                                        "mul", "mulh", "mulhu", "mulhsu",
                                        "div", "divu", "rem", "remu",
                                        "andn", "max", "maxu", "min", "minu",
                                        "orn", "rol", "ror", "xnor", "bclr",
                                        "bext", "binv", "bset", "clmul",
                                        "clmulh", "clmulr"]
          self.branch_instructions = ["beq", "bne", "bge", "bgeu", "blt", "bltu"]
          self.load_instructions = ["lw"]

      def init_regs(self) -> List[str]:
          asm_code = []
          for i in range(7):
              asm_code.append(f"   li x0, {random.randint(0, 1023)}")
          for i in range(7):
              asm_code.append(f"   li x1, {random.randint(0, 1023)}")
          for i in range(32):
              asm_code.append(f"   li x{i}, {random.randint(0, 1023)}")
          return asm_code

      def generate_inst(self, inst_type: str, num_data: int) -> str:
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
          elif inst_type in self.onlyregister_instructions:
              register1 = f"x{random.randint(0, self.num_registers - 1)}"
              register2 = f"x{random.randint(0, self.num_registers - 1)}"
              return f"{inst_type} {register2}, {register1}"
          elif inst_type in self.load_instructions:
              return self.generate_load_inst(inst_type, num_data)
          else:
              raise ValueError(f"Unsupported inst type: {inst_type}")

      def generate_data_section(self, num_data: int) -> str:
          data_section = [".data"]
          for i in range(num_data):
              data = f"tdat{i+1}: .word 0x{random.randint(0, (2**32)-1):08x}"
              data_section.append(data)
          return "\n".join(data_section)

      def generate_load_inst(self, inst_type: str, num_data) -> str:
          if inst_type in self.load_instructions:
              registers = [f"x{i}" for i in range(1, 32)]
              load_test = [""]
              reg1 = random.choice(registers)
              reg2 = random.choice(registers)
              data_index = random.randint(1, num_data)
              load_test.append(f"la {reg1}, tdat{data_index}")
              load_test.append(f"lw {reg2}, 0({reg1})")
              return "\n".join(load_test)
          else:
              raise 1

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

          equal_index = random.randint(1000, 9999)+self.COUNT
          equal_label = f'equal_{test_number}_{equal_index}'
          self.COUNT += 1

          done_index = random.randint(1000, 9999)+self.COUNT
          done_label = f'done_{test_number}_{done_index}'
          self.COUNT += 1

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

      def generate_order_test(
          self, num_insts: int, inst_type: str, num_data: int
      ) -> str:
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

      def generate_random_test(
       self, num_insts: int, inst_type: str, num_data: int
      ) -> str:
          asm_code = self.init_regs()
          for _ in range(num_insts):
              inst = self.generate_inst(inst_type, num_data)
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
                   "beq", "bne", "bge", "bgeu", "blt", "bltu",
                   "mul", "mulh", "mulhu", "mulhsu",
                   "div", "divu", "rem", "remu",
                   "clz", "cpop", "ctz", "orc.b", "sext.b",
                   "sext.h", "rev8", "rori", "zext.h",
                   "bclri", "bexti", "binvi", "bseti",
                   "andn", "max", "maxu", "min", "minu",
                   "orn", "rol", "ror", "xnor", "bclr",
                   "bext", "binv", "bset", "clmul",
                   "lw",
                   "clmulh", "clmulr"],
          required=True,
          help="inst type to generate (li, add, sub, addi, xori, ori, or andi)",
      )
      parser.add_argument(
          "--num-insts", type=int, default=32, help="Num of insts to generate"
      )
      parser.add_argument(
          "--num-data", type=int, default=256, help="Num of data in data region"
      )

      parser.add_argument(
          "--out_asm_file",
          type=str,
          required=True,
          default="src/test/c/src/start.S",
          help="Output path for the assembly code"
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
          test_asm = test_gen.generate_random_test(
              args.num_insts, args.inst, args.num_data
          )
      elif args.type == "order":
          test_asm = test_gen.generate_order_test(
              args.num_insts, args.inst, args.num_data
          )
      else:
          raise ValueError(f"Unsupported test type: {args.type}")

      data_section = test_gen.generate_data_section(args.num_data)
      with open(args.out_asm_file, 'w') as out_file:
          out_file.write(start_part)
          out_file.write(f"{args.inst}_{args.type}_test:\n")
          out_file.write(test_asm)
          out_file.write(end_part)
          out_file.write(data_section)

      # with open(args.data_asm_file, 'w') as data_file:
''
