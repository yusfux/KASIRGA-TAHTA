import json
import os
from decimal import Decimal
from enum import Enum
from pathlib import Path
from typing import Any, List, Tuple

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, RisingEdge, Timer
from cocotb.utils import get_sim_time

repo = git.Repo(".", search_parent_directories=True)
project_dir = repo.working_tree_dir
cwd = os.path.abspath(f"{project_dir}")
build_dir = f"{cwd}/src/test/c/build"

"""
tb_wood is a generic N wide testbench
    WOOD_NWIDE            = Wood nWide configuration
    WOOD_INSTRUCTION_PATH = nWide hex files as list
    WOOD_SPIKE_TRACE_PATH = spike.trace file path as str
"""
CLOCK_PERIOD = 10
TIME_UNIT = "ns"
TIMEOUT = 800  # watchdog timer, resets itself
WOOD_NWIDE = 2
WOOD_INSTRUCTION_PATH = []
for n in range(0, WOOD_NWIDE):
    WOOD_INSTRUCTION_PATH.append(Path(f"{build_dir}/main{n}.hex"))
print(WOOD_INSTRUCTION_PATH)

WOOD_SPIKE_TRACE_PATH = Path(f"{build_dir}/spike_trace.json")

timeout = Event(name="timeout")
start = Event(name="start")
timeout.clear()
start.clear()


class Color(Enum):
    BLACK = "30"
    RED = "31"
    GREEN = "32"
    YELLOW = "33"
    BLUE = "34"
    MAGENTA = "35"
    CYAN = "36"
    WHITE = "37"


def color(arg: Any, color: Color) -> str:
    text = str(arg)
    return f"\033[{color.value}m{text}\033[0m"


@cocotb.coroutine
async def watchdog_timer():
    timer = 0
    while timer < TIMEOUT:
        if timeout.is_set():
            timer = 0
            timeout.clear()
        timer = timer + 1
        await Timer(Decimal("1"), units=TIME_UNIT)
    assert 0, color("TIMEOUT! DUT halted.", Color.RED)


async def get_spike_trace():
    with open(WOOD_SPIKE_TRACE_PATH, "r") as f:
        spike_trace = json.load(f)

    return spike_trace


@cocotb.coroutine
async def diff_traces(dut):
    spike_trace = await get_spike_trace()

    start.wait()
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    # previous_pc = 69
    while True:
        in_valid = [0 for _ in range(WOOD_NWIDE)]
        in_ready = [0 for _ in range(WOOD_NWIDE)]
        in_wrf = [0 for _ in range(WOOD_NWIDE)]

        arfBus_adr = [0 for _ in range(WOOD_NWIDE)]
        arfBus_tag = [0 for _ in range(WOOD_NWIDE)]
        arfBus_valid = [0 for _ in range(WOOD_NWIDE)]
        commBus_tag = [0 for _ in range(WOOD_NWIDE)]
        commBus_valid = [0 for _ in range(WOOD_NWIDE)]
        inst = [0 for _ in range(WOOD_NWIDE)]
        pc = [0 for _ in range(WOOD_NWIDE)]
        rd_data = [0 for _ in range(WOOD_NWIDE)]

        retired = [0 for _ in range(WOOD_NWIDE)]
        for n in range(0, WOOD_NWIDE):
            arfBus_adr[n] = getattr(
                dut, f"exunit.rwstage.io_arfBus_{n}_bits_rd"
            ).value.integer
            arfBus_tag[n] = getattr(
                dut, f"exunit.rwstage.io_arfBus_{n}_bits_tag"
            ).value.integer
            arfBus_valid[n] = getattr(
                dut, f"exunit.rwstage.io_arfBus_{n}_valid"
            ).value.integer

            commBus_valid[n] = getattr(
                dut, f"exunit.rwstage.io_commitedBus_{n}_valid"
            ).value.integer
            commBus_tag[n] = getattr(
                dut, f"exunit.rwstage.io_commitedBus_{n}_bits_tag"
            ).value.integer

            inst[n] = getattr(dut, f"exunit.rwstage.io_in_{n}_bits_inst").value.integer
            pc[n] = getattr(dut, f"exunit.rwstage.io_in_{n}_bits_pcIdx").value.integer
            rd_data[n] = getattr(
                dut, f"exunit.rrstage.prf_{arfBus_tag[n]}"
            ).value.integer
            in_valid[n] = getattr(dut, f"exunit.rwstage.io_in_{n}_valid").value.integer
            in_ready[n] = getattr(dut, f"exunit.rwstage.io_in_{n}_ready").value.integer
            in_wrf[n] = getattr(
                dut, f"exunit.rwstage.io_in_{n}_bits_writeRf"
            ).value.integer
            retired[n] = (in_wrf[n] or (arfBus_adr[n] == 0)) and (
                in_ready[n] and in_valid[n]
            )

        golden_reference = [{} for _ in range(WOOD_NWIDE)]
        inst_p = ["" for _ in range(WOOD_NWIDE)]
        pc_p = ["" for _ in range(WOOD_NWIDE)]
        rd_data_p = ["" for _ in range(WOOD_NWIDE)]
        # if previous_pc != pc:
        # previous_pc = pc

        for n in range(0, WOOD_NWIDE):
            if retired[n]:
                golden_reference[n] = spike_trace.pop(0)
                print(color(golden_reference[n], Color.YELLOW))

                inst_p[n] = "{0:#0{1}x}".format(inst[n], 10)
                pc_p[n] = "{0:#0{1}x}".format(pc[n], 10)
                rd_data_p[n] = "{0:#0{1}x}".format(rd_data[n], 10)
                inst_p[n] = f"{inst_p[n]}".strip()
                rd_data_p[n] = f"x{arfBus_adr[n]:>2} {rd_data_p[n]}"

                print(
                    f"{{'pc': '{pc_p[n]}', 'inst': '{inst_p[n]}','result': '{rd_data_p[n]}', 'time': {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}, 'n': {n}}}"
                )

                assert (
                    inst_p[n] == golden_reference[n]["inst"]
                ), f"Instruction is {color(inst_p[n], Color.GREEN)} but it should be {color(golden_reference[n]['inst'], Color.YELLOW)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

                golden_result = golden_reference[n]["result"]
                if not golden_result:
                    golden_result = "x 0 0x00000000"
                if "x 0" in rd_data_p[n]:
                    rd_data_p[n] = "x 0 0x00000000"
                assert (
                    rd_data_p[n] == golden_result
                ), f"Result is {color(rd_data_p[n], Color.GREEN)} at tag {color(arfBus_tag[n], Color.GREEN)} but it should be {color(golden_reference[n]['result'], Color.YELLOW)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

        for n in range(0, WOOD_NWIDE):
            if retired[n]:
                if arfBus_valid[n] and commBus_valid[n]:
                    assert (
                        arfBus_tag[n] != commBus_tag[n]
                    ), f"Tag duplication! tag_{n} {color(arfBus_tag[n], Color.GREEN)} is also commited at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

                timeout.set()
        await RisingEdge(dut.clock)


