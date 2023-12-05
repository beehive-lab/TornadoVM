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

"""
Script for reading a JSON output from the TornadoVM's profiler.

It prints the block 0 (with all compilation values) and the median for
all entries for the rest of the timers not related to compilation.

Usage: ./readJsonFile.py <inputFile.json>
"""

import json
import numpy as np
import sys


class Colors:
    RED = "\033[1;31m"
    BLUE = "\033[1;34m"
    CYAN = "\033[1;36m"
    GREEN = "\033[0;32m"
    RESET = "\033[0;0m"
    BOLD = "\033[;1m"
    REVERSE = "\033[;7m"


class TornadoVMJsonReader:
    def __init__(self):
        self.timers = dict()
        self.ignoreStrings = [
            "TOTAL_BYTE_CODE_GENERATION",
            "TOTAL_GRAAL_COMPILE_TIME",
            "TOTAL_DRIVER_COMPILE_TIME",
            "TASK_COMPILE_GRAAL_TIME",
            "TASK_COMPILE_DRIVER_TIME",
            "DEVICE",
            "IP",
            "DEVICE_ID",
            "METHOD",
        ]

    def ignore(self, keyString):
        if keyString in self.ignoreStrings:
            return True

        if keyString.endswith(tuple(self.ignoreStrings)):
            return True
        return False

    def createList(self, key, value):
        self.timers[key] = [float(value)]

    def addToList(self, key, value):
        if not self.ignore(key):
            # print Colors.CYAN + "ADDING " + key + Colors.RESET
            if key not in self.timers:
                self.createList(key, value)
            else:
                self.timers[key].append(float(value))

    def processBlock0(self, data, taskScheduleName, index):
        ## For the Block 0 we can get compilation times
        print(Colors.BLUE + "Entry,0 " + Colors.RESET)
        entryNumber = str(index)
        entriesBlock0 = data[entryNumber][taskScheduleName].keys()
        for e in entriesBlock0:
            value = data[entryNumber][taskScheduleName][e]
            if type(value) is not dict:
                print(e + "," + str(value))
                self.addToList(e, value)
            else:
                keys = data[entryNumber][taskScheduleName][e].keys()
                print("TaskName, " + e)
                for e2 in keys:
                    v = data[entryNumber][taskScheduleName][e][e2]
                    print(e2 + "," + str(v))
                    self.addToList(e + "-" + e2, v)
        print(Colors.BLUE + "EndEntry,0" + Colors.RESET)

    def traverseBlocks(self, data, taskScheduleName, index):
        entryNumber = str(index)
        entriesBlock0 = data[entryNumber][taskScheduleName].keys()
        for e in entriesBlock0:
            value = data[entryNumber][taskScheduleName][e]
            if type(value) is not dict:
                self.addToList(e, value)
            else:
                keys = data[entryNumber][taskScheduleName][e].keys()
                for e2 in keys:
                    v = data[entryNumber][taskScheduleName][e][e2]
                    self.addToList(e + "-" + e2, v)

    def readJsonFile(self, inputFileName, outputFileName="output.json"):
        with open(inputFileName, "r") as inputFile:
            content = inputFile.read()

        data = json.loads(content)

        keys = data.keys()
        numEntries = len(keys)
        print("Num entries = " + str(numEntries))
        taskScheduleName = list(data["0"].keys())[0]

        # Print block 0 and prepare the lists
        self.processBlock0(data, taskScheduleName, 0)

        ## Add all data to the lists
        for i in range(1, numEntries):
            self.traverseBlocks(data, taskScheduleName, i)

        mean = {}
        medians = {}
        ## Compute the median for each list
        for k in self.timers.keys():
            medians[k] = np.median(self.timers[k])
            mean[k] = np.mean(self.timers[k])

        ## Print csv table with the median for the rest
        print(Colors.GREEN + "MEDIANS" + Colors.RESET)
        for k in medians.keys():
            print(k + "," + str(medians[k]))

        print(Colors.GREEN + "------" + Colors.RESET)


if __name__ == "__main__":
    print(sys.argv)
    if len(sys.argv) == 2:
        print("Processing file: " + sys.argv[1])
        jsonReader = TornadoVMJsonReader()
        jsonReader.readJsonFile(sys.argv[1])
    else:
        print("Usage: ./readJsonFile.py <inputFile.json>")
