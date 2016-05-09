#!/bin/bash

PACKAGE="tornado.benchmarks"
BENCHMARKS="sadd saxpy sgemm addvector dotvector rotatevector rotateimage convolvearray convolveimage"
MAIN_CLASS="Benchmark"

TORNADO_CMD="tornado"

LOGFILE="bm.log"

ITERATIONS=10

rm ${LOGFILE}

for bm in ${BENCHMARKS}; do
	for (( i=0; i<${ITERATIONS}; i++ )); do
		echo "running ${i} ${bm} ..."
		${TORNADO_CMD} ${PACKAGE}.${bm}.${MAIN_CLASS} >> ${LOGFILE}
	done
done
