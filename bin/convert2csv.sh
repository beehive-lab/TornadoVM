#!/bin/bash

echo "converting ${1} to ${2}..."

FIELDS="bm= id= elapsed= per iteration= speedup= overhead="

echo "benchmark, device, total, iteration, speedup, overhead" > ${2}

grep -v "benchmark" ${1} >> ${2}

for field in ${FIELDS}; do

	perl -pi -e "s/${field}//g" ${2}

done
