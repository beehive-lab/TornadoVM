#!/usr/bin/env python3

#
# Copyright (c) 2013-2025, APT Group, Department of Computer Science,
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

import argparse
import os
import subprocess
import sys
import time

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
__SKIP_TORNADOVM__ = " -Dtornado.benchmarks.skiptornadovm=True "
__SKIP_SERIAL__ = " -Dtornado.benchmarks.skipserial=True "
__ENERGY_MONITOR_INTERVAL__ = " -Denergy.monitor.interval="
__DUMP_ENERGY_METRICS_TO_DIRECTORY__ = " -Ddump.energy.metrics.to.directory="
__SKIP_DEVICES__ = " -Dtornado.blacklist.devices="
__VALIDATE__ = " -Dtornado.benchmarks.validate=True "
__ENABLE_PROFILER__ = " --enableProfiler "
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
## Dimensions
## ========================================================================================
__DIMENSIONS__ = {
    "saxpy": "1",
    "addImage": "2",
    "stencil": "1",
    "convolvearray": "2",
    "convolveimage": "2",
    "blackscholes": "1",
    "montecarlo": "1",
    "blurFilter": "2",
    "renderTrack": "2",
    "euler": "2",
    "nbody": "1",
    "sgemm": "2",
    "dgemm": "2",
    "mandelbrot": "2",
    "dft": "1",
    "juliaset": "2"
}


## ========================================================================================


def getSize():
    return ITERATIONS


## Hashmap with sizes
__MAX_ITERATIONS__ = 131
ITERATIONS = __MAX_ITERATIONS__
allSizes = {
    "montecarlo": [
        [512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 1048576],
        ["getSize()"],
    ],
    "nbody": [[512, 1024, 2048, 4096, 16384, 32768], ["getSize()"]],
    "saxpy": [
        [
            512,
            1024,
            2048,
            4096,
            8192,
            16384,
            32768,
            65536,
            131072,
            262144,
            524288,
            1048576,
            2097152,
            4194304,
        ],
        ["getSize()"],
    ],
    "sgemm": [[128, 256, 512, 1024, 2048], ["getSize()"]],
    "blackscholes": [
        [512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 1048576, 4194304],
        ["getSize()"],
    ],
    "dft": [[256, 512, 1024, 2048, 4096, 8192], ["getSize()"]],
    "blurFilter": [[256, 512, 1024, 2048, 8192, 16384], ["getSize()"]],
    "juliaset": [[512, 1024, 2048, 4096, 8192], ["getSize()"]],
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
            32768,
            65536,
            131072,
            262144,
            524288,
            1048576,
            2097152,
        ],
        ["getSize()"],
    ],
    "sgemm": [[128, 256, 512, 1024], ["getSize()"]],
    "blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32768, 65536], ["getSize()"]],
    "dft": [[256, 512, 1024, 2048, 4096], ["getSize()"]],
    "blurFilter": [[256, 512, 1024, 2048], ["getSize()"]],
    "juliaset": [[512, 1024, 2048, 4096], ["getSize()"]],
}


## ========================================================================================
def composeAllOptions(args):
    jvm_options = __JVM_FLAGS__
    tornado_options = ""
    if args.skip_tornadovm:
        jvm_options = jvm_options + __SKIP_TORNADOVM__ + " "
    if args.skip_serial:
        jvm_options = jvm_options + __SKIP_SERIAL__ + " "
    if args.delay_energy_interval:
        jvm_options = jvm_options + __ENERGY_MONITOR_INTERVAL__ + str(args.delay_energy_interval) + " "
    if args.dump_energy_table_dir:
        jvm_options = jvm_options + __DUMP_ENERGY_METRICS_TO_DIRECTORY__ + args.dump_energy_table_dir + " "
    if args.validate:
        jvm_options = jvm_options + __VALIDATE__ + " "
    if args.skip_devices != None:
        jvm_options = jvm_options + __SKIP_DEVICES__ + args.skip_devices + " "
    if args.profiler:
        tornado_options = tornado_options + __ENABLE_PROFILER__ + args.profiler + " "
    if args.jvmFlags != None:
        jvm_options = jvm_options + args.jvmFlags + " "
    if args.tornadoThreadScheduler == True:
        jvm_options = jvm_options + __DISABLE_LEVEL_ZERO_DEFAULT_SCHEDULER__ + " "
    if args.spirvOptimizer:
        jvm_options = jvm_options + __ENABLE_SPIRV_OPTIMIZER__ + " "
    return jvm_options, tornado_options


def printBenchmarks(indent=""):
    print(Colors.GREEN + indent + "List of benchmarks: " + Colors.RESET)
    for b in __BENCHMARKS__:
        print(Colors.BOLD + indent + "\t*" + b + Colors.RESET)


