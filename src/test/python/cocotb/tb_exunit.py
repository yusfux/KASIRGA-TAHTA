import os
from pathlib import Path

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, FallingEdge, RisingEdge
from libs import (
    watchdog_timer,
    branch_monitor,
    flist_monitor,
    diff_traces,
    store_monitor,
)


@cocotb.coroutine
async def decode_driver(dut, hex_path, nwide, base_addr):
    insts: list[str] = []
    with open(hex_path, "r") as f:
        raw_lines = f.readlines()
        insts = [line.strip() for line in raw_lines]
        print(f"[INFO] Reading: {insts}")

    insts.append("0" * 1024)

    virtual_pc = 0  # raw, requires (* 4) + base

    in_valid = []
    in_ready = []
    in_inst = []
    in_pc = []
    for n in range(0, nwide):
        in_valid.append(getattr(dut, f"io_in_{n}_valid"))
        in_inst.append(getattr(dut, f"io_in_{n}_bits_inst"))
        in_pc.append(getattr(dut, f"io_in_{n}_bits_pc"))
        in_ready.append(getattr(dut, f"io_in_{n}_ready"))

    dut.reset.value = 0  # START

    for n in range(0, nwide):
        in_inst[n].value = int(insts[virtual_pc + n], 16)
        in_pc[n].value = base_addr + (4 * (virtual_pc + n))
        in_valid[n].value = 1

    virtual_pc += nwide

    await RisingEdge(dut.clock)

    while True:
        jumped = 0
        for n in range(0, nwide):
            in_inst[n].value = int(insts[virtual_pc + n], 16)
            in_pc[n].value = base_addr + (4 * (virtual_pc + n))
            in_valid[n].value = 1
            # all_ready += in_ready[n].value.integer

        await FallingEdge(dut.clock)

        bp_pc = [0 for _ in range(nwide)]
        bp_taken = [0 for _ in range(nwide)]
        bp_targetPC = [0 for _ in range(nwide)]
        bp_valid = [0 for _ in range(nwide)]
        bp_taken = [0 for _ in range(nwide)]
        bp_exception = [0 for _ in range(nwide)]
        for n in range(0, nwide):
            if getattr(dut, f"rsstage.io_bpBus_{n}_bits_pc").value.is_resolvable:
                bp_pc[n] = getattr(dut, f"rsstage.io_bpBus_{n}_bits_pc").value.integer
            bp_taken[n] = getattr(dut, f"rsstage.io_bpBus_{n}_bits_taken").value.integer
            bp_targetPC[n] = getattr(
                dut, f"rsstage.io_bpBus_{n}_bits_targetPC"
            ).value.integer
            bp_exception[n] = getattr(
                dut, f"rsstage.io_bpBus_{n}_bits_exception"
            ).value.integer
            bp_valid[n] = getattr(dut, f"rsstage.io_bpBus_{n}_valid").value.integer

            if (bp_taken[n] | bp_exception[n]) & bp_valid[n]:
                real_pc = bp_targetPC[n]
                virtual_pc = (real_pc - base_addr) // 4
                print("JUMP!", f"real_pc: {real_pc:0>8X} ", f"virtual_pc: {virtual_pc}")
                jumped = 1
                break

        if not jumped:
            all_ready = 0
            for n in range(0, nwide):
                all_ready += in_ready[n].value.integer

            if all_ready == nwide:
                virtual_pc += nwide

        if jumped:  # wait for fetch pipeline depth
            for n in range(0, nwide):
                in_inst[n].value = 0
                in_pc[n].value = 0
                in_valid[n].value = 0

            await RisingEdge(dut.clock)
            await RisingEdge(dut.clock)
            await RisingEdge(dut.clock)

        await RisingEdge(dut.clock)


@cocotb.test()
async def test_wood(dut):
    inst = os.getenv("INST")
    nwide = int(os.getenv("NWIDE", 0))

    clock_period = 10
    time_unit = "ns"
    timeout_value = 4000
    base_addr = 0x80000000
    timeout_event = Event(name="timeout")
    top = ""  # relative to exunit

    repo = git.Repo(".", search_parent_directories=True)
    project_dir = repo.working_tree_dir
    cwd = os.path.abspath(f"{project_dir}")
    build_dir = f"{cwd}/src/test/c/build"

    hex_path = Path(f"{build_dir}/{inst}_main.hex")
    trace_path = Path(f"{build_dir}/{inst}_spike_trace.json")

    await cocotb.start(
        Clock(dut.clock, clock_period, time_unit).start(start_high=False)
    )
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    cocotb.start_soon(watchdog_timer(timeout_event, timeout_value, time_unit))

    cocotb.start_soon(branch_monitor(dut, top, nwide, time_unit, trace_path))
    cocotb.start_soon(store_monitor(dut, top, time_unit, trace_path))
    cocotb.start_soon(flist_monitor(dut, top, nwide, time_unit))
    cocotb.start_soon(decode_driver(dut, hex_path, nwide, base_addr))
    await cocotb.start_soon(
        diff_traces(dut, top, timeout_event, trace_path, nwide, time_unit)
    )
