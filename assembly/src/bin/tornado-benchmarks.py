#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornado
#
# Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

## ========================================================================================
## Script Options
## ========================================================================================
__TORNADO_FLAGS__ = "-Dtornado.kernels.coarsener=False -Dtornado.profiles.print=True -Dtornado.profiling.enable=True -Dtornado.opencl.schedule=True"
__JVM_FLAGS__ = "-Xms24G -Xmx24G -server"
__RUNNER__ = " uk.ac.manchester.tornado.benchmarks.BenchmarkRunner "
__DEVICES__ = [
	"-Ddevices=0:0",
	"-Ddevices=0:1",
]
__TORNADO_COMMAND__ = "tornado "
__SKIP_SERIAL__ = " -Dtornado.benchmarks.skipserial=True "
__SKIP_PARALLEL = " -Dtornado.enable=False "
__VALIDATE__    = " -Dtornado.benchmarks.validate=True "
__VERBOSE__     = " -Dtornado.verbose=True "
__JENKINS_ITERATIONS__ = 3
## ========================================================================================

## Include here benchmarks to run
__BENCHMARKS__ = [
	"montecarlo",
	"nbody",
	"saxpy",
	"sgemm",
	"scopy",
	"blackscholes",
	"bitset",
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

## Hashmap with sizes
__MAX_ITERATIONS__ = 131
executions = {
	"montecarlo": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__MAX_ITERATIONS__]],
	"nbody": [[512, 1024, 2040, 4096, 16384, 327684], [__MAX_ITERATIONS__]],
	"saxpy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304], [__MAX_ITERATIONS__]],
	"sgemm": [[128, 256, 512, 1024], [__MAX_ITERATIONS__]],
	"scopy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304, 16777216], [__MAX_ITERATIONS__]],
	"blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304], [__MAX_ITERATIONS__]],
	"vectormult": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__MAX_ITERATIONS__]],
	"bitset": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536], [__MAX_ITERATIONS__]],
	"dft": [[256, 512, 1024, 2048, 4096, 8192], [__MAX_ITERATIONS__]],
}

jenkins_executions = {
	"montecarlo": [[512, 1024, 2048, 4096, 8192, 16384, 32798], [__JENKINS_ITERATIONS__]],
	"nbody": [[512, 1024, 2040, 4096], [__JENKINS_ITERATIONS__]],
	"saxpy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304], [__JENKINS_ITERATIONS__]],
	"sgemm": [[128, 256, 512, 1024], [__JENKINS_ITERATIONS__]],
	"scopy": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576, 4194304], [__JENKINS_ITERATIONS__]],
	"blackscholes": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__JENKINS_ITERATIONS__]],
	"vectormult": [[512, 1024, 2048, 4096, 8192, 16384, 32798, 65536, 1048576], [__JENKINS_ITERATIONS__]],
	"bitset": [[512, 1024, 2048, 4096, 8192, 16384, 32798], [__JENKINS_ITERATIONS__]],
	"dft": [[256, 512, 1024, 2048, 4096], [__JENKINS_ITERATIONS__]],
}

def composeAllOption(args):
	options = __JVM_FLAGS__
	if args.skip_serial:
		options = options + __SKIP_SERIAL__
	if args.skip_parallel:
		options = options + __SKIP_PARALLEL
	if args.validate:
		options = options + __VALIDATE__
	if args.verbose:
		options = options + __VERBOSE__
	return options

def printBenchmakrks():
	print "List of benchmarks: "
	wrapper = textwrap.TextWrapper(initial_indent="* ")
	for b in __BENCHMARKS__:
		print wrapper.fill(b)

def runForAllSizes(args):
	options = composeAllOption(args)
	for s in __PROBLEM_SIZES__:
		for b in __BENCHMARKS__:
			command = __TORNADO_COMMAND__ + options + __RUNNER__ + b + __JENKINS_ITERATIONS__ + s
			os.system(command)

def runAllDevices(args):
	options = composeAllOption(args)
	index = 0
	for d in __DEVICES__:
		print "Currently executing on device: device=0:", index
		for b in __BENCHMARKS__:
			command = __TORNADO_COMMAND__ + options + d + __RUNNER__ + b
			os.system(command)
		index += 1

def runBenchmarks(args):
	options = composeAllOption(args)

	for b in __BENCHMARKS__:
		command = __TORNADO_COMMAND__ + options + __RUNNER__ + b
		os.system(command)

def runBenchmarksFullCoverage(args):
	options = composeAllOption(args)
	for key in executions.keys():
		for size in executions[key][0]:
			command = __TORNADO_COMMAND__ + options + " " + __RUNNER__ + key + " " + str(executions[key][1][0]) + " " + str(size)
			if key is 'sgemm':
				command = command + " " + str(size)
			os.system(command)

def runJenkinsConfiguration(args):
        options = composeAllOption(args)
	for key in jenkins_executions.keys():
		for size in jenkins_executions[key][0]:
			command = __TORNADO_COMMAND__ + options + " " + __RUNNER__ + key + " " + str(jenkins_executions[key][1][0]) + " " + str(size) + " " + str(size)
			if key is 'sgemm':
				command = command + " " + str(size)
			os.system(command)

def parseArguments():
	parser = argparse.ArgumentParser(description='Tool to execute benchmarks in Tornado')
	parser.add_argument('--devices', "-D", action="store_true", dest="device", default=False, help="Run to all devices")
	parser.add_argument('--sizes', "-S", action="store_true", dest="size", default=False, help="Run for all problem sizes")
	parser.add_argument('--benchmarks', "-BL", action="store_true", dest="benchmarks", default=False, help="Print list of benchmarks")
	parser.add_argument('--metrics', "-M", action="store_true", dest="metrics", default=False, help="Run for all sizes in all devices")
	parser.add_argument('--skipSeq', "-SS", action="store_true", dest="skip_serial", default=False, help="Skip java version")
	parser.add_argument('--validate', "-VL", action="store_true", dest="validate", default=False, help="Enable result validation")
	parser.add_argument('--skipPar', "-SP", action="store_true", dest="skip_parallel", default=False, help="Skip parallel version")
	parser.add_argument('--jenkins', "-J", action="store_true", dest="jenkins", default=False, help="Run jenkins benchmark configuration")
	parser.add_argument('--verbose', "-V", action="store_true", dest="verbose", default=False, help="Enable verbose")
	args = parser.parse_args()
	return args

def main():
	args = parseArguments()
	if args.device:
		runAllDevices(args)
	elif args.size:
		runForAllSizes(args)
	elif args.benchmarks:
		printBenchmakrks()
        elif args.jenkins:
                runJenkinsConfiguration(args)
	elif args.metrics:
		runBenchmarksFullCoverage(args)
	else:
		runBenchmarks(args)

if __name__ == '__main__':
	main()
