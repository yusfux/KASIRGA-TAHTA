rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

SRC_DIR = ./src
LINKER_SCRIPT ?= link.ld
BUILD_DIR = ./build

MARCH ?= rv32im
MABI ?= ilp32
FLAGS ?= -nostartfiles -march=${MARCH} -mabi=${MABI} -T $(LINKER_SCRIPT)

ASM_SOURCES = $(call rwildcard,$(SRC_DIR),*.S)
C_SOURCES = $(call rwildcard,$(SRC_DIR),*.c)

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
		$(CC) $(C_SOURCES) $(ASM_SOURCES) $(FLAGS) -I$(CSMITH_INCLUDE) -o $(BUILD_DIR)/main.elf
		$(OBJCOPY) $(BUILD_DIR)/main.elf -O binary $(BUILD_DIR)/main.bin
		$(OBJDUMP) -M numeric,no-aliases -D $(BUILD_DIR)/main.elf  > $(BUILD_DIR)/main.dump
		bin2hex      $(BUILD_DIR)/main.bin  > $(BUILD_DIR)/main.hex

.PHONY: dump2
dump2: compile
		dump2gtkw  $(BUILD_DIR)/main.dump > $(BUILD_DIR)/main.gtkw.map
		dump2vsim  $(BUILD_DIR)/main.dump > $(BUILD_DIR)/main.vsim.map

.PHONY: trace
trace: compile
		spike -m0x80000000:0x200000  --log-commits --isa=rv32gc -l $(BUILD_DIR)/main.elf &> $(BUILD_DIR)/spike.trace
		spiketrace2json -f $(BUILD_DIR)/spike.trace -o $(BUILD_DIR)/spike_trace.json

.PHONY: inject_questa_radix
inject_questa_radix: dump2
		cp  $(SRC_DIR)/exunit_wave.do $(BUILD_DIR)/exunit_wave.do
		cp  $(SRC_DIR)/wood_wave.do $(BUILD_DIR)/wood_wave.do
		sed -i '1r $(BUILD_DIR)/main.vsim.map' $(BUILD_DIR)/wood_wave.do
		sed -i '1r $(BUILD_DIR)/main.vsim.map' $(BUILD_DIR)/exunit_wave.do


clean:
	rm -rf $(BUILD_DIR)
	rm -rf FAILED
	rm -rf PASSED
	rm -rf UNKNOWN
