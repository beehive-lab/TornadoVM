#!/usr/bin/env python

import os
import sys
import argparse

__TEST_THE_WORLD__ = [
	"tornado.unittests.TestHello",
	"tornado.unittests.arrays.TestArrays",
	"tornado.unittests.functional.TestFunctional",
]

__MAIN_TORNADO_TEST_RUNNER__ = "tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=true -Dgraal.PrintCFG=true "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.opencl.source.dump=True "
__VERSION__ = "0.1_02112017"

def runTests(args):

	## Compose all Tornado options
	verbose = "-Dtornado.unittests.verbose="
	igv = " "
	if (args.verbose):
		verbose = verbose + "True "
	else:
		verbose = verbose + "False "

	if (args.igv):
		igv = igv + __IGV_OPTIONS__

	printKernelOption = " "
	if (args.printKernel):
		printKernelOption = " " + __PRINT_OPENCL_KERNEL__

	## Run test
	cmd = "tornado " + verbose + printKernelOption + igv + " " + __MAIN_TORNADO_TEST_RUNNER__ 
	if (args.testClass != None):
		cmd = cmd + " " + args.testClass 
		print cmd
		os.system(cmd)
	else:
		for t in __TEST_THE_WORLD__:
			command = cmd + t
			print cmd
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
	parser = argparse.ArgumentParser(description='Tool to execute Tornado unit testing')
	parser.add_argument('testClass', nargs="?", help='testClass#method')
	parser.add_argument('--version', action="store_true", dest="version", default=False, help="Print version")
	parser.add_argument('--verbose', action="store_true", dest="verbose", default=False, help="Run test in verbose mode")	
	parser.add_argument('--printKernel', action="store_true", dest="printKernel", default=False, help="Print OpenCL kernel")	
	parser.add_argument('--junit', action="store_true", dest="junit", default=False, help="Run within JUnitCore main class")	
	parser.add_argument('--igv', action="store_true", dest="igv", default=False, help="Dump GraalIR into IGV")	
	parser.add_argument('--testall', action="store_true", dest="verbose", default=False, help="Run all unittest in Tornado")	
	args = parser.parse_args()
	return args

if __name__ == '__main__':

	args = parseArguments()

	if (args.version):
		print __VERSION__
		sys.exit(0)

	if (args.junit):
		runWithJUnit(args)
	else:
		runTests(args)	
