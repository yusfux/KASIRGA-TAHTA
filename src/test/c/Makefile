rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

inst ?= addi

SRC_DIR = ./src
LINKER_SCRIPT ?= link.ld
BUILD_DIR = ./build

MARCH ?= rv32im
MABI ?= ilp32
FLAGS ?=  -Wno-overflow -nostartfiles -march=${MARCH} -mabi=${MABI} -T $(LINKER_SCRIPT)

# ASM_SOURCES = $(call rwildcard,$(SRC_DIR),$(inst)*.S)
# C_SOURCES = $(call rwildcard,$(SRC_DIR),$(inst)*.c)
# why above line do not find anything??
ASM_SOURCES = $(wildcard $(SRC_DIR)/$(inst)*.S)
C_SOURCES = $(wildcard $(SRC_DIR)/$(inst)*.c)

CC = $(RISCV_PREFIX)gcc
OBJCOPY = $(RISCV_PREFIX)objcopy
OBJDUMP = $(RISCV_PREFIX)objdump


.PHONY: all clean test

all: dump2 trace inject_questa_radix

.PHONY: build_dir
build_dir:
		mkdir -p ./build

.PHONY: compile
compile: build_dir
		$(CC) $(C_SOURCES) $(ASM_SOURCES) $(FLAGS) -I$(CSMITH_INCLUDE) -o $(BUILD_DIR)/$(inst)_main.elf
		$(OBJCOPY) $(BUILD_DIR)/$(inst)_main.elf -O binary $(BUILD_DIR)/$(inst)_main.bin
		$(OBJDUMP) -M numeric,no-aliases -D $(BUILD_DIR)/$(inst)_main.elf  > $(BUILD_DIR)/$(inst)_main.dump
		bin2hex      $(BUILD_DIR)/$(inst)_main.bin  > $(BUILD_DIR)/$(inst)_main.hex

.PHONY: dump2
dump2: compile
		dump2gtkw  $(BUILD_DIR)/$(inst)_main.dump > $(BUILD_DIR)/$(inst)_main.gtkw.map
		dump2vsim  $(BUILD_DIR)/$(inst)_main.dump > $(BUILD_DIR)/$(inst)_main.vsim.map

.PHONY: trace
trace: compile
		spike -m0x80000000:0x150000  --log-commits --isa=rv32gc -l $(BUILD_DIR)/$(inst)_main.elf &> $(BUILD_DIR)/$(inst)_spike.trace
		spiketrace2json -f $(BUILD_DIR)/$(inst)_spike.trace -o $(BUILD_DIR)/$(inst)_spike_trace.json

.PHONY: inject_questa_radix
inject_questa_radix: dump2
		cp  $(SRC_DIR)/exunit_wave.do $(BUILD_DIR)/exunit_wave.do
		cp  $(SRC_DIR)/wood_wave.do $(BUILD_DIR)/wood_wave.do
		sed -i '1r $(BUILD_DIR)/$(inst)_main.vsim.map' $(BUILD_DIR)/wood_wave.do
		sed -i '1r $(BUILD_DIR)/$(inst)_main.vsim.map' $(BUILD_DIR)/exunit_wave.do


clean:
	rm -rf $(BUILD_DIR)
	rm -rf FAILED
	rm -rf PASSED
	rm -rf UNKNOWN
