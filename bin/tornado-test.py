#!/usr/bin/env python

import os
import sys
import argparse

## Include the new test class here
__TEST_THE_WORLD__ = [
	"tornado.unittests.TestHello",
	"tornado.unittests.arrays.TestArrays",
	"tornado.unittests.functional.TestFunctional",
	"tornado.unittests.ocljit.TestOpenCLJIT",
	"tornado.unittests.vectortypes.TestFloats",
	"tornado.unittests.vectortypes.TestDoubles",
	"tornado.unittests.vectortypes.TestInts",
	"tornado.unittests.vectortypes.TestVectorAllocation",
	"tornado.unittests.prebuilt.PrebuiltTest",
	"tornado.unittests.virtualization.TestsVirtualLayer",
	"tornado.unittests.tasks.TestSingleTaskSingleDevice",
	"tornado.unittests.tasks.TestMultipleTasksSingleDevice",
	"tornado.unittests.images.TestImages",
	"tornado.unittests.branching.TestConditionals",
	"tornado.unittests.loops.TestLoops",
]

## Options
__MAIN_TORNADO_TEST_RUNNER__ = "tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=true -Dgraal.PrintCFG=true "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.opencl.source.print=True "
__DEBUG_TORNADO__ = "-Dtornado.debug=True "

## 
__VERSION__ = "0.2_09112017"


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

