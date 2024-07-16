import json
import os
from decimal import Decimal
from pathlib import Path

import cocotb
import git
from cocotb.clock import Clock
from cocotb.triggers import Event, FallingEdge, RisingEdge, Timer
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

TIMEOUT = 200  # watchdog timer, resets itself
WOOD_NWIDE = 1
WOOD_INSTRUCTION_PATH = Path(f"{build_dir}/main0.hex")
WOOD_SPIKE_TRACE_PATH = Path(f"{build_dir}/spike_trace.json")

timeout = Event(name="timeout")


@cocotb.coroutine
async def watchdog_timer():
    timer = 0
    while timer < TIMEOUT:
        if timeout.is_set():
            timer = 0
            timeout.clear()
        timer = timer + 1
        await Timer(Decimal("1"), units="ns")
    assert 0, "TIMEOUT! DUT halted."


async def get_spike_trace():
    with open(WOOD_SPIKE_TRACE_PATH, "r") as f:
        spike_trace = json.load(f)

    return spike_trace


@cocotb.coroutine
async def diff_traces(dut):
    spike_trace = await get_spike_trace()
    await FallingEdge(dut.reset)

    await RisingEdge(dut.clock)
    # with open("wood.trace", "w") as f:
    #     f.write("\n".join(final_logs))

    # validWrite & io_in_0_bits_rd == 5'h0;
    while True:
        rd = getattr(dut, "exunit.arstage.io_in_0_bits_rd").value.integer
        rd_tag = getattr(dut, "exunit.arstage.io_in_0_bits_rdTag").value.integer
        inst = getattr(dut, "exunit.arstage.io_in_0_bits_inst").value.integer
        we = getattr(dut, "exunit.arstage.validWrite").value.integer

        rd_data = getattr(dut, f"exunit.rrstage.prf_{rd_tag}").value.integer

        if we:
            st = spike_trace.pop()
            inst_p = "{0:#0{1}x}".format(inst, 10)
            rd_data_p = "{0:#0{1}x}".format(rd_data, 10)
            t = f"{inst_p} x{rd:>2} {rd_data_p}"
            print(t, f"{get_sim_time(units='ns')}ns")
            print("spike: ", st)
            # trace.append(t)
            timeout.set()
        await RisingEdge(dut.clock)


@cocotb.coroutine
async def decode_driver(dut):
    index = 0  # WOOD_NWIDE - 1  # TODO

    with open(WOOD_INSTRUCTION_PATH, "r") as f:
        inst_list = f.readlines()
        inst_list.reverse()

    if not inst_list:
        print(
            f"No instructions found in WOOD_INSTRUCTION_PATH: {WOOD_INSTRUCTION_PATH}"
        )
        assert 0

    dut.reset.value = 0  # START

    dut.io_in_0_valid.value = 0
    dut.io_in_0_bits.value = 0

    dut.io_pcIdx_valid.value = 0
    await RisingEdge(dut.clock)

    inst = inst_list.pop()
    in_valid = getattr(dut, f"io_in_{index}_valid")
    in_bits = getattr(dut, f"io_in_{index}_bits")
    while True:
        if not inst_list:
            break
        in_valid.value = 1
        in_bits.value = int(inst, 16)

        dut.io_pcIdx_valid.value = 1
        await RisingEdge(dut.clock)
        print(inst)
        try:
            if dut.io_in_0_ready.value.integer:
                inst = inst_list.pop()
            else:
                pass
        except Exception as e:
            print(e)
            assert 0


@cocotb.test()
async def test_teknofest_wrapper(dut):
    await cocotb.start(Clock(dut.clock, 10, "ns").start(start_high=False))
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    cocotb.start_soon(watchdog_timer())
    cocotb.start_soon(decode_driver(dut))
    await cocotb.start_soon(diff_traces(dut))
