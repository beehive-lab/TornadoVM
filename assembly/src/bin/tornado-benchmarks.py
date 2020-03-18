#!/usr/bin/env python2.7

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2013-2020, APT Group, Department of Computer Science,
# Department of Engineering, The University of Manchester. All rights reserved.
# Copyright (c) 2013-2019, APT Group, Department of Computer Science,
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
import subprocess
import textwrap
import sys

try:
	__JAVA_HOME__ = os.environ["JAVA_HOME"]
except:
	print "[ERROR] JAVA_HOME is not defined"
	sys.exit(0)

JDK_11_VERSION = "11.0"
JDK_8_VERSION = "1.8"
# Get java version
__JAVA_VERSION__ = subprocess.Popen(__JAVA_HOME__ + '/bin/java -version 2>&1 | awk -F[\\\"\.] -v OFS=. \'NR==1{print $2,$3}\'', stdout=subprocess.PIPE, shell=True).communicate()[0][:-1]

## ========================================================================================
## Script Options
## ========================================================================================
__RUNNER__ = ""
if (__JAVA_VERSION__ == JDK_11_VERSION):
    __RUNNER__ = " -m tornado.benchmarks/"
__RUNNER__ += "uk.ac.manchester.tornado.benchmarks.BenchmarkRunner "
__TORNADO_FLAGS__ = "-Dtornado.kernels.coarsener=False -Dtornado.profiles.print=True -Dtornado.profiling.enable=True -Dtornado.opencl.schedule=True"
__JVM_FLAGS__ = "-Xms24G -Xmx24G -server "
__DEVICES__ = [
	"-Ddevices=0:0",
	"-Ddevices=0:1",
]
__TORNADO_COMMAND__ = "tornado "
__SKIP_SERIAL__   = " -Dtornado.benchmarks.skipserial=True "
__SKIP_PARALLEL__ = " -Dtornado.enable=False "
__SKIP_DEVICES__  = " -Dtornado.blacklist.devices="
__VALIDATE__      = " -Dtornado.benchmarks.validate=True "
__VERBOSE__       = " -Dtornado.verbose=True "
## ========================================================================================

## Include here benchmarks to run
__BENCHMARKS__ = [
	"montecarlo",
	"nbody",
	"saxpy",
	"sgemm",
	"scopy",
	"blackscholes",
	"vectormult",
	"dft",
]

__PROBLEM_SIZES__ = [
	"1024",
	"2048",
	"4096",
	"8192",
	"16384",
	"32798",
	"65536",
	"262144"
	"1048576",
	"4194304",
]

def getSize():
	return ITERATIONS

## Hashmap with sizes
__MAX_ITERATIONS__ = 131
ITERATIONS = __MAX_ITERATIONS__
allSizes = {
	"montecarlo": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__MAX_ITERATIONS__]],
	"nbody": [[512, 1024, 2048, 4096, 16384, 327684], [__MAX_ITERATIONS__]],
	"saxpy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 131072, 262144, 524288, 1048576, 2097152, 4194304], [__MAX_ITERATIONS__]],
	"sgemm": [[128, 256, 512, 1024, 2048], [__MAX_ITERATIONS__]],
	"scopy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304, 16777216], [__MAX_ITERATIONS__]],
	"blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304], [__MAX_ITERATIONS__]],
	"vectormult": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__MAX_ITERATIONS__]],
	"dft": [[256, 512, 1024, 2048, 4096, 8192], [__MAX_ITERATIONS__]],
}

mediumSizes = {
	## Benchmark: [ [SIZES], [functionCALL] ] 
	"montecarlo": [[512, 1024, 2048, 4096, 8192], ["getSize()"]],
	"nbody": [[512, 1024, 2048, 4096], ["getSize()"]],
	"saxpy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 131072, 262144, 524288, 1048576, 2097152], ["getSize()"]],
	"sgemm": [[128, 256, 512, 1024, 2048], ["getSize()"]],
	"scopy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 2097152], ["getSize()"]],
	"blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536], ["getSize()"]],
	"vectormult": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 131072, 262144, 524288, 1048576], ["getSize()"]],
	"dft": [[256, 512, 1024, 2048, 4096], ["getSize()"]],
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
	if args.verbose:
		options = options + __VERBOSE__
	if args.skip_devices != None:
		options = options + __SKIP_DEVICES__ + args.skip_devices  + " "
	return options

