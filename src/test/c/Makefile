rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

SRC_DIR = ./src
LINKER_SCRIPT ?= link.ld
BUILD_DIR = ./build

MARCH ?= rv32i
MABI ?= ilp32
FLAGS ?= -nostartfiles -march=${MARCH} -mabi=${MABI} -T $(LINKER_SCRIPT)

ASM_SOURCES = $(call rwildcard,$(SRC_DIR),*.S)
C_SOURCES = $(call rwildcard,$(SRC_DIR),*.c)

CC = $(RISCV_PREFIX)gcc
OBJCOPY = $(RISCV_PREFIX)objcopy
OBJDUMP = $(RISCV_PREFIX)objdump

.PHONY: all clean test

all: dump2

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

clean:
	rm -rf $(BUILD_DIR)
	rm -rf FAILED
	rm -rf PASSED
	rm -rf UNKNOWN
