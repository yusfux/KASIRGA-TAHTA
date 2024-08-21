import json
import os
from decimal import Decimal
from enum import Enum
from pathlib import Path
from typing import Any

import cocotb
import git
from cocotb.triggers import RisingEdge, Timer
from cocotb.utils import get_sim_time

repo = git.Repo(".", search_parent_directories=True)
project_dir = repo.working_tree_dir
cwd = os.path.abspath(f"{project_dir}")
build_dir = f"{cwd}/src/test/c/build"


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
async def watchdog_timer(timeout_event, timeout_value, time_unit):
    timer = 0
    while timer < timeout_value:
        if timeout_event.is_set():
            timer = 0
            timeout_event.clear()
        timer = timer + 1
        await Timer(Decimal("1"), units=time_unit)
    assert 0, f"TIMEOUT! DUT halted after {timeout_value} {time_unit}."


async def get_spike_trace(trace_path):
    with open(trace_path, "r") as f:
        spike_trace = json.load(f)

    return spike_trace


@cocotb.coroutine
async def flist_monitor(dut, top, nwide, time_unit: str):
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    flist = {}

    while True:
        in_valid = [0 for _ in range(nwide)]
        in_ready = [0 for _ in range(nwide)]
        in_tag = [0 for _ in range(nwide)]
        for n in range(0, nwide):
            in_valid[n] = getattr(
                dut, f"{top}mistage.flist.io_in_{n}_valid"
            ).value.integer
            in_ready[n] = getattr(
                dut, f"{top}mistage.flist.io_in_{n}_ready"
            ).value.integer
            in_tag[n] = getattr(
                dut, f"{top}mistage.flist.io_in_{n}_bits_tag"
            ).value.integer

            if in_ready[n] & in_valid[n]:
                if 0 in flist:
                    assert 0, f"Zero tag inserted! tag_{n} {color(in_tag[n], Color.GREEN)} at {get_sim_time(units=time_unit)}{time_unit}"
                elif in_tag[n] in flist:
                    assert 0, f"Flist tag inserted twice! tag_{n} {color(in_tag[n], Color.GREEN)} at {get_sim_time(units=time_unit)}{time_unit}"
                else:
                    flist[in_tag[n]] = in_tag[n]

        out_valid = [0 for _ in range(nwide)]
        out_ready = [0 for _ in range(nwide)]
        out_tag = [0 for _ in range(nwide)]
        for n in range(0, nwide):
            out_valid[n] = getattr(
                dut, f"{top}mistage.flist.io_out_{n}_valid"
            ).value.integer
            out_ready[n] = getattr(
                dut, f"{top}mistage.flist.io_out_{n}_ready"
            ).value.integer
            out_tag[n] = getattr(
                dut, f"{top}mistage.flist.io_out_{n}_bits_tag"
            ).value.integer

            if out_ready[n] & out_valid[n]:
                if 0 == out_tag[n]:
                    assert 0, f"Zero read! tag_{n} {color(in_tag[n], Color.GREEN)} at {get_sim_time(units=time_unit)}{time_unit}"
                if out_tag[n] in flist:
                    del flist[out_tag[n]]

        await RisingEdge(dut.clock)


@cocotb.coroutine
async def get_branch_trace(trace_path: str):
    spike_trace = await get_spike_trace(trace_path)

    branches_and_jumps = []
    branch_instructions = [
        "beq",
        "bne",
        "blt",
        "bge",
        "bltu",
        "bgeu",
        "jal",
        "jalr",
        "j  ",
    ]

    for i, instr in enumerate(spike_trace):
        if any(
            instr["alias_numeric"].startswith(branch) for branch in branch_instructions
        ):
            entry = {"pc": instr["pc"], "alias_numeric": instr["alias_numeric"]}

            # Check if it's a taken branch/jump
            if i < len(spike_trace) - 1:
                current_pc = int(instr["pc"], 16)
                next_pc = int(spike_trace[i + 1]["pc"], 16)
                entry["targetPC"] = spike_trace[i + 1]["pc"]
                if next_pc != current_pc + 4:
                    entry["taken"] = 1
                else:
                    entry["taken"] = 0
            else:
                entry["taken"] = "Unknown (last instruction)"

            branches_and_jumps.append(entry)

    btfile = "./branch_trace.json"
    with Path(btfile).open("w") as output_file:
        output_file.write("[\n")
        output_file.write(
            ",\n".join(
                [
                    json.dumps(entry, separators=(",", ":"))
                    for entry in branches_and_jumps
                ]
            )
        )
        output_file.write("]\n")

    return branches_and_jumps


