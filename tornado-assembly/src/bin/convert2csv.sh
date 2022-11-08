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
