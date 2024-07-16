{ pkgs }:
pkgs.writers.writePython3Bin "asmgen" { } ''
  from typing import List


  class RiscVTestGenerator:
      def __init__(self, num_registers: int = 32) -> None:
          self.num_registers = num_registers

      def generate_order_test(self, num_instructions: int = 32) -> str:
          """Generates 'li' instructions with consecutive integers."""
          asm_code: List[str] = []
          c = 0
          for _ in range(num_instructions):
              for i in range(self.num_registers):
                  asm_code.append(f"    li x{i}, {c}")
                  c = c + 1
          return "\n".join(asm_code)


  if __name__ == "__main__":
      num_instruction_groups = 32

      test_generator = RiscVTestGenerator()
      order_test_asm = test_generator.generate_order_test(num_instruction_groups)

      print("\nordertest:")
      print(order_test_asm)
''