@cocotb.coroutine
async def branch_monitor(dut, top: str, nwide: int, time_unit: str, trace_path: str):
    branch_trace = await get_branch_trace(trace_path)

    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    while True:
        bp_pc = [0 for _ in range(nwide)]
        bp_taken = [0 for _ in range(nwide)]
        bp_targetPC = [0 for _ in range(nwide)]
        bp_valid = [0 for _ in range(nwide)]
        bp_taken = [0 for _ in range(nwide)]
        for n in range(0, nwide):
            bp_pc[n] = getattr(dut, f"{top}rsstage.io_bpBus_{n}_bits_pc").value.integer
            bp_taken[n] = getattr(
                dut, f"{top}rsstage.io_bpBus_{n}_bits_taken"
            ).value.integer
            bp_targetPC[n] = getattr(
                dut, f"{top}rsstage.io_bpBus_{n}_bits_targetPC"
            ).value.integer
            bp_valid[n] = getattr(dut, f"{top}rsstage.io_bpBus_{n}_valid").value.integer

        for n in range(0, nwide):
            if bp_valid[n]:
                current_trace = branch_trace.pop(0)
                targetPC = "{0:#0{1}x}".format(bp_targetPC[n], 10)
                pc = "{0:#0{1}x}".format(bp_pc[n], 10)
                golden_taken = current_trace["taken"]
                golden_targetPC = current_trace["targetPC"]
                golden_pc = current_trace["pc"]

                if (targetPC == golden_targetPC) and (bp_pc[n] + 4 == bp_targetPC[n]):
                    pass
                else:
                    if bp_taken[n] == 0:
                        targetPC = "{0:#0{1}x}".format(bp_pc[n] + 4, 10)

                    assert (
                        (targetPC == golden_targetPC)
                        and (pc == golden_pc)
                        and (bp_taken[n] == golden_taken)
                    ), f"{color('Incorrect JUMP!', Color.RED)}                                           \n \
                         TargetPC: {color(targetPC, Color.GREEN)} {color(golden_targetPC, Color.YELLOW)} \n \
                         PC:       {color(pc, Color.GREEN)} {color(golden_pc, Color.YELLOW)}             \n \
                         Taken:    {color(bp_taken[n], Color.GREEN)} {color(golden_taken, Color.YELLOW)} at {get_sim_time(units=time_unit)}{time_unit}\n \
                    "

        await RisingEdge(dut.clock)