def runBenchmarksFullCoverage(args):
    if args.benchmark and args.benchmark not in allSizes:
        print(f"Error: '{args.benchmark}' does not match a valid key in allSizes. Please provide a valid benchmark.")
        return
    jvm_options, tornado_options = composeAllOptions(args)
    for key in allSizes.keys():
        if args.benchmark and key != args.benchmark:
            continue
        for size in allSizes[key][0]:
            numIterations = eval(allSizes[key][1][0])
            command = (
                    __TORNADO_COMMAND__
                    + tornado_options
                    + ' --jvm="'
                    + jvm_options
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
            time.sleep(args.delay_interval)


def runMediumConfiguration(args):
    if args.benchmark and args.benchmark not in allSizes:
        print(f"Error: '{args.benchmark}' does not match a valid key in allSizes. Please provide a valid benchmark.")
        return
    jvm_options, tornado_options = composeAllOptions(args)
    print(tornado_options, jvm_options)
    for key in mediumSizes.keys():
        if args.benchmark and key != args.benchmark:
            continue
        for size in mediumSizes[key][0]:
            numIterations = eval(mediumSizes[key][1][0])
            command = (
                    __TORNADO_COMMAND__
                    + tornado_options
                    + ' --jvm="'
                    + jvm_options
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
            time.sleep(args.delay_interval)


def runWithJMH(args):
    printBenchmarks()
    jvm_options, tornado_options = composeAllOptions(args)
    print(Colors.CYAN + "[INFO] TornadoVM options: " + tornado_options +
          jvm_options + Colors.RESET)
    command = __TORNADO_COMMAND__ + tornado_options + " -jar tornado-benchmarks/target/jmhbenchmarks.jar "
    print(command)
    os.system(command)


def runDefaultSizePerBenchmark(args):
    if args.benchmark and args.benchmark not in allSizes:
        print(f"Error: '{args.benchmark}' does not match a valid key in allSizes. Please provide a valid benchmark.")
        return
    printBenchmarks()
    jvm_options, tornado_options = composeAllOptions(args)
    print(Colors.CYAN + "[INFO] TornadoVM options: " + tornado_options +
          jvm_options + Colors.RESET)
    for b in __BENCHMARKS__:
        if args.benchmark and b != args.benchmark:
            continue
        command = (
                __TORNADO_COMMAND__
                + tornado_options
                + '--jvm="'
                + jvm_options
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
        "--skipTornadoVM",
        action="store_true",
        dest="skip_tornadovm",
        default=False,
        help="Skip TornadoVM parallel implementations",
    )
    parser.add_argument(
        "--skipSerial",
        action="store_true",
        dest="skip_serial",
        default=False,
        help="Skip java version",
    )
    parser.add_argument(
        "--skipDevices",
        action="store",
        dest="skip_devices",
        default=None,
        help="Skip devices. Provide a list of devices (e.g., 0,1)",
    )
    parser.add_argument(
        "--benchmark",
        action="store",
        dest="benchmark",
        default=None,
        help="Run a specific BENCHMARK, as defined in the argument list",
    )
    parser.add_argument(
        "--printBenchmarks",
        action="store_true",
        dest="print_benchmarks",
        default=False,
        help="Print the list of available benchmarks",
    )
    parser.add_argument(
        "--profiler",
        action="store",
        dest="profiler",
        required=False,
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
    parser.add_argument(
        "--properties",
        action="store_true",
        dest="properties",
        default=False,
        help="Print dimensions and sizes for all benchmarks",
    )
    parser.add_argument(
        "--delayInterval",
        type=float,
        dest="delay_interval",
        default=0.0,
        help="Time interval (in seconds) to wait between execution of benchmarks. Default is 0 seconds.",
    )
    parser.add_argument(
        "--delayEnergyInterval",
        type=int,
        dest="delay_energy_interval",
        default=0,
        help="Time interval (in milliseconds) for the thread that monitors energy to sleep. Default is 0 milliseconds.",
    )
    parser.add_argument(
        "--dumpEnergyTable",
        action="store",
        dest="dump_energy_table_dir",
        default=None,
        help="Store the energy metric table in a specific directory",
    )

    args = parser.parse_args()
    return args


def printProperties():
    for benchmark, sizes in allSizes.items():
        dims = __DIMENSIONS__.get(benchmark, "-1")  # get dimension, -1 by default
        for size in sizes[0]:
            print(f"{benchmark}, dims={dims}, size={size}")


def main():
    args = parseArguments()
    global ITERATIONS
    if args.iterations > 0:
        ITERATIONS = args.iterations

    if args.print_benchmarks:
        printBenchmarks()
    elif args.full:
        runBenchmarksFullCoverage(args)
    elif args.medium:
        print("[INFO] Running small and medium sizes")
        runMediumConfiguration(args)
    elif args.jmh:
        print("[INFO] Running default size with JMH")
        runWithJMH(args)
    elif args.properties:
        printProperties()
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
