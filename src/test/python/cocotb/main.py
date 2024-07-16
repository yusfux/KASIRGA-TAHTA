import argparse
import os
from pathlib import Path

from cocotb.runner import get_runner

SCRIPT_DIR = Path(os.path.realpath(__file__)).parent.absolute()


def run_test(
    simulator: str, test_file: Path, top_module: str, waves: bool, hdl_dir: str
):
    hdlPath = Path(hdl_dir)
    verilog_files = hdlPath.rglob("*.v")
    system_verilog_files = hdlPath.rglob("*.sv")

    verilog_headers = hdlPath.rglob("*.vh")
    system_verilog_headers = hdlPath.rglob("*.svh")

    verilog_sources = list(verilog_files) + list(system_verilog_files)
    include_dirs = [
        header.parent for header in list(system_verilog_headers) + list(verilog_headers)
    ]
    print("include_dirs: ", include_dirs)
    print("verilog_sources: ", verilog_sources)
    if not verilog_sources:
        print(f"ERROR: No verilog sources found in hdl_dir: {hdl_dir}")
        exit(1)

    runner = get_runner(simulator)
    runner.build(
        verilog_sources=verilog_sources,
        includes=include_dirs,
        hdl_toplevel=top_module,
        # parameters={"DEPTH": 123456},
        always=True,
    )

    runner.test(
        hdl_toplevel=top_module,
        test_module=str(test_file),
        waves=waves,
        pre_cmd=[
            'set WildcardFilter {};set WildcardSizeThreshold "16777216"; coverage save -onexit covres.ucdb; do wave.do;'
        ],
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--sim", type=str, required=True, help="Simulator. <icarus, verilator, questa>"
    )
    parser.add_argument(
        "--top", type=str, required=True, help="Top level hdl module to test a.k.a DUT"
    )
    parser.add_argument(
        "--dir", type=str, required=True, help="Directory containing HDL files"
    )
    parser.add_argument(
        "--test",
        type=str,
        required=True,
        help="Python test file to run, all tests inside will be run",
    )
    parser.add_argument("--waves", type=bool, help="Dump waves? <true,false>")
    args = parser.parse_args()

    test_dir = Path(SCRIPT_DIR)
    tests = list(test_dir.rglob("*.py"))
    print("test_dir: ", test_dir)
    print("tests: ", tests)

    if not tests:
        print(f"ERROR: No tests found in test_dir: {test_dir}")
        exit(1)

    test_names = {test.stem: test for test in test_dir.rglob("*.py")}

    # if args.test not in test_names:
    #     raise FileNotFoundError(f"Can't find <{args.test}> in <{tests}>")

    run_test(args.sim, args.test, args.top, args.waves, args.dir)
