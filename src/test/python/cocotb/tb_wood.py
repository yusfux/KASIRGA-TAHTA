import os
from pathlib import Path

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, RisingEdge
from libs import diff_traces, flist_monitor, watchdog_timer


@cocotb.coroutine
async def main_memory_writer(dut, hex_path):
    insts: list[str] = []
    with open(hex_path, "r") as f:
        raw_lines = f.readlines()
        insts = [line.strip() for line in raw_lines]
        print(f"[INFO] Reading: {insts}")

    insts.append("0" * 1024)

    combined_inst = []
    for index, inst in enumerate(insts):
        combined_inst.append(inst)
        if ((index + 1) % 4 == 0) and (index != 0):
            try:
                combined_inst.reverse()
                real_comb = ""
                for i in combined_inst:
                    real_comb += i

                print(real_comb)

                dut.mem.mem_ext.Memory[index >> 2].value = int(real_comb, 16)
                combined_inst = []
            except Exception:
                break


@cocotb.test()
async def test_wood(dut):
    inst = os.getenv("INST")
    nwide = int(os.getenv("NWIDE", 0))

    clock_period = 10
    time_unit = "ns"
    timeout_value = 4000
    # base_addr = 0x80000000
    timeout_event = Event(name="timeout")
    top = "wood.exunit."  # relative to exunit

    repo = git.Repo(".", search_parent_directories=True)
    project_dir = repo.working_tree_dir
    cwd = os.path.abspath(f"{project_dir}")
    build_dir = f"{cwd}/src/test/c/build"

    hex_path = Path(f"{build_dir}/{inst}_main.hex")
    trace_path = Path(f"{build_dir}/{inst}_spike_trace.json")

    await cocotb.start(
        Clock(dut.clock, clock_period, time_unit).start(start_high=False)
    )
    dut.reset.value = 1
    dut.io_wreset.value = 1
    await RisingEdge(dut.clock)
    await cocotb.start_soon(main_memory_writer(dut, hex_path))
    await RisingEdge(dut.clock)
    dut.reset.value = 0
    dut.io_wreset.value = 0

    # wait for fstage initialization
    if "wood.exunit." in top:
        while True:
            await RisingEdge(dut.clock)
            if (
                dut.wood.frunit.f2stage.icachebankcont.icachebank_1.icachecontroller.state.value.integer
                != 0
            ):
                break

    cocotb.start_soon(watchdog_timer(timeout_event, timeout_value, time_unit))
    # cocotb.start_soon(branch_monitor(dut, top, nwide, time_unit, trace_path)) # TODO: enable after branch predictor
    cocotb.start_soon(flist_monitor(dut, top, nwide, time_unit))
    await cocotb.start_soon(
        diff_traces(dut, top, timeout_event, trace_path, nwide, time_unit)
    )
