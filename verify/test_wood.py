import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge, with_timeout
from tests.li_test import Test

test = Test(nWide=1)


@cocotb.coroutine
async def decode_driver(dut, index):
    dut.io_in_0_valid.value = 0
    dut.io_in_0_bits.value = 0

    dut.io_pcIdx_valid.value = 0
    dut.io_pcIdx_bits.value = 0
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)

    inst_list = test.instructions()[index]

    inst = inst_list.pop()
    while True:
        if not inst_list:
            break
        if index == 0:
            dut.io_in_0_valid.value = 1
            dut.io_in_0_bits.value = int(inst, 16)
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
        elif index == 1:
            pass  # TODO


@cocotb.test()
async def test_teknofest_wrapper(dut):
    await cocotb.start(Clock(dut.clock, 10, "ns").start(start_high=False))

    await RisingEdge(dut.clock)
    dut.reset.value = 1
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    await RisingEdge(dut.clock)
    dut.reset.value = 0

    await with_timeout(decode_driver(dut, 0), test.timeout(), "ns")
