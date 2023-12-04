#!/usr/bin/env bash

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
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
