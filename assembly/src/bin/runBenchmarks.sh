#!/usr/bin/env bash

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2013-2020, APT Group, Department of Computer Science,
# The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Authors: James Clarkson
#

PACKAGE="uk.ac.manchester.tornado.benchmarks"
BENCHMARKS="sadd saxpy sgemm dgemm spmv addvector dotvector rotatevector rotateimage convolvearray convolveimage montecarlo"
MAIN_CLASS="Benchmark"

TORNADO_CMD="tornado"

if [ -z "${TORNADO_BM_FLAGS}" ]; then
	TORNADO_BM_FLAGS="-Xms8G -server -Dtornado.kernels.coarsener=False -Dtornado.profiles.print=True -Dtornado.profiling.enable=True -Dtornado.opencl.schedule=True"
fi

TORNADO_FLAGS="${TORNADO_FLAGS} ${TORNADO_BM_FLAGS}"

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

${TORNADO_CMD} -Xms8G -Ddevices=${DEVICES} -Dstartsize=2 -Dendsize=16777216 uk.ac.manchester.tornado.benchmarks.DataMovement > "${BM_ROOT}/data-movement.csv"

for bm in ${BENCHMARKS}; do
	for (( i=0; i<${ITERATIONS}; i++ )); do
		echo "running ${i} ${bm} ..."
		OUTFILE="${LOGFILE}-${bm}-${i}.log"
		${TORNADO_CMD} ${TORNADO_FLAGS} -Ddevices=${DEVICES} uk.ac.manchester.tornado.benchmarks.BenchmarkRunner ${bm} >> "${OUTFILE}"
		${TORNADO_ROOT}assembly/src/bin/convert2csv.sh ${OUTFILE}
	done
done

