#!/usr/bin/env python3

import os
import subprocess
import sys
from pathlib import Path
from typing import List

import git

repo = git.Repo(".", search_parent_directories=True)
tests_dir = os.path.abspath(f"{repo.working_tree_dir}/src/test/python/cocotb/tests")
sys.path.append(tests_dir)

from test_template import TestTemplate  # noqa: E402


class Test(TestTemplate):
    def __init__(self, nWide: int):
        repo = git.Repo(".", search_parent_directories=True)
        repo.working_tree_dir

        self._project_dir = repo.working_tree_dir
        cwd = os.path.abspath(f"{self._project_dir}")

        self.test_gen_file_path = f"{cwd}/src/test/python/tools/gen_li.py"
        self.code_dir = f"{cwd}/src/test/c"
        self.build_dir = f"{cwd}/src/test/c/build"
        hex_file_path = f"{cwd}/src/test/c/build/main.hex"
        elf_file_path = f"{cwd}/src/test/c/build/main.elf"

        os.makedirs(self.build_dir, exist_ok=True)

        self.__prepare_test()

        self.spike_trace = self._get_spike_trace(Path(elf_file_path))
        self.insts = self._group_hex(Path(hex_file_path), nWide)

    def __prepare_test(self):
        try:
            code = subprocess.check_output(
                ["asmgen"], stderr=subprocess.STDOUT, text=True
            )
            with open(f"{self.code_dir}/src/test.S", "w") as f:
                f.write(code)

            subprocess.run(["make", "-C", f"{self.code_dir}"], check=True)
        except subprocess.CalledProcessError as e:
            print(f"Error executing command: {e.output}")
            exit(1)

    def instructions(self) -> List[List[str]]:
        return self.insts

    def trace(self) -> List[List[str]]:
        return self.spike_trace

    def timeout(self) -> int:
        return 100000


if __name__ == "__main__":
    test = Test(nWide=1)
    for lst in test.instructions():
        print(lst)
