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

SUBMAKE := $(MAKE) inst=$(inst) --no-print-directory -C

.PHONY: all test
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: exunit_asm_test
exunit_asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) --out_asm_file src/test/c/src/$(inst)_test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_exunit --top ExUnit --waves true --sim questa --dir ./test_run_dir/ExUnit_should_emit_for_cocotb/

.PHONY: wood_asm_test
wood_asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) --out_asm_file src/test/c/src/$(inst)_test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/$(inst)_main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --inst $(inst) --width $(nWide) --test tb_wood --top WoodDut --waves true --sim questa --dir ./test_run_dir/WoodDut_should_emit_for_cocotb/


.PHONY: clean
clean:
	-+@$(SUBMAKE) src/test/c/ clean