def printBenchmakrks():
	print "List of benchmarks: "
	wrapper = textwrap.TextWrapper(initial_indent="* ")
	for b in __BENCHMARKS__:
		print wrapper.fill(b)

def runForAllSizes(args):
	options = composeAllOptions(args)
	for size in __PROBLEM_SIZES__:
		for bench in __BENCHMARKS__:
			command = __TORNADO_COMMAND__ + options + __RUNNER__ + bench + " " + str(ITERATIONS) + " " + str(size)
			os.system(command)

def runAllDevices(args):
	options = composeAllOptions(args)
	index = 0
	for d in __DEVICES__:
		print "Currently executing on device: device=0:", index
		for b in __BENCHMARKS__:
			command = __TORNADO_COMMAND__ + options + d + __RUNNER__ + b
			os.system(command)
		index += 1

def runBenchmarks(args):
	options = composeAllOptions(args)
	for b in __BENCHMARKS__:
		command = __TORNADO_COMMAND__ + options + __RUNNER__ + b
		os.system(command)

def runBenchmarksFullCoverage(args):
	options = composeAllOptions(args)
	for key in allSizes.keys():
		for size in allSizes[key][0]:
			command = __TORNADO_COMMAND__ + options + " " + __RUNNER__ + key + " " + str(allSizes[key][1][0]) + " " + str(size)
			if key is 'sgemm':
				command = command + " " + str(size)
			os.system(command)

def runMediumConfiguration(args):
	options = composeAllOptions(args)
	print options
	for key in mediumSizes.keys():
		for size in mediumSizes[key][0]:
			numIterations = eval(mediumSizes[key][1][0])
			command = __TORNADO_COMMAND__ + options + " " + __RUNNER__ + key + " " + str(numIterations) + " " + str(size)
			if key is 'sgemm':
				command = command + " " + str(size)
			os.system(command)

def parseArguments():
	parser = argparse.ArgumentParser(description="""Tool to execute benchmarks in TornadoVM. With no options, it runs the medium sizes""")
	parser.add_argument('--validate', action="store_true", dest="validate", default=False, help="Enable result validation")
	parser.add_argument('--default', action="store_true", dest="default", default=False, help="Run default benchmark configuration")
	parser.add_argument('--iterations', action="store", type=int, dest="iterations", default=0, help="Set the number of iterations")
	parser.add_argument('--devices', action="store_true", dest="device", default=False, help="Run to all devices")
	parser.add_argument('--sizes', action="store_true", dest="size", default=False, help="Run for all problem sizes")
	parser.add_argument('--full', action="store_true", dest="full", default=False, help="Run for all sizes in all devices. Including big data sizes")
	parser.add_argument('--skipSequential', action="store_true", dest="skip_serial", default=False, help="Skip java version")
	parser.add_argument('--skipParallel', action="store_true", dest="skip_parallel", default=False, help="Skip parallel version")
	parser.add_argument('--skipDevices', action="store", dest="skip_devices", default=None, help="Skip devices. Provide a list of devices (e.g., 0,1)")
	parser.add_argument('--verbose', "-V", action="store_true", dest="verbose", default=False, help="Enable verbose")
	parser.add_argument('--printBenchmarks', action="store_true", dest="benchmarks", default=False, help="Print the list of available benchmarks")
	args = parser.parse_args()
	return args

def main():
	args = parseArguments()
	global ITERATIONS
	if (args.iterations > 0):
		ITERATIONS = args.iterations

	if args.device:
		runAllDevices(args)
	elif args.size:
		runForAllSizes(args)
	elif args.benchmarks:
		printBenchmakrks()
	elif args.default:
		runBenchmarks(args)
	elif args.full:
		runBenchmarksFullCoverage(args)
	else:
		## Default option. It runs with medium size
		print "[INFO] Running small and medium sizes"
		runMediumConfiguration(args)
		
if __name__ == '__main__':
	main()
