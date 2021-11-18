#!/bin/bash

for binary in /tmp/tornadoVM-spirv/*.spv;
do 
    echo "Validating:  $binary"
    spirv-val $binary
done