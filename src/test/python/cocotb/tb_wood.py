import os
from pathlib import Path

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, RisingEdge
from libs import branch_monitor, diff_traces, flist_monitor, watchdog_timer


@cocotb.coroutine
async def main_memory_writer(dut, hex_path):
    insts: list[str] = []
    with open(hex_path, "r") as f:
        raw_lines = f.readlines()
        insts = [line.strip() for line in raw_lines]
        print(f"[INFO] Reading: {insts}")

    insts.append("0" * 1024)

    combined_inst = ""
    for index, inst in enumerate(insts):
        combined_inst += inst
        if index % 4:
            try:
                dut.mem.mem_ext.Memory[index >> 2].value = int(inst, 16)
                combined_inst = ""
            except Exception:
                break

    dut.reset.value = 1


@cocotb.test()
async def test_wood(dut):
    clock_period = 10
    time_unit = "ns"
    timeout_value = 400
    nwide = 2
    # base_addr = 0x80000000
    timeout_event = Event(name="timeout")
    top = "wood.exunit."  # relative to exunit

    repo = git.Repo(".", search_parent_directories=True)
    project_dir = repo.working_tree_dir
    cwd = os.path.abspath(f"{project_dir}")
    build_dir = f"{cwd}/src/test/c/build"

    hex_path = Path(f"{build_dir}/main.hex")
    trace_path = Path(f"{build_dir}/spike_trace.json")

    await cocotb.start(
        Clock(dut.clock, clock_period, time_unit).start(start_high=False)
    )
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    await cocotb.start_soon(main_memory_writer(dut, hex_path))
    cocotb.start_soon(watchdog_timer(timeout_event, timeout_value, time_unit))
    cocotb.start_soon(branch_monitor(dut, top, nwide, time_unit, trace_path))
    cocotb.start_soon(flist_monitor(dut, top, nwide, time_unit))
    await cocotb.start_soon(
        diff_traces(dut, top, timeout_event, trace_path, nwide, time_unit)
    )
