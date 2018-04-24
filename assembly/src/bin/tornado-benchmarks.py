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
import textwrap

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

__ITERATIONS__ = " 10 "

__TORNADO__ = "tornado "


def printBenchmakrks():
    print "List of benchmarks: "
    wrapper = textwrap.TextWrapper(initial_indent="* ")

    for b in __BENCHMARKS__:
        print wrapper.fill(b)


def runForAllSizes():
    for s in __PROBLEM_SIZES__:
        for b in __BENCHMARKS__:
            command = __TORNADO__ + __RUNNER__ + b + __ITERATIONS__ + s
            os.system(command)


def runAllDevices():
    index = 0
    for d in __DEVICES__:
        print "Currently executing on device: device=0:", index
        for b in __BENCHMARKS__:
            command = __TORNADO__ + d + __RUNNER__ + b
            os.system(command)
            index += 1


def runBenchmarks():
    for b in __BENCHMARKS__:
        command = __TORNADO__ + __RUNNER__ + b
        os.system(command)


def parseArguments():
    parser = argparse.ArgumentParser(description='Tool to execute benchmarks in Tornado')
    parser.add_argument('--devices', "-D", action="store_true", dest="device", default=False, help="Run to all devices")
    parser.add_argument('--sizes', "-S", action="store_true", dest="size", default=False,
                        help="Run for all problem sizes")
    parser.add_argument('--benchmarks', "-BL", action="store_true", dest="bl", default=False,
                        help="Print list of benchmarks")
    args = parser.parse_args()
    return args


def main():
    args = parseArguments()

    if args.device:
        runAllDevices()
    elif args.size:
        runForAllSizes()
    elif args.bl:
        printBenchmakrks()
    else:
        runBenchmarks()


if __name__ == '__main__':
    main()
