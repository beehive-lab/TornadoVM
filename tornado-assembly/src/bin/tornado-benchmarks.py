#!/usr/bin/env python3

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2013-2022, APT Group, Department of Computer Science,
# Department of Engineering, The University of Manchester. All rights reserved.
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
#

import argparse
import os
import subprocess
import textwrap
import sys

try:
    __JAVA_HOME__ = os.environ["JAVA_HOME"]
except:
    print("[ERROR] JAVA_HOME is not defined")
    sys.exit(0)


class Colors:
    RED = "\033[1;31m"
    BLUE = "\033[1;34m"
    CYAN = "\033[1;36m"
    GREEN = "\033[0;32m"
    RESET = "\033[0;0m"
    BOLD = "\033[;1m"
    REVERSE = "\033[;7m"


JDK_8_VERSION = "1.8"
# Get java version
__JAVA_VERSION__ = (
    subprocess.Popen(
        __JAVA_HOME__
        + "/bin/java -version 2>&1 | awk -F[\\\"\.] -v OFS=. 'NR==1{print $2,$3}'",
        stdout=subprocess.PIPE,
        shell=True,
    )
    .communicate()[0]
    .decode("utf-8")[:-1]
)

## ========================================================================================
## TornadoVM/JVM Options
## ========================================================================================
__RUNNER__ = ""
if __JAVA_VERSION__ != JDK_8_VERSION:
    __RUNNER__ = " -m tornado.benchmarks/"
__RUNNER__ += "uk.ac.manchester.tornado.benchmarks.BenchmarkRunner "
__JVM_FLAGS__ = "-Xms24G -Xmx24G -server -Dtornado.recover.bailout=False "
__TORNADO_COMMAND__ = "tornado "
__SKIP_SERIAL__ = " -Dtornado.benchmarks.skipserial=True "
__SKIP_PARALLEL__ = " -Dtornado.enable=False "
__SKIP_DEVICES__ = " -Dtornado.blacklist.devices="
__VALIDATE__ = " -Dtornado.benchmarks.validate=True "
__ENABLE_PROFILER_SILENT_MODE__ = " --enableProfiler silent "
__DISABLE_LEVEL_ZERO_DEFAULT_SCHEDULER__ = (
    " -Dtornado.spirv.levelzero.thread.dispatcher=False "
)
__ENABLE_SPIRV_OPTIMIZER__ = " -Dtornado.spirv.loadstore=True "
## ========================================================================================

## ========================================================================================
## Include here benchmarks to run
## ========================================================================================
__BENCHMARKS__ = [
    "saxpy",
    "addImage",
    "stencil",
    "convolvearray",
    "convolveimage",
    "blackscholes",
    "montecarlo",
    "blurFilter",
    "renderTrack",
    "euler",
    "nbody",
    "sgemm",
    "dgemm",
    "mandelbrot",
    "dft",
    "juliaset",
]
## ========================================================================================


def getSize():
    return ITERATIONS


## Hashmap with sizes
__MAX_ITERATIONS__ = 131
ITERATIONS = __MAX_ITERATIONS__
allSizes = {
    "montecarlo": [
        [512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576],
        [__MAX_ITERATIONS__],
    ],
    "nbody": [[512, 1024, 2048, 4096, 16384, 327684], [__MAX_ITERATIONS__]],
    "saxpy": [
        [
            512,
            1024,
            2048,
            4096,
            8192,
            16384,
            32798,
            65536,
            131072,
            262144,
            524288,
            1048576,
            2097152,
            4194304,
        ],
        [__MAX_ITERATIONS__],
    ],
    "sgemm": [[128, 256, 512, 1024, 2048], [__MAX_ITERATIONS__]],
    "blackscholes": [
        [512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304],
        [__MAX_ITERATIONS__],
    ],
    "dft": [[256, 512, 1024, 2048, 4096, 8192], [__MAX_ITERATIONS__]],
    "blurFilter": [[256, 512, 1024, 2048, 8192, 16384], [__MAX_ITERATIONS__]],
    "juliaset": [[512, 1024, 2048, 4096, 8192], [__MAX_ITERATIONS__]],
}

mediumSizes = {
    ## Benchmark: [ [SIZES], [functionCALL] ]
    "montecarlo": [[512, 1024, 2048, 4096, 8192], ["getSize()"]],
    "nbody": [[512, 1024, 2048, 4096], ["getSize()"]],
    "saxpy": [
        [
            512,
            1024,
            2048,
            4096,
            8192,
            16384,
            32798,
            65536,
            131072,
            262144,
            524288,
            1048576,
            2097152,
        ],
        ["getSize()"],
    ],
    "sgemm": [[128, 256, 512, 1024, 2048], ["getSize()"]],
    "blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536], ["getSize()"]],
    "dft": [[256, 512, 1024, 2048, 4096], ["getSize()"]],
    "blurFilter": [[256, 512, 1024, 2048], ["getSize()"]],
    "juliaset": [[512, 1024, 2048, 4096], ["getSize()"]],
}

## ========================================================================================
def composeAllOptions(args):
    options = __JVM_FLAGS__
    if args.skip_serial:
        options = options + __SKIP_SERIAL__
    if args.skip_parallel:
        options = options + __SKIP_PARALLEL__
    if args.validate:
        options = options + __VALIDATE__
    if args.skip_devices != None:
        options = options + __SKIP_DEVICES__ + args.skip_devices + " "
    if args.profiler:
        options = options + __ENABLE_PROFILER_SILENT_MODE__
    if args.jvmFlags != None:
        options = options + args.jvmFlags
    if args.tornadoThreadScheduler == True:
        options = options + __DISABLE_LEVEL_ZERO_DEFAULT_SCHEDULER__
    if args.spirvOptimizer:
        options = options + __ENABLE_SPIRV_OPTIMIZER__
    return options


