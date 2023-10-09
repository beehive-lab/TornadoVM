#!/usr/bin/env python3
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
import os
import re
import subprocess
from datetime import datetime


def runBenchmarks(filename, command):
    with open(filename, "w") as fout:
        proc = subprocess.Popen(command, stdout=fout, shell=False)
        return_code = proc.wait()


def main():
    date = datetime.today().strftime("%d-%m-%Y-%T")

    directory = "benchmarks_results"
    try:
        os.mkdir(directory)
    except:
        print("Directory already created")

    ## Obtain the list of backends installed
    command = "tornado -- version"
    process = subprocess.Popen(
        ["tornado", "--version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    output = str(process.stdout.read())
    m = re.search(r"backends=(\w+(,\w+)?)", output)
    match = m.group(0)

    ## Obtain if SPIR-V backend is installed
    m2 = re.search("spirv", match)
    spirvEnabled = False
    if m2 != None:
        spirvEnabled = True

    print("Is SPIRV Backend present? " + str(spirvEnabled))
    print("Current Process PID: " + str(os.getpid()))

    # Run with Profiler and Optimizations Disabled
    filename = directory + "/BENCHMARKS_PROFILER_NOOPT_" + date + ".log"
    command = ["tornado-benchmarks.py", "--profiler"]
    print("[SCRIPT] Running benchmarks with Profiler")
    runBenchmarks(filename, command)

    if spirvEnabled:
        # Run with Profiler and Optimizations Enabled
        filename = directory + "/BENCHMARKS_PROFILER_OPTIMIZED_" + date + ".log"
        command = ["tornado-benchmarks.py", "--profiler", "--spirvOptimizer"]
        print("[SCRIPT] Running benchmarks with Profiler - Optimized SPIR-V")
        runBenchmarks(filename, command)

    # Run end-to-end and Optimizations Disabled
    filename = directory + "/BENCHMARKS_END2END_NOOPT_" + date + ".log"
    command = ["tornado-benchmarks.py"]
    print("[SCRIPT] Running benchmarks END 2 END")
    runBenchmarks(filename, command)

    if spirvEnabled:
        # Run end-to-end and Optimizations enabled
        filename = directory + "/BENCHMARKS_END2END_OPTIMZED_" + date + ".log"
        command = ["tornado-benchmarks.py", "--spirvOptimizer"]
        print("[SCRIPT] Running benchmarks END 2 END - Optimized SPIR-V")
        runBenchmarks(filename, command)


if __name__ == "__main__":
    main()
