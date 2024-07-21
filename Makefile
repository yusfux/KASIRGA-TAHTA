SUBMAKE := $(MAKE) --no-print-directory -C

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

.PHONY: all test
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: asm_test
asm_test:
	asmgen --inst $(inst) --type $(type) --num-insts $(numInst) &> src/test/c/src/test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/main.hex -g $(nWide) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --test tb_wood --top Wood --waves true --sim questa --dir ./test_run_dir/Wood_should_emit_for_cocotb/
.PHONY: clean
clean:
	-+@$(SUBMAKE) src/test/c/ clean

