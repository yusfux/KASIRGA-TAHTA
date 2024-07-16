{ pkgs }:
pkgs.writers.writePython3Bin "dump2gtkw" { } ''
  import sys


  def parse_disassembly(file_name):
      with open(file_name, "r") as file:
          disassembly_code = file.read()
      # Initialize an empty dictionary to store the results
      parsed_instructions = {}

      # Split the disassembly code into lines
      lines = disassembly_code.strip().split("\n")

      # Process each line
      for line in lines:
          if line:
              # Split the line into parts
              parts = line.split()
              if len(parts) >= 3:
                  hex_code = parts[1]
                  instruction = " ".join(parts[2:])
                  # Add the hex code and instruction to the dictionary
                  parsed_instructions[hex_code] = instruction

      return parsed_instructions


  def main():
      """
      Create a gtkwave translate filter file from an objdump
      """
      parsed_instructions = parse_disassembly(sys.argv[1])
      for key, value in parsed_instructions.items():
          print(f"{key} {value}\n", end="")


  if __name__ == "__main__":
      main()
''
