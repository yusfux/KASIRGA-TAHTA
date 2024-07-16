import subprocess
from abc import ABC, abstractmethod
from pathlib import Path
from typing import List


class TestTemplate(ABC):
    """
    Abstract class for compiling code tests
    """

    @abstractmethod
    def instructions(self) -> List[List[str]]:
        """
        Performs any setup tasks before compilation.
        """
        pass

    @abstractmethod
    def trace(self) -> List[List[str]]:
        """
        Returns spike trace.
        """
        pass

    @abstractmethod
    def timeout(self) -> int:
        """
        Max timeout duration for the test.
        """
        pass

    def _group_hex(self, hex_file: Path, num_groups: int) -> List[List[str]]:
        with open(hex_file, "r") as source:
            hex_lines = source.readlines()
        hex_lines.reverse()

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

    def _get_spike_trace(self, elf_file: Path) -> List[List[str]]:
        """
        Get Spike Trace
        """

        spike_cmd = [
            "spike",
            "-m0x80000000:0x200000",
            "--log-commits",
            "--isa=rv32gc",
            "-l",
            str(elf_file),
        ]

        with subprocess.Popen(
            spike_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
        ) as process:
            trace_lines = [
                line.decode().strip().split() for line in process.stdout.readlines()
            ]

        return trace_lines
