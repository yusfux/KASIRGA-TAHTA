rwildcard=$(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

SRC_DIR = ./src
LINKER_SCRIPT ?= link.ld
BUILD_DIR = ./build

MARCH ?= rv32i
MABI ?= ilp32
ASMFLAGS ?= -nostartfiles -nostdlib -march=${MARCH} -mabi=${MABI} -T $(LINKER_SCRIPT)

CC = $(RISCV_PREFIX)gcc
OBJCOPY = $(RISCV_PREFIX)objcopy
OBJDUMP = $(RISCV_PREFIX)objdump

.PHONY: all clean asm test

all: src/main.S

.PHONY: src/main.S
src/main.S:
		mkdir -p ./build
		$(CC) $@ $(ASMFLAGS) -o $(BUILD_DIR)/$(notdir $(basename $@)).elf
		$(OBJCOPY) $(BUILD_DIR)/$(notdir $(basename $@)).elf -O binary $(BUILD_DIR)/$(notdir $(basename $@)).bin
		$(OBJDUMP) -M numeric,no-aliases -D $(BUILD_DIR)/$(notdir $(basename $@)).elf  > $(BUILD_DIR)/$(notdir $(basename $@)).dump
		python3 ../python/bin2hex.py        $(BUILD_DIR)/$(notdir $(basename $@)).bin  > $(BUILD_DIR)/$(notdir $(basename $@)).hex
		python3 ../python/dump2filter.py    $(BUILD_DIR)/$(notdir $(basename $@)).dump > $(BUILD_DIR)/$(notdir $(basename $@)).map

clean:
	rm -rf $(BUILD_DIR)
	rm -rf FAILED
	rm -rf PASSED
	rm -rf UNKNOWN
