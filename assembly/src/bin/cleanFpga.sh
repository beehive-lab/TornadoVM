#!/bin/bash

vendor="$1"

## Back up current kernel 
mv fpga-source-comp/lookupBufferAddress.cl fpga-source-comp/backup_source.cl

## Move current genereted directory
mv fpga-source-comp/lookupBufferAddress fpga-source-comp/intelFPGAFiles

## Create sym link to the original kerenl 
cd fpga-source-comp/ 
if [ "$vendor" = "Intel(R) Corporation" ]; then
	ln -s lookupBufferAddress.aocx lookupBufferAddress && cd -
elif [ "$vendor" = "Xilinx" ]; then
	ln -s lookupBufferAddress.xclbin lookupBufferAddress && cd -
else
	echo "Unsupported FPGA vendor" && cd -
fi

