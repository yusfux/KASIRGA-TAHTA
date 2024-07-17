import json
import os
from decimal import Decimal
from pathlib import Path

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

TIMEOUT = 800  # watchdog timer, resets itself
WOOD_NWIDE = 1
WOOD_INSTRUCTION_PATH = Path(f"{build_dir}/main0.hex")
WOOD_SPIKE_TRACE_PATH = Path(f"{build_dir}/spike_trace.json")

timeout = Event(name="timeout")
start = Event(name="start")
timeout.clear()
start.clear()


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

    start.wait()
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    previous_pc = 69
    while True:
        rd = getattr(dut, "exunit.arstage.io_in_0_bits_rd").value.integer
        rd_tag = getattr(dut, "exunit.arstage.io_in_0_bits_rdTag").value.integer
        inst = getattr(dut, "exunit.arstage.io_in_0_bits_inst").value.integer
        we = getattr(dut, "exunit.arstage.validWrite").value.integer
        pc = getattr(dut, "exunit.arstage.io_in_0_bits_pcIdx").value.integer
        rd_data = getattr(dut, f"exunit.rrstage.prf_{rd_tag}").value.integer

        if we and (previous_pc != pc):
            previous_pc = pc
            golden_reference = spike_trace.pop(0)
            print(golden_reference)

            inst_p = "{0:#0{1}x}".format(inst, 10)
            pc_p = "{0:#0{1}x}".format(pc, 10)
            rd_data_p = "{0:#0{1}x}".format(rd_data, 10)
            inst = f"{inst_p}".strip()
            result = f"x{rd:>2} {rd_data_p}"

            print(
                f"{{'pc': '{pc_p}', 'inst': '{inst}','result': '{result}', 'time': {get_sim_time(units='ns')}ns}}"
            )

            assert (
                inst == golden_reference["inst"]
            ), f"Instruction is {inst} but it should be {golden_reference['inst']} at {get_sim_time(units='ns')}ns"
            golden_result = golden_reference["result"]
            if not golden_result:
                golden_result = "x 0 0x00000000"
            if "x 0" in result:
                result = "x 0 0x00000000"
            assert (
                result == golden_result
            ), f"Result is {result} but it should be {golden_reference['result']} at {get_sim_time(units='ns')}ns"

            timeout.set()
        await RisingEdge(dut.clock)


@cocotb.coroutine
async def decode_driver(dut):
    index = 0  # WOOD_NWIDE - 1  # TODO

    with open(WOOD_INSTRUCTION_PATH, "r") as f:
        inst_list = f.readlines()

    pc_and_inst = []
    base_address = 0x80000000
    for i, instruction in enumerate(inst_list):
        pc_address = base_address + 4 * i
        pc_instructions_tuple = (pc_address, int(instruction.strip(), 16))
        pc_and_inst.append(pc_instructions_tuple)

    if not inst_list:
        print(
            f"No instructions found in WOOD_INSTRUCTION_PATH: {WOOD_INSTRUCTION_PATH}"
        )
        assert 0

    dut.io_in_0_valid.value = 0
    dut.io_in_0_bits.value = 0
    dut.io_pcIdx_valid.value = 0

    pc, inst = pc_and_inst.pop(0)

    in_valid = getattr(dut, f"io_in_{index}_valid")
    in_bits = getattr(dut, f"io_in_{index}_bits")
    pc_idx = getattr(dut, "io_pcIdx_bits")

    in_valid.value = 1
    in_bits.value = inst
    pc_idx.value = pc

    dut.reset.value = 0  # START
    start.set()

    while True:
        if not pc_and_inst:
            break
        in_valid.value = 1
        in_bits.value = inst
        pc_idx.value = pc

        dut.io_pcIdx_valid.value = 1
        await RisingEdge(dut.clock)
        # print(inst)
        try:
            if dut.io_in_0_ready.value.integer:
                pc, inst = pc_and_inst.pop(0)
            else:
                pass
        except Exception as e:
            print(e)
            assert 0


@cocotb.test()
async def test_wood(dut):
    await cocotb.start(Clock(dut.clock, 10, "ns").start(start_high=False))
    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)

    cocotb.start_soon(watchdog_timer())
    cocotb.start_soon(decode_driver(dut))
    await cocotb.start_soon(diff_traces(dut))
