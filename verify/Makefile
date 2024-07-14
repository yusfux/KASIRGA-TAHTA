# All args except for the first one (which is the target name)
ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))

# arg1 stands for the first argument
arg1 := $(firstword $(ARGS))

# default simulator
ifeq ($(arg1),)
	arg1 := icarus
endif

.PHONY: all
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: alu
alu:
	python3 ./main.py --sim $(arg1) --test test_alu --top alu --waves true

.PHONY: rf
rf:
	python3 ./main.py --sim $(arg1) --test test_rf --top rf_p_2r2w --waves true
 
.PHONY: clean
clean:
	@echo "Cleaning sim outputs";
	-rm -rf ./sim_build

.PHONY: test
