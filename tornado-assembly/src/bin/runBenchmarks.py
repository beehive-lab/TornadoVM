#!/usr/bin/env python3
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
