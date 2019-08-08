#!/usr/bin/env bash

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornado
#
# Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

TORNADO_DEVICE=0:2
TORNADO_FLAGS="-Ds0.device=${TORNADO_DEVICE} -Dtornado.profiles.print=True -Dtornado.profiling.enable=True -Dtornado.debug.compiletimes=True"

PROBLEM_SIZES="128 256 512 1024 2048 4096 8192 16384 32768 65536 131072 262144 524288 1048576 2097152 4194304 8388608 16777216"

PROBLEM_ITERATIONS="1 10 100"

TORNADO_CMD="tornado"
DATE=$(date '+%Y-%m-%d-%H:%M')

RESULTS_ROOT="./results"
BM_ROOT="${RESULTS_ROOT}/${DATE}"

if [ ! -d ${BM_ROOT} ]; then
  mkdir -p ${BM_ROOT}
fi

LOGFILE="${BM_ROOT}/bm"

ITERATIONS=10

if [ ! -z "${TORNADO_FLAGS}" ];then
  echo ${TORNADO_FLAGS} > "${BM_ROOT}/tornado.flags"
fi

if [ ! -z "${TORNADO_DEVICE}" ];then
  echo ${TORNADO_DEVICE} > "${BM_ROOT}/tornado.device"
fi

echo $(git rev-parse HEAD) > "${BM_ROOT}/git.sha"

bm="$1"

for SIZE in ${PROBLEM_SIZES}; do
	for ITER in ${PROBLEM_ITERATIONS}; do
		for (( i=0; i<${ITERATIONS}; i++ )); do
                	echo "running ${bm} ${SIZE} ${i}  ..."
                	${TORNADO_CMD} -Diterations=${ITER} -DuseTornado=False ${bm} ${SIZE}  >> "${LOGFILE}-${bm}-nt-${SIZE}-${ITER}-${i}.log"
                	${TORNADO_CMD} ${TORNADO_FLAGS} -Diterations=${ITER} -DuseTornado=True ${bm} ${SIZE}  >> "${LOGFILE}-${bm}-t-${SIZE}-${ITER}-${i}.log"
                	${TORNADO_CMD} ${TORNADO_FLAGS} -Diterations=${ITER} -DuseTornado=True -Dwarmup=True ${bm} ${SIZE}  >> "${LOGFILE}-${bm}-tw-${SIZE}-${ITER}-${i}.log"
        	done
	done
done
