SUBMAKE := $(MAKE) --no-print-directory -C

# All args except for the first one (which is the target name)
ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
# arg1 stands for the first argument
arg1 := $(firstword $(ARGS))

.PHONY: all test
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: li_test
li_test:
	asmgen &> src/test/c/src/test.S
	+@$(SUBMAKE) src/test/c/
	grouphex -f src/test/c/build/main.hex -g $(arg1) -o src/test/c/build/
	python3 src/test/python/cocotb/main.py --test tb_wood --top Wood --waves true --sim questa --dir ./test_run_dir/Wood_should_emit_for_cocotb/

.PHONY: clean
clean:
	-+@$(SUBMAKE) src/test/c/ clean
