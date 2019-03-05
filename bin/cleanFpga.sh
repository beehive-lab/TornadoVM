#!/bin/bash

## Back up current kernel 
mv fpga-source-comp/lookupBufferAddress.cl fpga-source-comp/backup_source.cl

## Move current genereted directory
mv fpga-source-comp/lookupBufferAddress fpga-source-comp/intelFPGAFiles

## Create sym link to the original kerenl 
cd fpga-source-comp/ && ln -s lookupBufferAddress.aocx lookupBufferAddress && cd -

