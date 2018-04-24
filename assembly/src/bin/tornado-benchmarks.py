#!/usr/bin/env python

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
# Authors: Michalis Papadimitriou
#

import argparse
import os

## Include here benchmarks to run
__BENCHMARKS__ = [
    "montecarlo",
    "nbody",
    "saxpy",
    "sgemm",
    "scopy",
    "blackscholes"
]

__PROBLEM_SIZES__ = [
    "1024",
    "2048",
    "4096",
    "10240",
    "40960",
    "102400",
]

## Options
__TORNADO_BM_FLAGS__ = "-Xms8G -server -Dtornado.kernels.coarsener=False -Dtornado.profiles.print=True -Dtornado.profiling.enable=True -Dtornado.opencl.schedule=True"

__RUNNER__ = " uk.ac.manchester.tornado.benchmarks.BenchmarkRunner "

__DEVICES__ = ["-Ddevice=0:0",
               "-Ddevice=0:1",
               ]

cmd = "tornado "


def runalldevices():
    index = 0
    for d in __DEVICES__:
        print "Currenly executing on device: device=0:", index
        for b in __BENCHMARKS__:
            command = cmd + d + __RUNNER__ + b
            os.system(command)
            index += 1


def runbenchmarks():
    for b in __BENCHMARKS__:
        command = cmd + __RUNNER__ + b
        os.system(command)


def parseArguments():
    parser = argparse.ArgumentParser(description='Tool to execute benchmarks in Tornado')
    parser.add_argument('--devices', action="store_true", dest="dev", default=False, help="Run to all devices")
    parser.add_argument('--sizes', "-S", action="store_true", dest="siz", default=False,
                        help="Run for all problem sizes")
    args = parser.parse_args()
    return args


def main():
    args = parseArguments()

    if args.dev:
        runalldevices()
    else:
        runbenchmarks()


if __name__ == '__main__':
    main()
