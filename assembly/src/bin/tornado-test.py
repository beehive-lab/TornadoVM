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
]

## Options
__MAIN_TORNADO_TEST_RUNNER__ = "uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=true -Dgraal.PrintCFG=true "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.opencl.source.print=True "
__DEBUG_TORNADO__ = "-Dtornado.debug=True "

## 
__VERSION__ = "0.2_27022018"


def composeAllOptions(args):

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


def runTests(args):
	options = composeAllOptions(args)

	## Run test
	cmd = "tornado " + options + " " + __MAIN_TORNADO_TEST_RUNNER__ 
	if (args.testClass != None):
		cmd = cmd + " " + args.testClass 
		#print cmd
		os.system(cmd)
	else:
		for t in __TEST_THE_WORLD__:
			command = cmd + t
			#print cmd
			os.system(command)


def runWithJUnit(args):
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

