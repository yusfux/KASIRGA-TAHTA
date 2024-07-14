SUBMAKE := $(MAKE) --no-print-directory -C

# All args except for the first one (which is the target name)
ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))

.PHONY: all
all: 
	@echo "What are you expecting? (￣ー￣)";
	@echo "Read the makefile.";

.PHONY: sim
sim:
	+@$(SUBMAKE) verify/ $(ARGS)

.PHONY: clean
clean:
	-rm -rf ./build
	-rm -rf ./sim_build
	-+@$(SUBMAKE) verify/ clean

.PHONY: test
