# All args except for the first one (which is the target name)
ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
# first argument
nWide := $(word 1,$(ARGS))
# second argument
inst := $(word 2,$(ARGS))
# third argument
type := $(word 3,$(ARGS))
# fourth argument
numInst := $(word 4,$(ARGS))
# fifth argument, true or false
wave := $(word 5,$(ARGS))

XLEN := 32
OBJCOPY = $(RISCV_PREFIX)objcopy

SUBMAKE := $(MAKE) inst=$(inst) XLEN=$(XLEN) --no-print-directory -C

.PHONY: all test
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: exunit_asm_test
exunit_asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) --out_asm_file src/test/c/src/$(inst)_test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnit --waves $(wave) --sim questa --dir ./test_run_dir/ExUnit_should_emit_for_cocotb/

.PHONY: exunitdut_asm_test
exunitdut_asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) --out_asm_file src/test/c/src/$(inst)_test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnitDut --waves $(wave) --sim questa --dir ./test_run_dir/ExUnitDut_should_emit_for_cocotb/

.PHONY: wood_asm_test
wood_asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) --out_asm_file src/test/c/src/$(inst)_test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_wood --top WoodDut --waves $(wave)  --sim questa --dir ./test_run_dir/WoodDut_should_emit_for_cocotb/

.PHONY: exunit_csmith_test
exunit_csmith_test:
	csmith --seed 69 --no-argc --no-float --quiet --no-builtins --max-pointer-depth 10 --max-block-size 7 --max-array-dim 10 --max-funcs 100 --no-hash-value-printf > src/test/c/src/$(inst).c
	sed -i 's/^.*platform_main/\/\/&/' src/test/c/src/$(inst).c
	genstarts -f src/test/c/src/$(inst).S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnit --waves $(wave)  --sim questa --dir ./test_run_dir/ExUnit_should_emit_for_cocotb/

.PHONY: wood_csmith_test
wood_csmith_test:
	csmith --seed 69 --no-argc --no-float --quiet --no-builtins --max-pointer-depth 10 --max-block-size 7 --max-array-dim 10 --max-funcs 100 --no-hash-value-printf > src/test/c/src/$(inst).c
	sed -i 's/^.*platform_main/\/\/&/' src/test/c/src/$(inst).c
	genstarts -f src/test/c/src/$(inst).S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_wood --top WoodDut --waves $(wave)  --sim questa --dir ./test_run_dir/WoodDut_should_emit_for_cocotb

.PHONY: exunit_coremark_test
exunit_coremark_test:
	# assumes coremark is present:
	spike -m0x80000000:0x550000  --log-commits --isa=rv32gc -l src/test/c/build/$(inst)_main.elf &> src/test/c/build/$(inst)_spike.trace
	spiketrace2json -f src/test/c/build/$(inst)_spike.trace -o src/test/c/build/$(inst)_spike_trace.json


	$(OBJCOPY) src/test/c/build/$(inst)_main.elf -O binary src/test/c/build/$(inst)_main.bin
	bin2hex src/test/c/build/$(inst)_main.bin  > src/test/c/build/$(inst)_main.hex

	dump2gtkw  src/test/aapg/work/objdump/$(inst).objdump > src/test/c/build/$(inst)_main.gtkw.map
	dump2vsim  src/test/aapg/work/objdump/$(inst).objdump > src/test/c/build/$(inst)_main.vsim.map

	cp  src/test/c/src/exunit_wave.do src/test/c/build/exunit_wave.do
	cp  src/test/c/src/wood_wave.do   src/test/c/build/wood_wave.do
	sed -i '1r src/test/c/build/$(inst)_main.vsim.map' src/test/c/build/wood_wave.do
	sed -i '1r src/test/c/build/$(inst)_main.vsim.map' src/test/c/build/exunit_wave.do

	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnit --waves $(wave)  --sim questa --dir ./test_run_dir/ExUnit_should_emit_for_cocotb/

.PHONY: exunit_aapg_test
exunit_aapg_test:
	mkdir -p src/test/aapg/
	cd src/test/aapg/ && aapg setup
	rm src/test/aapg/work/config.yaml
	cp src/test/aapg/config.yaml src/test/aapg/work/config.yaml
	cd src/test/aapg && aapg gen --arch rv32 --no_headers --seed 60 --asm_name $(inst)
	rm src/test/aapg/work/common/crt.S
	cp src/test/aapg/crt.S src/test/aapg/work/common/crt.S
	+@$(SUBMAKE) src/test/aapg/work

	cp src/test/aapg/work/bin/$(inst).riscv src/test/c/build/$(inst)_main.elf
	cp src/test/aapg/work/objdump/$(inst).objdump src/test/c/build/$(inst)_main.dump

	spike -m0x80000000:0x550000  --log-commits --isa=rv32gc -l src/test/c/build/$(inst)_main.elf &> src/test/c/build/$(inst)_spike.trace
	spiketrace2json -f src/test/c/build/$(inst)_spike.trace -o src/test/c/build/$(inst)_spike_trace.json


	$(OBJCOPY) src/test/c/build/$(inst)_main.elf -O binary src/test/c/build/$(inst)_main.bin
	bin2hex src/test/c/build/$(inst)_main.bin  > src/test/c/build/$(inst)_main.hex

	dump2gtkw  src/test/aapg/work/objdump/$(inst).objdump > src/test/c/build/$(inst)_main.gtkw.map
	dump2vsim  src/test/aapg/work/objdump/$(inst).objdump > src/test/c/build/$(inst)_main.vsim.map

	cp  src/test/c/src/exunit_wave.do src/test/c/build/exunit_wave.do
	cp  src/test/c/src/wood_wave.do   src/test/c/build/wood_wave.do
	sed -i '1r src/test/c/build/$(inst)_main.vsim.map' src/test/c/build/wood_wave.do
	sed -i '1r src/test/c/build/$(inst)_main.vsim.map' src/test/c/build/exunit_wave.do

	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnit --waves $(wave)  --sim questa --dir ./test_run_dir/ExUnit_should_emit_for_cocotb/

.PHONY: clean
clean:
	-+@$(SUBMAKE) src/test/c/ clean

