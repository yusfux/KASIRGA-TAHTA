import argparse
import subprocess
from concurrent.futures import ThreadPoolExecutor

def run_test(op):
  output_file = f"runs/{op}.log"
  with open(output_file, "w") as f:
    command = f"make wood_asm_test 2 {op} random 500"
    result = subprocess.run(command, shell=True, stdout=subprocess.PIPE, text=True)
    # Write the stdout to the file
    f.write(f"Command: {command}\n")
    f.write(result.stdout)
    f.write("\n\n")

def main(thread_count):

  operations = ["addi", "xori", "ori", "andi", "slli", "srli", "srai",
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
                "clmulh", "clmulr"]

  with ThreadPoolExecutor(max_workers=thread_count) as executor:
        executor.map(run_test, operations)

if __name__ == "__main__":
  parser = argparse.ArgumentParser(description="Run random tests in parallel with a specified number of threads.")
  parser.add_argument("--thread_count", type=int, help="Number of threads")
  args = parser.parse_args()
  
  main(args.thread_count)