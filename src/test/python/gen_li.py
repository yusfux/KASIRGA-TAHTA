#!/usr/bin/env python3

import random
from typing import List


class RiscVTestGenerator:
    def __init__(self, num_registers: int = 32) -> None:
        self.num_registers = num_registers

    def generate_random_test(self, num_instructions: int = 32) -> str:
        """Generates random 'li' instructions."""
        asm_code: List[str] = []
        for _ in range(num_instructions):
            reg = random.randint(0, self.num_registers - 1)
            immediate = random.randint(
                0, 0xFFFFFFFF
            )  # Assuming 32-bit immediate values
            asm_code.append(f"    li x{reg}, {immediate}")
        return "\n".join(asm_code)

    def generate_order_test(self, num_instructions: int = 32) -> str:
        """Generates 'li' instructions with consecutive integers."""
        asm_code: List[str] = []
        c = 0
        for _ in range(num_instructions):
            for i in range(self.num_registers):
                asm_code.append(f"    li x{i}, {c}")
                c = c + 1
        return "\n".join(asm_code)

    def generate_smoke_test(self, num_instructions: int = 32) -> str:
        """Generates 'li' instructions with immediate values equal to register indices."""
        asm_code: List[str] = []
        for j in range(num_instructions):
            for i in range(self.num_registers):
                asm_code.append(f"    li x{i}, {j}")
        return "\n".join(asm_code)


if __name__ == "__main__":
    num_random_inst = 100

    test_generator = RiscVTestGenerator()
    smoke_test_asm = test_generator.generate_smoke_test()
    order_test_asm = test_generator.generate_order_test()
    random_test_asm = test_generator.generate_random_test(num_random_inst)

    print("\n.globl _start")
    print("\n_start:")

    # print("\nsmoketest:")
    # print(smoke_test_asm)
    print("\nordertest:")
    print(order_test_asm)
    # print("\nrandomtest:")
    # print(random_test_asm)
