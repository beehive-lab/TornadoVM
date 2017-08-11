#!/bin/bash

BENCHMARKS="scopy striad sadd saxpy sgemv sgemm spmv"

TORNADO_CMD="tornado"

#-Dbenchmark.streamin=False -Dbenchmark.streamout=False -Dbenchmark.warmup=True -Dtornado.opencl.schedule=True
BASE_BM_FLAGS="-Dtornado.opencl.eventwindow=10240 -Dtornado.profiling.enable=True -Dbenchmark.profiles.print=True"

if [ -z "${TORNADO_BM_FLAGS}" ]; then
	TORNADO_BM_FLAGS="-Xms8G -Dbenchmark.streamin=False -Dbenchmark.streamout=False"
fi

TORNADO_FLAGS="${TORNADO_FLAGS} ${BASE_BM_FLAGS} ${TORNADO_BM_FLAGS}"

DATE=$(date '+%Y-%m-%d-%H:%M')

RESULTS_ROOT="${TORNADO_ROOT}/var/results"
BM_ROOT="${RESULTS_ROOT}/${DATE}"

if [ -z "${DEVICES}" ]; then
	echo "Please set env variable DEVICES."
	echo "	e.g. DEVICES=0:0,0:1"
	exit
fi

if [ ! -d ${BM_ROOT} ]; then
  mkdir -p ${BM_ROOT}
fi

LOGFILE="${BM_ROOT}/bm"

ITERATIONS=10

if [ ! -z "${TORNADO_FLAGS}" ];then
  echo ${TORNADO_FLAGS} > "${BM_ROOT}/tornado.flags"
fi

if [ -e ${TORNADO_ROOT}/.git ]; then
	echo $(git rev-parse HEAD) > "${BM_ROOT}/git.sha"
fi

${TORNADO_CMD} -Xms8G -Ddevices=${DEVICES} -Dstartsize=2 -Dendsize=16777216 tornado.benchmarks.DataMovement > "${BM_ROOT}/data-movement.csv"

for bm in ${BENCHMARKS}; do
	for (( i=0; i<${ITERATIONS}; i++ )); do
		echo "running ${i} ${bm} ..."
		OUTFILE="${LOGFILE}-${bm}-${i}.log"
		${TORNADO_CMD} ${TORNADO_FLAGS} -Ddevices=${DEVICES} tornado.benchmarks.BenchmarkRunner ${bm} >> "${OUTFILE}"
		${TORNADO_ROOT}/bin/convert2csv.sh ${OUTFILE}
	done
done