@cocotb.coroutine
async def diff_traces(
    dut, top, timeout_event, trace_path: str, nwide: int, time_unit: str
):
    spike_trace = await get_spike_trace(trace_path)

    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    while True:
        isStore = [0 for _ in range(nwide)]

        in_valid = [0 for _ in range(nwide)]
        in_ready = [0 for _ in range(nwide)]
        in_wrf = [0 for _ in range(nwide)]

        arfBus_adr = [0 for _ in range(nwide)]
        arfBus_tag = [0 for _ in range(nwide)]
        arfBus_valid = [0 for _ in range(nwide)]
        commBus_tag = [0 for _ in range(nwide)]
        commBus_valid = [0 for _ in range(nwide)]
        inst = [0 for _ in range(nwide)]
        pc = [0 for _ in range(nwide)]
        rd_data = [0 for _ in range(nwide)]

        retired = [0 for _ in range(nwide)]
        flushed = [0 for _ in range(nwide)]
        for n in range(0, nwide):
            isStore[n] = getattr(dut, f"{top}rwstage.isStore_{n}").value.integer

            arfBus_valid[n] = getattr(
                dut, f"{top}rwstage.io_arfBus_{n}_valid"
            ).value.integer

            if getattr(dut, f"{top}rwstage.io_arfBus_{n}_bits_rd").value.is_resolvable:
                arfBus_adr[n] = getattr(
                    dut, f"{top}rwstage.io_arfBus_{n}_bits_rd"
                ).value.integer

            arfBus_tag[n] = getattr(
                dut, f"{top}rwstage.io_arfBus_{n}_bits_tag"
            ).value.integer

            commBus_valid[n] = getattr(
                dut, f"{top}rwstage.io_commitedBus_{n}_valid"
            ).value.integer
            commBus_tag[n] = getattr(
                dut, f"{top}rwstage.io_commitedBus_{n}_bits_tag"
            ).value.integer

            if getattr(dut, f"{top}rwstage.io_in_{n}_bits_inst").value.is_resolvable:
                inst[n] = getattr(
                    dut, f"{top}rwstage.io_in_{n}_bits_inst"
                ).value.integer

            pc[n] = getattr(dut, f"{top}rwstage.io_in_{n}_bits_pc").value.integer
            rd_data[n] = getattr(dut, f"{top}rrstage.prf_{arfBus_tag[n]}").value.integer
            in_valid[n] = getattr(dut, f"{top}rwstage.io_in_{n}_valid").value.integer
            in_ready[n] = getattr(dut, f"{top}rwstage.io_in_{n}_ready").value.integer

            if getattr(dut, f"{top}rwstage.io_in_{n}_bits_writeRf").value.is_resolvable:
                in_wrf[n] = getattr(
                    dut, f"{top}rwstage.io_in_{n}_bits_writeRf"
                ).value.integer

            flushed[n] = getattr(
                dut, f"{top}rwstage.io_in_{n}_bits_flushed"
            ).value.integer
            retired[n] = in_ready[n] and in_valid[n]

        golden_reference = [{} for _ in range(nwide)]
        inst_p = ["" for _ in range(nwide)]
        pc_p = ["" for _ in range(nwide)]
        rd_data_p = ["" for _ in range(nwide)]

        for n in range(0, nwide):
            if retired[n] and not flushed[n]:
                golden_reference[n] = spike_trace.pop(0)
                print(color(golden_reference[n], Color.YELLOW), f" n={n}", flush=True)

                inst_p[n] = "{0:#0{1}x}".format(inst[n], 10)
                pc_p[n] = "{0:#0{1}x}".format(pc[n], 10)
                rd_data_p[n] = "{0:#0{1}x}".format(rd_data[n], 10)
                inst_p[n] = f"{inst_p[n]}".strip()
                rd_data_p[n] = f"x{arfBus_adr[n]:>2} {rd_data_p[n]}"

                golden_result = golden_reference[n]["result"]
                golden_pc = golden_reference[n]["pc"]
                golden_inst = golden_reference[n]["inst"]

                # print(
                #     f"{{'pc': '{pc_p[n]}', 'inst': '{inst_p[n]}','result': '{rd_data_p[n]}', 'time': {get_sim_time(units=time_unit)}{time_unit}, 'n': {n}}}"
                # )

                assert (
                    pc_p[n] == golden_pc
                ), f"PC is {color(pc_p[n], Color.GREEN)} but it should be {color(golden_pc, Color.YELLOW)} at {get_sim_time(units=time_unit)}{time_unit}"

                if isStore[n]:
                    # store happens unknown time after the rob, so dont validate write data, only addr
                    addr = "0x" + rd_data_p[n].split("0x")[-1]
                    is_correct_addr = addr in golden_result
                    assert is_correct_addr, f"Store Addr is {color(rd_data_p[n], Color.GREEN)} but it should be {color(golden_result, Color.YELLOW)} at {get_sim_time(units=time_unit)}{time_unit}"
                    continue

                assert (
                    inst_p[n] == golden_inst
                ), f"Instruction is {color(inst_p[n], Color.GREEN)} but it should be {color(golden_inst, Color.YELLOW)} at {get_sim_time(units=time_unit)}{time_unit}"

                if not golden_result:
                    golden_result = "x 0 0x00000000"
                if "x 0" in rd_data_p[n]:
                    rd_data_p[n] = "x 0 0x00000000"

                if not (in_wrf[n] or (arfBus_adr[n] == 0)):
                    rd_data_p[n] = "x 0 0x00000000"  # branch

                assert (
                    rd_data_p[n] == golden_result
                ), f"Result is {color(rd_data_p[n], Color.GREEN)} at tag {color(arfBus_tag[n], Color.GREEN)} but it should be {color(golden_result, Color.YELLOW)} at {get_sim_time(units=time_unit)}{time_unit}"

        for n in range(0, nwide):
            if retired[n] and not flushed[n]:
                if arfBus_valid[n] and commBus_valid[n]:
                    assert (
                        arfBus_tag[n] != commBus_tag[n]
                    ), f"Tag duplicated! tag_{n} {color(arfBus_tag[n], Color.GREEN)} is also commited at {get_sim_time(units=time_unit)}{time_unit}"
                if not arfBus_valid[n] and not commBus_valid[n]:
                    assert 0, f"Tag lost! tag_{n} {color(arfBus_tag[n], Color.GREEN)}, pc: {pc_p[n]}, {get_sim_time(units=time_unit)}{time_unit}"

                timeout_event.set()

        # Check for cloned tags in commBus
        valid_commBus_tags = [
            tag for tag, valid in zip(commBus_tag, commBus_valid) if valid
        ]
        commBus_clones = [
            tag for tag in set(valid_commBus_tags) if valid_commBus_tags.count(tag) > 1
        ]
        if commBus_clones:
            assert 0, f"Tag(s) cloned in commBus: {color(', '.join(map(str, commBus_clones)), Color.GREEN)} at {get_sim_time(units=time_unit)}{time_unit}"

        # Check for cloned tags in arfBus
        valid_arfBus_tags = [
            tag for tag, valid in zip(arfBus_tag, arfBus_valid) if valid
        ]
        arfBus_clones = [
            tag for tag in set(valid_arfBus_tags) if valid_arfBus_tags.count(tag) > 1
        ]
        if arfBus_clones:
            assert 0, f"Tag(s) cloned in arfBus: {color(', '.join(map(str, arfBus_clones)), Color.GREEN)} at {get_sim_time(units=time_unit)}{time_unit}"

        await RisingEdge(dut.clock)
