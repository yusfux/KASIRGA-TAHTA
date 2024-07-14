#!/usr/bin/env python3

import os
import subprocess
from typing import List

import git


import sys

repo = git.Repo(".", search_parent_directories=True)
tests_dir = os.path.abspath(f"{repo.working_tree_dir}/verify/tests")
sys.path.append(tests_dir)

from test_template import TestTemplate  # noqa: E402


class Test(TestTemplate):
    def __init__(self, nWide: int):
        repo = git.Repo(".", search_parent_directories=True)
        repo.working_tree_dir

        self._project_dir = repo.working_tree_dir
        self.root_dir = os.path.abspath(f"{self._project_dir}")
        self.src_dir = f"{self.root_dir}/src"
        self.test_gen_file_path = f"{self.src_dir}/test/python/gen_li.py"
        self.code_dir = f"{self.src_dir}/test/c"
        self.build_dir = f"{self.code_dir}/build"
        hex_file_path = f"{self.build_dir}/main.hex"

        os.makedirs(self.build_dir, exist_ok=True)

        self._generate_test_code()
        self._build_test_code()
        hex_lines = self._read_hex_file_to_list(hex_file_path)
        self.insts = self._group_hex_lines(hex_lines, nWide)

    def _generate_test_code(self):
        subprocess.run(["make", "clean", "-C", f"{self.code_dir}"], check=True)
        subprocess.run(
            [
                "sh",
                "-c",
                f"python {self.test_gen_file_path} > {self.code_dir}/src/main.S",
            ],
            check=True,
        )

    def _build_test_code(self):
        subprocess.run(["make", "-C", f"{self.code_dir}"], check=True)

    def _read_hex_file_to_list(self, file_path: str) -> List[str]:
        with open(file_path, "r") as source:
            lines = source.readlines()
        lines.reverse()
        return lines

    def _group_hex_lines(
        self, hex_lines: List[str], num_groups: int
    ) -> List[List[str]]:
        target_size = (len(hex_lines) + num_groups - 1) // num_groups
        grouped_lines = [[] for _ in range(num_groups)]

        for index, line in enumerate(hex_lines):
            grouped_lines[index % num_groups].append(line.strip())

        # Pad short lists with zeros (if needed)
        for i in range(num_groups):
            group = grouped_lines[i]
            if len(group) < target_size:
                group.extend(
                    ["0" * (target_size - len(group))] * (target_size - len(group))
                )

        return grouped_lines

    def instructions(self) -> List[List[str]]:
        return self.insts

    def pass_adr(self) -> int:
        return 0x99999999

    def fail_adr(self) -> int:
        return 0x99999999

    def timeout(self) -> int:
        return 10000


if __name__ == "__main__":
    test = Test(nWide=1)
    for lst in test.instructions():
        print(lst)
