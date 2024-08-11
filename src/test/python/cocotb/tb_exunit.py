import json
import os
from decimal import Decimal
from enum import Enum
from pathlib import Path
from typing import Any, List

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, RisingEdge, FallingEdge, Timer
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
WOOD_INSTRUCTION_PATH = Path(f"{build_dir}/main.hex")
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
async def flist_monitor(dut):
    start.wait()
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    flist = {}

    while True:
        in_valid = [0 for _ in range(WOOD_NWIDE)]
        in_ready = [0 for _ in range(WOOD_NWIDE)]
        in_tag = [0 for _ in range(WOOD_NWIDE)]
        for n in range(0, WOOD_NWIDE):
            in_valid[n] = getattr(dut, f"mistage.flist.io_in_{n}_valid").value.integer
            in_ready[n] = getattr(dut, f"mistage.flist.io_in_{n}_ready").value.integer
            in_tag[n] = getattr(dut, f"mistage.flist.io_in_{n}_bits_tag").value.integer

        for n in range(0, WOOD_NWIDE):
            if in_ready[n] & in_valid[n]:
                if in_tag[n] in flist:
                    assert 0, f"Flist tag inserted twice! tag_{n} {color(in_tag[n], Color.GREEN)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"
                else:
                    flist[in_tag[n]] = in_tag[n]

        out_valid = [0 for _ in range(WOOD_NWIDE)]
        out_ready = [0 for _ in range(WOOD_NWIDE)]
        out_tag = [0 for _ in range(WOOD_NWIDE)]
        for n in range(0, WOOD_NWIDE):
            out_valid[n] = getattr(dut, f"mistage.flist.io_out_{n}_valid").value.integer
            out_ready[n] = getattr(dut, f"mistage.flist.io_out_{n}_ready").value.integer
            out_tag[n] = getattr(
                dut, f"mistage.flist.io_out_{n}_bits_tag"
            ).value.integer

        for n in range(0, WOOD_NWIDE):
            if out_ready[n] & out_valid[n]:
                if out_tag[n] in flist:
                    del flist[out_tag[n]]

        await RisingEdge(dut.clock)


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
        flushed = [0 for _ in range(WOOD_NWIDE)]
        for n in range(0, WOOD_NWIDE):
            arfBus_adr[n] = getattr(dut, f"rwstage.io_arfBus_{n}_bits_rd").value.integer
            arfBus_tag[n] = getattr(
                dut, f"rwstage.io_arfBus_{n}_bits_tag"
            ).value.integer
            arfBus_valid[n] = getattr(dut, f"rwstage.io_arfBus_{n}_valid").value.integer

            commBus_valid[n] = getattr(
                dut, f"rwstage.io_commitedBus_{n}_valid"
            ).value.integer
            commBus_tag[n] = getattr(
                dut, f"rwstage.io_commitedBus_{n}_bits_tag"
            ).value.integer

            inst[n] = getattr(dut, f"rwstage.io_in_{n}_bits_inst").value.integer
            pc[n] = getattr(dut, f"rwstage.io_in_{n}_bits_pc").value.integer
            rd_data[n] = getattr(dut, f"rrstage.prf_{arfBus_tag[n]}").value.integer
            in_valid[n] = getattr(dut, f"rwstage.io_in_{n}_valid").value.integer
            in_ready[n] = getattr(dut, f"rwstage.io_in_{n}_ready").value.integer
            in_wrf[n] = getattr(dut, f"rwstage.io_in_{n}_bits_writeRf").value.integer
            flushed[n] = getattr(dut, f"rwstage.io_in_{n}_bits_flushed").value.integer
            retired[n] = in_ready[n] and in_valid[n]

        golden_reference = [{} for _ in range(WOOD_NWIDE)]
        inst_p = ["" for _ in range(WOOD_NWIDE)]
        pc_p = ["" for _ in range(WOOD_NWIDE)]
        rd_data_p = ["" for _ in range(WOOD_NWIDE)]
        # if previous_pc != pc:
        # previous_pc = pc

        for n in range(0, WOOD_NWIDE):
            if retired[n] and not flushed[n]:
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
                    pc_p[n] == golden_reference[n]["pc"]
                ), f"PC is {color(pc_p[n], Color.GREEN)} but it should be {color(golden_reference[n]['pc'], Color.YELLOW)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

                assert (
                    inst_p[n] == golden_reference[n]["inst"]
                ), f"Instruction is {color(inst_p[n], Color.GREEN)} but it should be {color(golden_reference[n]['inst'], Color.YELLOW)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

                golden_result = golden_reference[n]["result"]
                if not golden_result:
                    golden_result = "x 0 0x00000000"
                if "x 0" in rd_data_p[n]:
                    rd_data_p[n] = "x 0 0x00000000"

                if not (in_wrf[n] or (arfBus_adr[n] == 0)):
                    rd_data_p[n] = "x 0 0x00000000"  # branch

                assert (
                    rd_data_p[n] == golden_result
                ), f"Result is {color(rd_data_p[n], Color.GREEN)} at tag {color(arfBus_tag[n], Color.GREEN)} but it should be {color(golden_reference[n]['result'], Color.YELLOW)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

        for n in range(0, WOOD_NWIDE):
            if retired[n] and not flushed[n]:
                if arfBus_valid[n] and commBus_valid[n]:
                    assert (
                        arfBus_tag[n] != commBus_tag[n]
                    ), f"Tag duplicated! tag_{n} {color(arfBus_tag[n], Color.GREEN)} is also commited at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"
                if not arfBus_valid[n] and not commBus_valid[n]:
                    assert 0, f"Tag lost! tag_{n} {color(arfBus_tag[n], Color.GREEN)}, pc: {pc_p[n]}, {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

                timeout.set()

        # Check for cloned tags in commBus
        valid_commBus_tags = [
            tag for tag, valid in zip(commBus_tag, commBus_valid) if valid
        ]
        commBus_clones = [
            tag for tag in set(valid_commBus_tags) if valid_commBus_tags.count(tag) > 1
        ]
        if commBus_clones:
            assert 0, f"Tag(s) cloned in commBus: {color(', '.join(map(str, commBus_clones)), Color.GREEN)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

        # Check for cloned tags in arfBus
        valid_arfBus_tags = [
            tag for tag, valid in zip(arfBus_tag, arfBus_valid) if valid
        ]
        arfBus_clones = [
            tag for tag in set(valid_arfBus_tags) if valid_arfBus_tags.count(tag) > 1
        ]
        if arfBus_clones:
            assert 0, f"Tag(s) cloned in arfBus: {color(', '.join(map(str, arfBus_clones)), Color.GREEN)} at {get_sim_time(units=TIME_UNIT)}{TIME_UNIT}"

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
    grouped_lines.reverse()
    return grouped_lines


