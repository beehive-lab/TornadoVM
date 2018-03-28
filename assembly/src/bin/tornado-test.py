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
# Authors: Juan Fumero
#

import os
import sys
import argparse
import time
import subprocess
import re

## Include the new test class here
__TEST_THE_WORLD__ = [
	"uk.ac.manchester.tornado.unittests.TestHello",
	"uk.ac.manchester.tornado.unittests.arrays.TestArrays",
	"uk.ac.manchester.tornado.unittests.functional.TestFunctional",
	"uk.ac.manchester.tornado.unittests.ocljit.TestOpenCLJIT",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestFloats",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestDoubles",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestInts",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestVectorAllocation",
	"uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTest",
	"uk.ac.manchester.tornado.unittests.virtualization.TestsVirtualLayer",
	"uk.ac.manchester.tornado.unittests.tasks.TestSingleTaskSingleDevice",
	"uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksSingleDevice",
	"uk.ac.manchester.tornado.unittests.images.TestImages",
	"uk.ac.manchester.tornado.unittests.branching.TestConditionals",
	"uk.ac.manchester.tornado.unittests.loops.TestLoops",
	"uk.ac.manchester.tornado.unittests.matrices.TestMatrices",
	"uk.ac.manchester.tornado.unittests.images.TestResizeImage",
	"uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers",
	"uk.ac.manchester.tornado.unittests.reductions.TestReductionsLongs",
]

## Options
__MAIN_TORNADO_TEST_RUNNER__ = "uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=true -Dgraal.PrintCFG=true "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.opencl.source.print=True "
__DEBUG_TORNADO__ = "-Dtornado.debug=True "

## 
__VERSION__ = "0.3_21032018"

__TORNADO_TESTS_WHITE_LIST__ = [
	"uk.ac.manchester.tornado.unittests.arrays.TestArrays#testVectorAdditionShort",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestFloats#simpleDotProductFloat8",
	"uk.ac.manchester.tornado.unittests.vectortypes.TestFloats#simpleDotProduct",
	"uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTest#testPrebuild01",
	]


__TEST_NOT_PASSED__= False

RED   = "\033[1;31m"  
BLUE  = "\033[1;34m"
CYAN  = "\033[1;36m"
GREEN = "\033[0;32m"
RESET = "\033[0;0m"
BOLD    = "\033[;1m"
REVERSE = "\033[;7m"

def composeAllOptions(args):
	""" This method concatenates all JVM options that will be passed to 
		the Tornado VM. New options should be concatenated in this method. 
	"""

	verbose = "-Dtornado.unittests.verbose="
	options = verbose

	if (args.verbose):
		options = options + "True "
	else:
		options = options + "False "

	if (args.igv):
		options = options + __IGV_OPTIONS__

	if (args.debugTornado):
		options = options + __DEBUG_TORNADO__

	if (args.printKernel):
		options = options + __PRINT_OPENCL_KERNEL__

	if (args.device != None):
		options = options + args.device
		print options

	return options


def runSingleCommand(cmd, args):
	""" Run a command without processing the result of which tests 
		are passed and failed. This method is used to pass a single 
		test quickly in the terminal.
	"""

	cmd = cmd + " " + args.testClass
	cmd = cmd.split(" ")

	start = time.time()
	p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()
	end = time.time()

	print out
	print "Total Time (s): " + str(end-start)


def processStats(out, stats):
	""" It updates the hash table `stats` for reporting the total number 
		of methods that were failed and passed
	"""
	
	global __TEST_NOT_PASSED__ 

	pattern = r'Test: class (?P<test_class>[\w\.]+)*\S*$'
	regex = re.compile(pattern)

	statsProcessing = out.splitlines()
	className = ""
	for line in statsProcessing:
		match = regex.search(line)
		if match != None:
			className = match.groups(0)[0]
		
		l = re.sub(r'(  )+', '', line).strip()

		if (l.find("[PASS]") != -1):
			stats["[PASS]"] = stats["[PASS]"] + 1
		elif (l.find("[FAILED]") != -1) :
			stats["[FAILED]"] = stats["[FAILED]"] + 1
			name = l.split(" ")[2]

			# It removes characters for colors
			name = name[5:-4]
		
			if (name.endswith(".")):
				name = name[:-16]

			if (className + "#" + name in __TORNADO_TESTS_WHITE_LIST__):
				print RED + "Test: " + className + "#" + name + " in whiteList." + RESET
			else:
				## set a flag
				__TEST_NOT_PASSED__ = True
	
	return stats


