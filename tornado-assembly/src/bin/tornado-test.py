#!/usr/bin/env python3
# vim: set tasbstop=4

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import argparse
import os
import re
import shlex
import subprocess
import sys
import time


class TestEntry:
    def __init__(self, testName, testMethods=None, testParameters=None):
        self.testName = testName
        self.testMethods = testMethods
        self.testParameters = testParameters


## List of classes to be tested. Include new unittest classes here
__TEST_THE_WORLD__ = [

    ## SPIR-V, OpenCL and PTX foundation tests
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestIntegers"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestFloats"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestDoubles"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.MultipleRuns"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestLinearAlgebra"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestLong"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestShorts"),
    TestEntry("uk.ac.manchester.tornado.unittests.foundation.TestIf"),

    ## TornadoVM standard test-suite
    TestEntry("uk.ac.manchester.tornado.unittests.TestHello"),
    TestEntry("uk.ac.manchester.tornado.unittests.arrays.TestArrays"),
    TestEntry("uk.ac.manchester.tornado.unittests.functional.TestLambdas"),
    TestEntry("uk.ac.manchester.tornado.unittests.vectortypes.TestFloats"),
    TestEntry("uk.ac.manchester.tornado.unittests.vectortypes.TestDoubles"),
    TestEntry("uk.ac.manchester.tornado.unittests.vectortypes.TestInts"),
    TestEntry("uk.ac.manchester.tornado.unittests.vectortypes.TestVectorAllocation"),
    TestEntry("uk.ac.manchester.tornado.unittests.prebuilt.PrebuiltTest"),
    TestEntry("uk.ac.manchester.tornado.unittests.virtualization.TestsVirtualLayer"),
    TestEntry("uk.ac.manchester.tornado.unittests.tasks.TestSingleTaskSingleDevice"),
    TestEntry("uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksSingleDevice"),
    TestEntry("uk.ac.manchester.tornado.unittests.temporary.values.TestTemporaryValues"),
    TestEntry("uk.ac.manchester.tornado.unittests.images.TestImages"),
    TestEntry("uk.ac.manchester.tornado.unittests.images.TestResizeImage"),
    TestEntry("uk.ac.manchester.tornado.unittests.branching.TestConditionals"),
    TestEntry("uk.ac.manchester.tornado.unittests.loops.TestLoops"),
    TestEntry("uk.ac.manchester.tornado.unittests.loops.TestParallelDimensions"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.TestReductionsDoubles"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.TestReductionsLong"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.InstanceReduction"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.MultipleReductions"),
    TestEntry("uk.ac.manchester.tornado.unittests.reductions.TestReductionsAutomatic"),
    TestEntry("uk.ac.manchester.tornado.unittests.instances.TestInstances"),
    TestEntry("uk.ac.manchester.tornado.unittests.matrices.TestMatrixTypes"),
    TestEntry("uk.ac.manchester.tornado.unittests.api.TestAPI"),
    TestEntry("uk.ac.manchester.tornado.unittests.api.TestIO"),
    TestEntry("uk.ac.manchester.tornado.unittests.executor.TestExecutor"),
    TestEntry("uk.ac.manchester.tornado.unittests.grid.TestGrid"),
    TestEntry("uk.ac.manchester.tornado.unittests.grid.TestGridScheduler"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.api.TestCombinedTaskGraph"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.api.TestVectorAdditionKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.api.KernelContextWorkGroupTests"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsIntegersKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsFloatsKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsDoublesKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.kernelcontext.reductions.TestReductionsLongKernelContext"),
    TestEntry("uk.ac.manchester.tornado.unittests.math.TestMath"),
    TestEntry("uk.ac.manchester.tornado.unittests.batches.TestBatches"),
    TestEntry("uk.ac.manchester.tornado.unittests.lambdas.TestLambdas"),
    TestEntry("uk.ac.manchester.tornado.unittests.flatmap.TestFlatMap"),
    TestEntry("uk.ac.manchester.tornado.unittests.logic.TestLogic"),
    TestEntry("uk.ac.manchester.tornado.unittests.fields.TestFields"),
    TestEntry("uk.ac.manchester.tornado.unittests.profiler.TestProfiler"),
    TestEntry("uk.ac.manchester.tornado.unittests.bitsets.BitSetTests"),
    TestEntry("uk.ac.manchester.tornado.unittests.fails.TestFails"),
    TestEntry("uk.ac.manchester.tornado.unittests.fails.RuntimeFail"),
    TestEntry("uk.ac.manchester.tornado.unittests.math.TestTornadoMathCollection"),
    TestEntry("uk.ac.manchester.tornado.unittests.arrays.TestNewArrays"),
    TestEntry("uk.ac.manchester.tornado.unittests.dynsize.Resize"),
    TestEntry("uk.ac.manchester.tornado.unittests.loops.TestLoopTransformations"),
    TestEntry("uk.ac.manchester.tornado.unittests.numpromotion.TestNumericPromotion"),
    TestEntry("uk.ac.manchester.tornado.unittests.numpromotion.Types"),
    TestEntry("uk.ac.manchester.tornado.unittests.numpromotion.Inlining"),
    TestEntry("uk.ac.manchester.tornado.unittests.fails.CodeFail"),
    TestEntry("uk.ac.manchester.tornado.unittests.parameters.ParameterTests"),
    TestEntry("uk.ac.manchester.tornado.unittests.codegen.CodeGen"),
    TestEntry("uk.ac.manchester.tornado.unittests.atomics.TestAtomics"),
    TestEntry("uk.ac.manchester.tornado.unittests.compute.ComputeTests"),
    TestEntry("uk.ac.manchester.tornado.unittests.dynamic.TestDynamic"),
    TestEntry("uk.ac.manchester.tornado.unittests.tasks.TestMultipleFunctions"),
    TestEntry("uk.ac.manchester.tornado.unittests.tasks.TestMultipleTasksMultipleDevices"),
    TestEntry("uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends"),

    ## Test for function calls - We force not to inline methods
    TestEntry(testName="uk.ac.manchester.tornado.unittests.tasks.TestMultipleFunctions",
              testParameters=[
                  "-XX:CompileCommand=dontinline,uk/ac/manchester/tornado/unittests/tasks/TestMultipleFunctions.*"]),

    ## Tests for Virtual Devices
    TestEntry(testName="uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceKernel",
              testMethods=["testVirtualDeviceKernelGPU"],
              testParameters=[
                  "-Dtornado.device.desc=" + os.environ["TORNADO_SDK"] + "/examples/virtual-device-GPU.json",
                  "-Dtornado.print.kernel=True", "-Dtornado.virtual.device=True",
                  "-Dtornado.print.kernel.dir=" + os.environ["TORNADO_SDK"] + "/virtualKernelOut.out"]),
    TestEntry(testName="uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceKernel",
              testMethods=["testVirtualDeviceKernelCPU"],
              testParameters=[
                  "-Dtornado.device.desc=" + os.environ["TORNADO_SDK"] + "/examples/virtual-device-CPU.json",
                  "-Dtornado.print.kernel=True", "-Dtornado.virtual.device=True",
                  "-Dtornado.print.kernel.dir=" + os.environ["TORNADO_SDK"] + "/virtualKernelOut.out"]),
    TestEntry(testName="uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceFeatureExtraction",
              testMethods=["testVirtualDeviceFeaturesGPU"],
              testParameters=[
                  "-Dtornado.device.desc=" + os.environ["TORNADO_SDK"] + "/examples/virtual-device-GPU.json",
                  "-Dtornado.virtual.device=True", "-Dtornado.feature.extraction=True",
                  "-Dtornado.features.dump.dir=" + os.environ["TORNADO_SDK"] + "/virtualFeaturesOut.out"]),
    TestEntry(testName="uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceFeatureExtraction",
              testMethods=["testVirtualDeviceFeaturesCPU"],
              testParameters=[
                  "-Dtornado.device.desc=" + os.environ["TORNADO_SDK"] + "/examples/virtual-device-CPU.json",
                  "-Dtornado.virtual.device=True", "-Dtornado.feature.extraction=True",
                  "-Dtornado.features.dump.dir=" + os.environ["TORNADO_SDK"] + "/virtualFeaturesOut.out"])
]

## List of tests that can be ignored. The following either fail (we know it is a precision error), or they are not supported
## for specific backends. Every group of ignored tests have an explanation about the failure and the backends that are affected. 
#  Format: class#testMethod
__TORNADO_TESTS_WHITE_LIST__ = [

    ## PTX Backend does not support mathTahn
    "uk.ac.manchester.tornado.unittests.math.TestMath#testMathTanh",
    "uk.ac.manchester.tornado.unittests.math.TestTornadoMathCollection#testTornadoMathTanh",

    ## Virtual devices are only available for OpenCL. 
    "uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceKernel#testVirtualDeviceKernelGPU",
    "uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceKernel#testVirtualDeviceKernelCPU",
    "uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceFeatureExtraction#testVirtualDeviceFeaturesCPU",
    "uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceFeatureExtraction#testVirtualDeviceFeaturesGPU",

    ## Atomics are only available for OpenCL
    "uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic12",
    "uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic15",

    ## Precision errors 
    "uk.ac.manchester.tornado.unittests.compute.ComputeTests#testNBodyBigNoWorker",
    "uk.ac.manchester.tornado.unittests.compute.ComputeTests#testEuler",
    "uk.ac.manchester.tornado.unittests.codegen.CodeGen#test02",
    "uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext#mxm1DKernelContext",
    "uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext#mxm2DKernelContext01",
    "uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationKernelContext#mxm2DKernelContext02",

    # It might have errors during type casting and type conversion. However, the fractals images look correct.
    # This errors might be related to error precision when running many threads in parallel. 
    "uk.ac.manchester.tornado.unittests.compute.ComputeTests#testMandelbrot",
    "uk.ac.manchester.tornado.unittests.compute.ComputeTests#testJuliaSets",

    ## For the OpenCL Backend
    "uk.ac.manchester.tornado.unittests.foundation.TestIf#test06",

    ## Atomics
    "uk.ac.manchester.tornado.unittests.atomics.TestAtomics#testAtomic12",

    ## Multi-backend 
    "uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends#testTwoBackendsSerial",
    "uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends#testTwoBackendsConcurrent",
    "uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends#testThreeBackendsSerial",
    "uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentBackends#testThreeBackendsConcurrent",

]

# ################################################################################################################
## Default options and flags
# ################################################################################################################
__MAIN_TORNADO_TEST_RUNNER_MODULE__ = " tornado.unittests/"
__MAIN_TORNADO_TEST_RUNNER__ = "uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner "
__MAIN_TORNADO_JUNIT_MODULE__ = " junit/"
__MAIN_TORNADO_JUNIT__ = "org.junit.runner.JUnitCore "
__IGV_OPTIONS__ = "-Dgraal.Dump=*:verbose -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true "
__IGV_LAST_PHASE__ = "-Dgraal.Dump=*:1 -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true -Dtornado.debug.lowtier=True "
__PRINT_OPENCL_KERNEL__ = "-Dtornado.print.kernel=True "
__DEBUG_TORNADO__ = "-Dtornado.debug=True "
__TORNADOVM_FULLDEBUG__ = __DEBUG_TORNADO__ + "-Dtornado.fullDebug=True "
__THREAD_INFO__ = "-Dtornado.threadInfo=True "
__PRINT_EXECUTION_TIMER__ = "-Dtornado.debug.executionTime=True "
__GC__ = "-Xmx6g "
__BASE_OPTIONS__ = "-Dtornado.recover.bailout=False "
__VERBOSE_OPTION__ = "-Dtornado.unittests.verbose="
__TORNADOVM_PRINT_BC__ = "-Dtornado.print.bytecodes=True "
# ################################################################################################################

TORNADO_CMD = "tornado "
ENABLE_ASSERTIONS = "-ea "

__VERSION__ = "0.16_20122022"

JDK_8_VERSION = "1.8"
try:
    javaHome = os.environ["JAVA_HOME"]
except:
    print("[ERROR] JAVA_HOME is not defined.")
    sys.exit(-1)

__TEST_NOT_PASSED__ = False


class Colors:
    RED = "\033[1;31m"
    BLUE = "\033[1;34m"
    CYAN = "\033[1;36m"
    GREEN = "\033[0;32m"
    RESET = "\033[0;0m"
    BOLD = "\033[;1m"
    REVERSE = "\033[;7m"


def composeAllOptions(args):
    """ This method concatenates all JVM options that will be passed to
        the Tornado VM. New options should be concatenated in this method.
    """

    options = __GC__ + __BASE_OPTIONS__

    if (args.verbose):
        options = options + __VERBOSE_OPTION__ + "True "
    else:
        options = options + __VERBOSE_OPTION__ + "False "

    if (args.dumpIGVLastTier):
        options = options + __IGV_LAST_PHASE__
    elif (args.igv):
        options = options + __IGV_OPTIONS__

    if (args.debugTornado):
        options = options + __DEBUG_TORNADO__

    if (args.fullDebug):
        options = options + __TORNADOVM_FULLDEBUG__

    if (args.threadInfo):
        options = options + __THREAD_INFO__

    if (args.printKernel):
        options = options + __PRINT_OPENCL_KERNEL__

    if (args.device != None):
        options = options + args.device

    if (args.printExecution):
        options = options + __PRINT_EXECUTION_TIMER__

    if (args.printBytecodes):
        options = options + __TORNADOVM_PRINT_BC__

    if (args.jvmFlags != None):
        options = options + args.jvmFlags

    return options


def runSingleCommand(cmd, args):
    """ Run a command without processing the result of which tests
        are passed and failed. This method is used to pass a single
        test quickly in the terminal.
    """

    cmd = cmd + " " + args.testClass

    start = time.time()
    p = subprocess.Popen(shlex.split(cmd), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    end = time.time()
    out = out.decode('utf-8')
    err = err.decode('utf-8')

    print(err)
    print(out)
    print("Total Time (s): " + str(end - start))


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
        elif (l.find("[FAILED]") != -1):
            stats["[FAILED]"] = stats["[FAILED]"] + 1
            name = l.split(" ")[2]

            # It removes characters for colors
            name = name[5:-4]

            if (name.endswith(".")):
                name = name[:-16]

            if (className + "#" + name in __TORNADO_TESTS_WHITE_LIST__):
                print(Colors.RED + "Test: " + className + "#" + name + " in whiteList." + Colors.RESET)
            else:
                ## set a flag
                __TEST_NOT_PASSED__ = True

        elif (l.find("UNSUPPORTED") != -1):
            stats["[UNSUPPORTED]"] = stats["[UNSUPPORTED]"] + 1

    return stats


def runCommandWithStats(command, stats):
    """ Run a command and update the stats dictionary """
    p = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    out = out.decode('utf-8')
    err = err.decode('utf-8')

    if (err.rfind("Segmentation fault") > 0):
        print(Colors.REVERSE)
        print("[!] RUNNING AGAIN BECAUSE OF A SEG FAULT")
        print(Colors.RESET)
        p = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = p.communicate()
        out = out.decode('utf-8')
        err = err.decode('utf-8')

    print(err)
    print(out)

    return processStats(out, stats)


def appendTestRunnerClassToCmd(cmd, args):
    testRunner = __MAIN_TORNADO_TEST_RUNNER__
    module = __MAIN_TORNADO_TEST_RUNNER_MODULE__
    if args.junit:
        testRunner = __MAIN_TORNADO_JUNIT__
        module = __MAIN_TORNADO_JUNIT_MODULE__

    if (javaVersion != JDK_8_VERSION):
        cmd += " -m " + module + testRunner
    else:
        cmd += " " + testRunner
    return cmd


def runTests(args):
    """ Run the tests using the TornadoTestRunner program """

    options = composeAllOptions(args)

    if (args.testClass != None):
        options = "--jvm \"" + options + "\" "
        cmd = TORNADO_CMD + options
        command = appendTestRunnerClassToCmd(cmd, args)
        command = command + " --params \"" + args.testClass + "\""
        if (args.fast):
            print(command)
            os.system(command)
        else:
            runSingleCommand(command, args)
    else:
        start = time.time()
        stats = runTestTheWorld(options, args)
        end = time.time()
        print(Colors.CYAN)

        if args.fast == False and args.verbose == True:
            print(Colors.GREEN)
            print("==================================================")
            print(Colors.BLUE + "              Unit tests report " + Colors.GREEN)
            print("==================================================")
            print(Colors.CYAN)
            print(stats)
            coverage = stats["[PASS]"] / float((stats["[PASS]"] + stats["[FAILED]"])) * 100.0
            coverageTotal = stats["[PASS]"] / float(
                (stats["[PASS]"] + stats["[FAILED]"] + stats["[UNSUPPORTED]"])) * 100.0
            print("Coverage [PASS/(PASS+FAIL)]: " + str(round(coverage, 2)) + "%")
            print("Coverage [PASS/(PASS+FAIL+UNSUPPORTED)]: " + str(round(coverageTotal, 2)) + "%")
            print(Colors.GREEN)
            print("==================================================")
            print(Colors.CYAN)

        print("Total Time(s): " + str(end - start))
        print(Colors.RESET)


def runTestTheWorld(options, args):
    stats = {"[PASS]": 0, "[FAILED]": 0, "[UNSUPPORTED]": 0}

    for t in __TEST_THE_WORLD__:
        command = options
        if t.testParameters:
            for testParam in t.testParameters:
                command += " " + testParam

        command = TORNADO_CMD + " --jvm \"" + command + "\" "

        command = appendTestRunnerClassToCmd(command, args)
        command = command + " --params \"" + t.testName
        if t.testMethods:
            for testMethod in t.testMethods:
                testMethodCmd = command + "#" + testMethod + "\""
                if (args.fast):
                    os.system(testMethodCmd)
                else:
                    print(testMethodCmd)
                    stats = runCommandWithStats(testMethodCmd, stats)
        elif (args.fast):
            command += "\""
            os.system(command)
        else:
            command += "\""
            print(command)
            stats = runCommandWithStats(command, stats)

    return stats


def runWithJUnit(args):
    """ Run the tests using JUNIT """

    if (args.testClass != None):
        command = appendTestRunnerClassToCmd(TORNADO_CMD, args)
        command = command + " --params \"" + args.testClass + "\""
        os.system(command)
    else:
        runTestTheWorldWithJunit(args)


def runTestTheWorldWithJunit(args):
    for t in __TEST_THE_WORLD__:
        command = TORNADO_CMD
        if t.testParameters:
            for testParam in t.testParameters:
                command += " " + testParam

        command = " --jvm \"" + command + "\" "

        command = appendTestRunnerClassToCmd(command, args)
        command += " --params \"" + t.testName + "\""
        if t.testMethods:
            for testMethod in t.testMethods:
                print(
                    "Unable to run specific test methods with the default JUnit runner: " + t.testName + "#" + testMethod)
        else:
            os.system(command)


def parseArguments():
    """ Parse command line arguments """
    parser = argparse.ArgumentParser(description='Tool to execute tests in Tornado')
    parser.add_argument('testClass', nargs="?", help='testClass#method')
    parser.add_argument('--version', action="store_true", dest="version", default=False, help="Print version")
    parser.add_argument('--verbose', "-V", action="store_true", dest="verbose", default=False,
                        help="Run test in verbose mode")
    parser.add_argument("--ea", "--enableassertions", action="store_true", dest="enable_assertions", default=False,
                        help="Enable Tornado assertions")
    parser.add_argument('--threadInfo', action="store_true", dest="threadInfo", default=False,
                        help="Print thread information")
    parser.add_argument('--printKernel', "-pk", action="store_true", dest="printKernel", default=False,
                        help="Print OpenCL kernel")
    parser.add_argument('--junit', action="store_true", dest="junit", default=False,
                        help="Run within JUnitCore main class")
    parser.add_argument('--igv', action="store_true", dest="igv", default=False, help="Dump GraalIR into IGV")
    parser.add_argument('--igvLowTier', action="store_true", dest="dumpIGVLastTier", default=False,
                        help="Dump OpenCL Low-TIER GraalIR into IGV")
    parser.add_argument('--printBytecodes', "-pbc", action="store_true", dest="printBytecodes", default=False,
                        help="Print TornadoVM internal bytecodes")
    parser.add_argument('--debug', "-d", action="store_true", dest="debugTornado", default=False,
                        help="Enable the Debug mode in Tornado")
    parser.add_argument('--fullDebug', action="store_true", dest="fullDebug", default=False,
                        help="Enable the Full Debug mode. This mode is more verbose compared to --debug only")
    parser.add_argument('--fast', "-f", action="store_true", dest="fast", default=False, help="Visualize Fast")
    parser.add_argument('--device', dest="device", default=None, help="Set an specific device. E.g `s0.t0.device=0:1`")
    parser.add_argument('--printExec', dest="printExecution", action="store_true", default=False,
                        help="Print OpenCL Kernel Execution Time")
    parser.add_argument('--jvm', "-J", dest="jvmFlags", required=False, default=None,
                        help="Pass options to the JVM e.g. -J=\"-Ds0.t0.device=0:1\"")
    args = parser.parse_args()
    return args


def writeStatusInFile():
    f = open(".unittestingStatus", "w")
    if (__TEST_NOT_PASSED__):
        f.write("FAIL")
    else:
        f.write("OK")
    f.close()


def getJavaVersion():
    # Get the java version
    return subprocess.Popen(javaHome + '/bin/java -version 2>&1 | awk -F[\\\"\.] -v OFS=. \'NR==1{print $2,$3}\'',
                            stdout=subprocess.PIPE, shell=True).communicate()[0].decode('utf-8')[:-1]


def main():
    args = parseArguments()

    if (args.version):
        print(__VERSION__)
        sys.exit(0)
    global javaVersion
    javaVersion = getJavaVersion()

    if (args.enable_assertions):
        global TORNADO_CMD
        TORNADO_CMD += ENABLE_ASSERTIONS

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
