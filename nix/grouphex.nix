{ pkgs }:
pkgs.writers.writePython3Bin "grouphex" { } ''
  from pathlib import Path
  from typing import List
  import argparse


  def group_hex(hex_file: Path, num_groups: int) -> List[List[str]]:
      with open(hex_file, "r") as source:
          hex_lines = source.readlines()
      hex_lines.reverse()

      target_size = (len(hex_lines) + num_groups - 1) // num_groups
      grouped_lines = [[] for _ in range(num_groups)]

      for index, line in enumerate(hex_lines):
          grouped_lines[index % num_groups].append(line.strip())

      # Pad short lists with zeros (if needed)
      for i in range(num_groups):
          grouped_lines[i].reverse()
          group = grouped_lines[i]
          if len(group) < target_size:
              group.extend(
                  ["0" * (target_size - len(group))] * (target_size - len(group))
              )

      return grouped_lines


  def main():
      parser = argparse.ArgumentParser()
      parser.add_argument(
          "-f",
          "--file",
          type=Path,
          required=True,
          help="Hex file.",
      )
      parser.add_argument(
          "-o",
          "--output_dir",
          type=Path,
          required=True,
          help="Output file paths will be {output_dir}/main{group_index}.hex.",
      )
      parser.add_argument(
          "-g",
          "--groups",
          type=int,
          required=True,
          help="Number of groups.",
      )
      args = parser.parse_args()

      lists = group_hex(args.file, args.groups)

      if not (args.groups % 2) == 0:
          lists.reverse()

      for idx, lst in enumerate(lists):
          with open(f"{args.output_dir}/main{idx}.hex", "w") as f:
              for line in lst:
                  f.write(line + "\n")


  if __name__ == "__main__":
      main()
''
