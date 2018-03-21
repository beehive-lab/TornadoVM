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

## Include here the new test clasess in Tornado
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
]

## Options
__MAIN_TORNADO_TEST_RUNNER__ = "uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=true -Dgraal.PrintCFG=true "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.opencl.source.print=True "
__DEBUG_TORNADO__ = "-Dtornado.debug=True "

## 
__VERSION__ = "0.3_21032018"

def composeAllOptions(args):
	""" This method contatenates all JVM options that will be passed to 
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

	return options


def runSingleCommand(cmd, args):
	""" Run a command without processing the result 
        of which tests are passed and failed.
		This method is used to pass a single test quickly 
		in the terminal.
	"""

	cmd = cmd + " " + args.testClass
	cmd = cmd.split(" ")
	print cmd

	start = time.time()
	p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()
	end = time.time()

	print out
	print "Total Time (s): " + str(end-start)


def processStats(out, stats):
	""" It updates the hash table `stats` for reporting 
		the total number of method that were failed and passed
	"""
	statsProcessing = out.split(" ")
	for w in statsProcessing:
		if (w == "[PASS]"):
			stats["[PASS]"] = stats["[PASS]"] + 1
		elif (w == "[FAILED]"):
			stats["[FAILED]"] = stats["[FAILED]"] + 1
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

		if (args.fast == False):
			print stats
			coverage = stats["[PASS]"] / float((stats["[PASS]"] + stats["[FAILED]"])) * 100.0
			print "Coverage: " + str(round(coverage, 2))  + "%" 
	

		print "Total Time(s): " + str(end-start)
		

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
	args = parser.parse_args()
	return args


def main():
	args = parseArguments()

	if (args.version):
		print __VERSION__
		sys.exit(0)

	if (args.junit):
		runWithJUnit(args)
	else:
		runTests(args)	

if __name__ == '__main__':
	main()