async def group_pc(hex_lines: List[str], num_groups: int) -> List[List[str]]:
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


@cocotb.coroutine
async def decode_driver(dut):
    insts: list[list[str]] = [[] for _ in range(WOOD_NWIDE)]
    pcs: list[list[str]] = [[] for _ in range(WOOD_NWIDE)]
    for idx, inst_hex_file in enumerate(WOOD_INSTRUCTION_PATH):
        with open(inst_hex_file, "r") as f:
            raw_lines = f.readlines()
            insts[idx] = [line.strip() for line in raw_lines]
            print(f"[INFO] Reading: {inst_hex_file}")

    all_insts = [item.strip() for sublist in insts for item in sublist]
    all_pcs = []
    base_address = 0x80000000
    for i, _ in enumerate(all_insts):
        pc_address = base_address + 4 * i
        all_pcs.append(f"{pc_address:0>8X}")

    pcs = await group_pc(all_pcs, WOOD_NWIDE)

    pc_and_insts: list[list[Tuple[str, str]]] = [[] for _ in range(WOOD_NWIDE)]
    for idx_n, (pc_n, inst_n) in enumerate(zip(pcs, insts)):
        for pc, inst in zip(pc_n, inst_n):
            pc_and_insts[idx_n].append((pc, inst))
    print(pc_and_insts)

    for idx, pc_and_inst_n in enumerate(pc_and_insts):
        if not pc_and_inst_n:
            assert 0, f"No instructions found in WOOD_INSTRUCTION_PATH for n={idx}: {WOOD_INSTRUCTION_PATH}"

    in_valid = []
    in_ready = []
    in_bits = []
    pc_idx = getattr(dut, "io_pcIdx_bits")
    pc_idx_valid = getattr(dut, "io_pcIdx_valid")
    for n in range(0, WOOD_NWIDE):
        in_valid.append(getattr(dut, f"io_in_{n}_valid"))
        in_bits.append(getattr(dut, f"io_in_{n}_bits"))
        in_ready.append(getattr(dut, f"io_in_{n}_ready"))

    pc_idx.value = 0
    pc_idx_valid.value = 0
    for n in range(0, WOOD_NWIDE):
        in_valid[n].value = 0
        in_bits[n].value = 0

    pi_n = [("", "") for _ in range(WOOD_NWIDE)]
    for n in range(0, WOOD_NWIDE):
        (p, i) = pc_and_insts[n].pop(0)
        pi_n[n] = (p, i)

    pc_idx_valid.value = 1
    (p, i) = pi_n[0]
    pc_idx.value = int(p, 16)
    for n in range(0, WOOD_NWIDE):
        in_valid[n].value = 1
        (p, i) = pi_n[n]
        in_bits[n].value = int(i, 16)

    dut.reset.value = 0  # START
    start.set()

    while True:
        (p, i) = pi_n[0]
        pc_idx_valid.value = 1
        pc_idx.value = int(p, 16)
        for n in range(0, WOOD_NWIDE):
            in_valid[n].value = 1
            (p, i) = pi_n[n]
            in_bits[n].value = int(i, 16)

            if not i:
                break

        pc_idx_valid.value = 1

        await RisingEdge(dut.clock)

        try:
            all_valid = 0
            for n in range(0, WOOD_NWIDE):
                all_valid = all_valid | in_ready[n].value.integer

            if all_valid:
                for n in range(0, WOOD_NWIDE):
                    (p, i) = pc_and_insts[n].pop(0)
                    pi_n[n] = (p, i)
            else:
                pass
        except Exception as e:
            assert 0, e


@cocotb.test()
async def test_wood(dut):
    await cocotb.start(
        Clock(dut.clock, CLOCK_PERIOD, TIME_UNIT).start(start_high=False)
    )
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    cocotb.start_soon(watchdog_timer())
    cocotb.start_soon(decode_driver(dut))
    await cocotb.start_soon(diff_traces(dut))
