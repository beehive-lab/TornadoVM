#!/bin/bash

mv fpga-source-comp/lookupBufferAddress.cl fpga-source-comp/backup_source.cl
rm -rf ${TORNADO_ROOT}/fpga-source-comp/lookupBufferAddress
mv fpga-source-comp/lookupBufferAddress.aocx fpga-source-comp/lookupBufferAddress