def runCommandWithStats(command, stats):
	""" Run a command and update the stats dictionary """
	command = command.split(" ")
	p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()

	print err
	print out
	
	return processStats(out, stats)


def runTests(args):
	""" Run the tests using the TornadoTestRunner program """	

	options = composeAllOptions(args)

	stats = {"[PASS]" : 0, "[FAILED]": 0}

	## Run test
	cmd = "tornado " + options + " " + __MAIN_TORNADO_TEST_RUNNER__ 
	if (args.testClass != None):

		if (args.fast):
			cmd = cmd + " " + args.testClass
			os.system(cmd)
		else:
			runSingleCommand(cmd, args)
	else:
		start = time.time()
		for t in __TEST_THE_WORLD__:
			command = cmd + t

			if (args.fast):
				os.system(command)
			else:
				stats = runCommandWithStats(command, stats)
		
		end = time.time()
		print CYAN

		if (args.fast == False):
			print GREEN
			print "=================================================="
			print BLUE + "              Unit tests report " + GREEN
			print "=================================================="
			print CYAN
			print stats
			coverage = stats["[PASS]"] / float((stats["[PASS]"] + stats["[FAILED]"])) * 100.0
			print "Coverage: " + str(round(coverage, 2))  + "%" 
			print GREEN
			print "=================================================="
			print CYAN

		print "Total Time(s): " + str(end-start)
		print RESET
		

def runWithJUnit(args):
	""" Run the tests using JUNIT """

	cmd = "tornado " + __MAIN_TORNADO_JUNIT__ 

	if (args.testClass != None):
		cmd = cmd + args.testClass
		os.system(cmd)
	else:	
		for t in __TEST_THE_WORLD__:
			command = cmd + t
			os.system(command)


def parseArguments():
	""" Parse command line arguments """ 
	parser = argparse.ArgumentParser(description='Tool to execute tests in Tornado')
	parser.add_argument('testClass', nargs="?", help='testClass#method')
	parser.add_argument('--version', action="store_true", dest="version", default=False, help="Print version")
	parser.add_argument('--verbose', "-V", action="store_true", dest="verbose", default=False, help="Run test in verbose mode")	
	parser.add_argument('--printKernel', "-pk", action="store_true", dest="printKernel", default=False, help="Print OpenCL kernel")	
	parser.add_argument('--junit', action="store_true", dest="junit", default=False, help="Run within JUnitCore main class")	
	parser.add_argument('--igv', action="store_true", dest="igv", default=False, help="Dump GraalIR into IGV")	
	parser.add_argument('--debug', "-d", action="store_true", dest="debugTornado", default=False, help="Debug Tornado")
	parser.add_argument('--fast', "-f", action="store_true", dest="fast", default=False, help="Visualize Fast")	
	parser.add_argument('--device', dest="device", default=None, help="Set an specific device. E.g `s0.t0.device=0:1`")	
	args = parser.parse_args()
	return args

def writeStatusInFile():
	f = open(".unittestingStatus", "w")
	if (__TEST_NOT_PASSED__):
		f.write("FAIL")
	else:
		f.write("OK")
	f.close()


def main():
	args = parseArguments()

	if (args.version):
		print __VERSION__
		sys.exit(0)

	if (args.junit):
		runWithJUnit(args)
	else:
		runTests(args)	

	writeStatusInFile()
	if (__TEST_NOT_PASSED__):
		# return error
		sys.exit(1)

if __name__ == '__main__':
	main()

