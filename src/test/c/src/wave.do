onerror {resume}

quietly WaveActivateNextPane {} 0

# Remove all existing signals
delete wave *

# Define nWide parameter
set nWide 2
set rsDepth 2
set prfDepth 99999

################################
# EXUNITTOP
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/io_bpBus_%d*" $i]
  add wave -noupdate -group exunit -group io_bpBus_$i $sig_name
}

################################
# DESTAGE
add wave -noupdate -group exunit -group destage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/destage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group destage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/destage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group destage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/destage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group destage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/destage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group destage -group io_out_$i $sig_name
}

################################
# MISTAGE
add wave -noupdate -group exunit -group mistage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/mistage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group mistage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/mistage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group mistage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/mistage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group mistage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/mistage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group mistage -group io_out_$i $sig_name
}

################################
# RESTAGE
add wave -noupdate -group exunit -group restage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/restage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group restage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/restage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group restage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/restage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group restage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/restage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group restage -group io_out_$i $sig_name
}

################################
# SCSTAGE
add wave -noupdate -group exunit -group scstage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
    for {set j 0} {$j < $rsDepth} {incr j 1} {
        set sig_name [format "/ExUnit/scstage/reservationStations_%d/rows_%d/row_*" $i $j]
        add wave -noupdate -group exunit -group scstage -group reservationStations_$i -group rows_$j $sig_name
    }
}

for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/readyList_%d" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group readyList $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/io_commitedBus_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group io_commitedBus_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/io_forwardBus_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group io_forwardBus_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/io_wakeupBus_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group io_wakeupBus_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/io_in_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/readyList/io_out_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group readyList -group io_out_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group scstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group scstage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/scstage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group scstage -group io_out_$i $sig_name
}

################################
# RRSTAGE
add wave -noupdate -group exunit -group rrstage io_flush
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/prf_%d" $i]
  add wave -noupdate -group exunit -group rrstage -group prf $sig_name
}

for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/io_forwardBus_%d*" $i]
  add wave -noupdate -group exunit -group rrstage -group io_forwardBus_$i $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/io_writebackBus_%d*" $i]
  add wave -noupdate -group exunit -group rrstage -group io_writebackBus_$i $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/io_wakeupBus_%d*" $i]
  add wave -noupdate -group exunit -group rrstage -group io_wakeupBus_$i $sig_name
}


for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rrstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rrstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group rrstage -group io_in_$i $sig_name
}

################################
# EXSTAGE
add wave -noupdate -group exunit -group exstage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/alus_%d/io_out_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group alus_$i -group io_out $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/alus_%d/io_in_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group alus_$i -group io_in $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/imus_%d/io_out_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group imus_$i -group io_out $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/imus_%d/io_in_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group imus_$i -group io_in $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/idus_%d/io_out_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group idus_$i -group io_out $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/idus_%d/io_in_*" $i $i]
  add wave -noupdate -group exunit -group exstage -group idus_$i -group io_in $sig_name
}


for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group exstage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/exstage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group exstage -group io_out_$i $sig_name
}

################################
# WBSTAGE
add wave -noupdate -group exunit -group wbstage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/wbstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group wbstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/wbstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group wbstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/wbstage/io_writebackBus_%d*" $i]
  add wave -noupdate -group exunit -group wbstage -group io_writebackBus_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/wbstage/io_exceptionBus_%d*" $i]
  add wave -noupdate -group exunit -group wbstage -group io_exceptionBus_$i $sig_name
}

################################
# RBSTAGE
add wave -noupdate -group exunit -group rbstage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rbstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rbstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rbstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group rbstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rbstage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rbstage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rbstage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group rbstage -group io_out_$i $sig_name
}


################################
# RSSTAGE
add wave -noupdate -group exunit -group rsstage io_flush
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rsstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group rsstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/retireStatusRegisterFile_%d" $i]
  add wave -noupdate -group exunit -group rsstage -group retireStatusRegisterFile $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/exceptionStatusRegisterFile_%d" $i]
  add wave -noupdate -group exunit -group rsstage -group exceptionStatusRegisterFile $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/pcRegisterFile_%d" $i]
  add wave -noupdate -group exunit -group rsstage -group pcRegisterFile $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/takenStatusRegisterFile_%d" $i]
  add wave -noupdate -group exunit -group rsstage -group takenStatusRegisterFile $sig_name
}


for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rsstage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rsstage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group rsstage -group io_out_$i $sig_name
}

################################
# ARSTAGE
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/archRegisterFileValid_%d" $i]
  add wave -noupdate -group exunit -group arstage -group archRegisterFileValid $sig_name
}
for {set i 0} {$i < $prfDepth} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/archRegisterFile_%d" $i]
  add wave -noupdate -group exunit -group arstage -group archRegisterFile $sig_name
}


for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group arstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group arstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/io_out_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group arstage -group -radix RISCV io_out_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/arstage/io_out_%d*" $i]
  add wave -noupdate -group exunit -group arstage -group io_out_$i $sig_name
}

################################
# RWSTAGE
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rwstage/io_in_%d_bits_inst" $i]
  add wave -noupdate -group exunit -group rwstage -group -radix RISCV io_in_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rwstage/io_in_%d*" $i]
  add wave -noupdate -group exunit -group rwstage -group io_in_$i $sig_name
}

for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rwstage/io_arfBus_%d*" $i]
  add wave -noupdate -group exunit -group rwstage -group io_arfBus_$i $sig_name
}
for {set i 0} {$i < $nWide} {incr i 1} {
  set sig_name [format "/ExUnit/rwstage/io_commitedBus_%d*" $i]
  add wave -noupdate -group exunit -group rwstage -group io_commitedBus_$i $sig_name
}


# After all signals are added, zoom out to full view
wave zoom full

TreeUpdate [SetDefaultTree]
WaveRestoreCursors {{Cursor 1} {29 ns} 0}
quietly wave cursor active 1
configure wave -namecolwidth 232
configure wave -valuecolwidth 100
configure wave -justifyvalue left
configure wave -signalnamewidth 1
configure wave -snapdistance 10
configure wave -datasetprefix 0
configure wave -rowmargin 4
configure wave -childrowmargin 2
configure wave -gridoffset 0
configure wave -gridperiod 1
configure wave -griddelta 40
configure wave -timeline 0
configure wave -timelineunits us
update
