{ pkgs }:
pkgs.writers.writePython3Bin "spiketrace2json" { } ''
  # flake8: noqa
  import argparse
  import json
  import re
  from pathlib import Path


  def remove_after_loop(lines):
      """Removes all lines after encountering 'pc + 0x0' substring."""
      for i, line in enumerate(lines):
          if "pc + 0x0" in line:
              lines = lines[:i]
              # print(lines)
              break  # Exit the loop after finding the substring
      return lines


  def remove_lines_before_start_address(lines):
      """Removes all lines after encountering '0x80000000' substring."""
      for i, line in enumerate(lines):
          if "0: 0x80000000" in line:
              lines = lines[i:]  # Keep the substring line and following lines
              break  # Exit the loop after finding the substring
      return lines


  def split_list(lines):
      alias_list = []
      reg_list = []
      for line in lines:
          if "0: 3" in line:
              reg_list.append(line)
          else:
              alias_list.append(line)
      return alias_list, reg_list


  def remove_before_parenthesis_and_strip(lines):
      return [line[line.find(")") + 1 :].strip() for line in lines if line]


  def create_alias_numeric(lines):
      register_aliases = {
          "zero": "x0",
          "ra": "x1",
          "sp": "x2",
          "gp": "x3",
          "tp": "x4",
          "t0": "x5",
          "t1": "x6",
          "t2": "x7",
          "s0": "x8",
          "fp": "x8",
          "s1": "x9",
          "a0": "x10",
          "a1": "x11",
          "a2": "x12",
          "a3": "x13",
          "a4": "x14",
          "a5": "x15",
          "a6": "x16",
          "a7": "x17",
          "s2": "x18",
          "s3": "x19",
          "s4": "x20",
          "s5": "x21",
          "s6": "x22",
          "s7": "x23",
          "s8": "x24",
          "s9": "x25",
          "s10": "x26",
          "s11": "x27",
          "t3": "x28",
          "t4": "x29",
          "t5": "x30",
          "t6": "x31",
      }

      alias_numeric_list = []
      for line in lines:
          for alias, xnum in register_aliases.items():
              if alias in line:
                  line = line.replace(alias, xnum)
          alias_numeric_list.append(line)

      return alias_numeric_list


  def parse_reg_list(lines):
      lines = [line.replace("core   0: 3 ", "") for line in lines]
      pattern = re.compile(
          r"(0x[0-9a-fA-F]+) \((0x[0-9a-fA-F]+)\)(?: (x ?\d+ 0x[0-9a-fA-F]+|mem 0x[0-9a-fA-F]+ 0x[0-9a-fA-F]+))?"
      )
      # pattern = re.compile(
      #     r"(0x[0-9a-fA-F]+) \((0x[0-9a-fA-F]+)\)(?: (x ?\d+ 0x[0-9a-fA-F]+))?"
      # )
      result_list = []

      for line in lines:
          match = pattern.match(line)
          if match:
              pc, inst, result = match.groups()
              result_dict = {
                  "pc": pc,
                  "inst": inst,
                  "result": result if result else "",
              }
              result_list.append(result_dict)

      return result_list


  def add_alias(dict_list, aliases):
      if len(dict_list) != len(aliases):
          raise ValueError("Lengths does not match.")

      for i in range(len(dict_list)):
          dict_list[i]["alias"] = aliases[i]

      return dict_list


  def add_alias_numeric(dict_list, aliases):
      if len(dict_list) != len(aliases):
          raise ValueError("Lengths does not match.")

      for i in range(len(dict_list)):
          dict_list[i]["alias_numeric"] = aliases[i]

      return dict_list


  def parse_spike_trace(trace_file):
      with open(trace_file, "r") as f:
          lines = f.readlines()

      lines = remove_after_loop(lines)
      lines = remove_lines_before_start_address(lines)
      alias_list, reg_list = split_list(lines)
      reg_list = parse_reg_list(reg_list)

      alias_list = remove_before_parenthesis_and_strip(alias_list)
      numeric_alias_list = create_alias_numeric(alias_list)

      trace = add_alias(reg_list, alias_list)
      trace = add_alias_numeric(trace, numeric_alias_list)

      # for line in trace:
      #     print(line)

      return trace


  def main():
      parser = argparse.ArgumentParser()
      parser.add_argument(
          "-f",
          "--file",
          type=Path,
          required=True,
          help="Spike trace file.",
      )
      parser.add_argument(
          "-o",
          "--output_file",
          type=Path,
          required=True,
          help="Output json file.",
      )
      args = parser.parse_args()

      trace = parse_spike_trace(args.file)
      # with args.output_file.open("w") as output_file:
      #     json.dump(trace, output_file, indent=4)

      # Writing to file
      with args.output_file.open("w") as output_file:
          output_file.write("[\n")
          output_file.write(",\n".join([json.dumps(entry, separators=(",", ":")) for entry in trace]))
          output_file.write("]\n")


  if __name__ == "__main__":
      main()
''