@cocotb.coroutine
async def decode_driver(dut):
    insts: list[str] = []
    with open(WOOD_INSTRUCTION_PATH, "r") as f:
        raw_lines = f.readlines()
        insts = [line.strip() for line in raw_lines]
        print(f"[INFO] Reading: {insts}")

    insts.append("0" * 1024)

    virtual_pc = 0  # raw, requires (* 4) + base
    base_address = 0x80000000

    # bp_pc = getattr(dut, "rsstage.io_bpBus_bits_pc")
    bp_taken = getattr(dut, "rsstage.io_bpBus_bits_taken")
    bp_exception = getattr(dut, "rsstage.io_bpBus_bits_exception")
    bp_targetPC = getattr(dut, "rsstage.io_bpBus_bits_targetPC")
    bp_valid = getattr(dut, "rsstage.io_bpBus_valid")

    in_valid = []
    in_ready = []
    in_inst = []
    in_pc = []
    for n in range(0, WOOD_NWIDE):
        in_valid.append(getattr(dut, f"io_in_{n}_valid"))
        in_inst.append(getattr(dut, f"io_in_{n}_bits_inst"))
        in_pc.append(getattr(dut, f"io_in_{n}_bits_pc"))
        in_ready.append(getattr(dut, f"io_in_{n}_ready"))

    dut.reset.value = 0  # START
    start.set()

    for n in range(0, WOOD_NWIDE):
        in_inst[n].value = int(insts[virtual_pc + n], 16)
        in_pc[n].value = base_address + (4 * (virtual_pc + n))
        in_valid[n].value = 1

    virtual_pc += WOOD_NWIDE

    await RisingEdge(dut.clock)

    while True:
        for n in range(0, WOOD_NWIDE):
            in_inst[n].value = int(insts[virtual_pc + n], 16)
            in_pc[n].value = base_address + (4 * (virtual_pc + n))
            in_valid[n].value = 1
            # all_ready += in_ready[n].value.integer

        await FallingEdge(dut.clock)

        if (
            bp_taken.value.integer | bp_exception.value.integer
        ) & bp_valid.value.integer:
            real_pc = bp_targetPC.value.integer
            virtual_pc = (real_pc - base_address) // 4
            print("JUMP!", f"real_pc: {real_pc:0>8X} ", f"virtual_pc: {virtual_pc}")
        else:
            all_ready = 0
            for n in range(0, WOOD_NWIDE):
                all_ready += in_ready[n].value.integer

            if all_ready == WOOD_NWIDE:
                virtual_pc += WOOD_NWIDE

        await RisingEdge(dut.clock)


@cocotb.test()
async def test_wood(dut):
    await cocotb.start(
        Clock(dut.clock, CLOCK_PERIOD, TIME_UNIT).start(start_high=False)
    )
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    cocotb.start_soon(watchdog_timer())
    cocotb.start_soon(flist_monitor(dut))
    cocotb.start_soon(decode_driver(dut))
    await cocotb.start_soon(diff_traces(dut))