def printBenchmarks(indent=""):
    print(Colors.GREEN + indent + "List of benchmarks: " + Colors.RESET)
    for b in __BENCHMARKS__:
        print(Colors.BOLD + indent + "\t*" + b + Colors.RESET)


def runBenchmarksFullCoverage(args):
    options = composeAllOptions(args)
    for key in allSizes.keys():
        for size in allSizes[key][0]:
            command = (
                __TORNADO_COMMAND__
                + ' --jvm="'
                + options
                + '" '
                + __RUNNER__
                + ' --params="'
                + key
                + " "
                + str(allSizes[key][1][0])
                + " "
                + str(size)
            )
            if key == "sgemm":
                command = command + " " + str(size)
            command += '"'
            print(command)
            os.system(command)


def runMediumConfiguration(args):
    options = composeAllOptions(args)
    print(options)
    for key in mediumSizes.keys():
        for size in mediumSizes[key][0]:
            numIterations = eval(mediumSizes[key][1][0])
            command = (
                __TORNADO_COMMAND__
                + ' --jvm="'
                + options
                + '" '
                + __RUNNER__
                + ' --params="'
                + key
                + " "
                + str(numIterations)
                + " "
                + str(size)
            )
            if key == "sgemm":
                command = command + " " + str(size)
            command += '"'
            print(command)
            os.system(command)


def runWithJMH(args):
    printBenchmarks()
    options = composeAllOptions(args)
    print(Colors.CYAN + "[INFO] TornadoVM options: " + options + Colors.RESET)
    command = __TORNADO_COMMAND__ + " -jar benchmarks/target/jmhbenchmarks.jar "
    print(command)
    os.system(command)


def runDefaultSizePerBenchmark(args):
    printBenchmarks()
    options = composeAllOptions(args)
    print(Colors.CYAN + "[INFO] TornadoVM options: " + options + Colors.RESET)
    for b in __BENCHMARKS__:
        command = (
            __TORNADO_COMMAND__
            + '--jvm="'
            + options
            + '" '
            + __RUNNER__
            + '--params="'
            + b
            + '"'
        )
        print(command)
        os.system(command)


def parseArguments():
    parser = argparse.ArgumentParser(
        description="""Tool to execute benchmarks in TornadoVM. With no options, it runs all benchmarks with the default size"""
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        dest="validate",
        default=False,
        help="Enable result validation",
    )
    parser.add_argument(
        "--medium",
        action="store_true",
        dest="medium",
        default=False,
        help="Run benchmarks with medium sizes",
    )
    parser.add_argument(
        "--iterations",
        action="store",
        type=int,
        dest="iterations",
        default=0,
        help="Set the number of iterations",
    )
    parser.add_argument(
        "--full",
        action="store_true",
        dest="full",
        default=False,
        help="Run for all sizes in all devices. Including big data sizes",
    )
    parser.add_argument(
        "--skipSequential",
        action="store_true",
        dest="skip_serial",
        default=False,
        help="Skip java version",
    )
    parser.add_argument(
        "--skipParallel",
        action="store_true",
        dest="skip_parallel",
        default=False,
        help="Skip parallel version",
    )
    parser.add_argument(
        "--skipDevices",
        action="store",
        dest="skip_devices",
        default=None,
        help="Skip devices. Provide a list of devices (e.g., 0,1)",
    )
    parser.add_argument(
        "--printBenchmarks",
        action="store_true",
        dest="benchmarks",
        default=False,
        help="Print the list of available benchmarks",
    )
    parser.add_argument(
        "--profiler",
        action="store_true",
        dest="profiler",
        default=False,
        help="Run Benchmarks with the OpenCL|PTX|SPIRV profiler",
    )
    parser.add_argument(
        "--jmh", action="store_true", dest="jmh", default=False, help="Run with JMH"
    )
    parser.add_argument(
        "--jvm",
        "-J",
        dest="jvmFlags",
        required=False,
        default=None,
        help='Pass options to the JVM e.g. -J="-Ds0.t0.device=0:1"',
    )
    parser.add_argument(
        "--tornadoThreadScheduler",
        action="store_true",
        dest="tornadoThreadScheduler",
        required=False,
        default=False,
        help="Use the thread scheduler provided with TornadoVM when running SPIRV",
    )
    parser.add_argument(
        "--spirvOptimizer",
        action="store_true",
        dest="spirvOptimizer",
        default=False,
        help="Enable the SPIRV optimizer",
    )
    args = parser.parse_args()
    return args


def main():
    args = parseArguments()
    global ITERATIONS
    if args.iterations > 0:
        ITERATIONS = args.iterations

    if args.benchmarks:
        printBenchmarks()
    elif args.full:
        runBenchmarksFullCoverage(args)
    elif args.medium:
        print("[INFO] Running small and medium sizes")
        runMediumConfiguration(args)
    elif args.jmh:
        print("[INFO] Running default size with JMH")
        runWithJMH(args)
    else:
        print(Colors.BLUE + "Running TornadoVM Benchmarks" + Colors.RESET)
        print(
            Colors.CYAN
            + "[INFO] This process takes between 30-60 minutes"
            + Colors.RESET
        )
        runDefaultSizePerBenchmark(args)


if __name__ == "__main__":
    main()
