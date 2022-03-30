#!/usr/bin/env bash
#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2022, APT Group, Department of Computer Science,
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

day=`date "+%d_%m_%Y_%T"`
echo $day

directory="benchmarks_results"

mkdir -p $directory

backend=`tornado --version | grep backends`

# Run with Profiler and Optimizations Disabled
echo "tornado-benchmarks.py --profiler > ${directory}/SPIRV_BENCHMARKS_PROFILER_NOOPT_${day}.log"
tornado-benchmarks.py --profiler > ${directory}/SPIRV_BENCHMARKS_PROFILER_NOOPT_${day}.log


if [[ $backend == "backends=spirv" ]]
then
	# Run with Profiler and Optimizations Enabled
	echo "tornado-benchmarks.py --profiler --spirvOptimizer > ${directory}/SPIRV_BENCHMARKS_PROFILER_${day}.log"
	tornado-benchmarks.py --profiler --spirvOptimizer > ${directory}/SPIRV_BENCHMARKS_PROFILER_${day}.log
fi

## Run end-to-end and Optimizations Disabled
echo "tornado-benchmarks.py > ${directory}/SPIRV_BENCHMARKS_END2END_NOOPT_${day}.log"
tornado-benchmarks.py > ${directory}/SPIRV_BENCHMARKS_END2END_NOOPT_${day}.log

if [[ $backend == "backends=spirv" ]]
then
	## Run end-to-end and Optimizations enabled
	echo "tornado-benchmarks.py  --spirvOptimizer > ${directory}/SPIRV_BENCHMARKS_END2END_${day}.log"
	tornado-benchmarks.py  --spirvOptimizer > ${directory}/SPIRV_BENCHMARKS_END2END_${day}.log
fi

