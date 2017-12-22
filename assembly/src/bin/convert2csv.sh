#!/bin/bash

BASE=$(basename ${1} | cut -f1 -d.)
DIR=$(dirname ${1})
echo "converting ${1} to ${BASE}.csv ..."

FIELDS="bm= id= device= elapsed= per iteration= speedup= overhead="

OUTPUT="${DIR}/${BASE}.csv"
echo "benchmark, device, elapsed, iteration, speedup, overhead" > ${OUTPUT}
grep "bm=" ${1} >> "${OUTPUT}"

for field in ${FIELDS}; do
        perl -pi -e "s/${field}//g" ${OUTPUT}
done

KERNELS="${DIR}/${BASE}-kernels.csv"
echo "device kernel time submit start end" > ${KERNELS}
grep "task:" ${1} >> ${KERNELS}

perl -pi -e "s/task:\s+//g" ${KERNELS}
perl -pi -e "s/ /,/g" ${KERNELS}