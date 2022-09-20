#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2022, APT Group, Department of Computer Science,
# School of Engineering, The University of Manchester. All rights reserved.
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

from subprocess import Popen, PIPE
import os
import subprocess
import shlex
import sys
import re
import argparse


# ########################################################
# FLAGS FOR TORNADOVM
# ########################################################
__TORNADOVM_DEBUG__ = "-Dtornado.debug=True "
__TORNADOVM_THREAD_INFO__ = "-Dtornado.threadInfo=True "
__TORNADOVM_IGV__         = "-Dgraal.Dump=*:5 -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true "
__TORNADOVM__IGV_LOW_TIER = "-Dgraal.Dump=*:1 -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true -Dtornado.debug.lowtier=True "
__TORNADOVM_PRINT_KERNEL__ = "-Dtornado.print.kernel=True "
__TORNADOVM_PRINT_BC__  = "-Dtornado.print.bytecodes=True"
__TORNADOVM_DUMP_PROFILER__ = "-Dtornado.profiler=True -Dtornado.log.profiler=True -Dtornado.profiler.dump.dir="
__TORNADOVM_ENABLE_PROFILER_SILENT__ = "-Dtornado.profiler=True -Dtornado.log.profiler=True "
__TORNADOVM_ENABLE_PROFILER_CONSOLE__ = "-Dtornado.profiler=True "

__TORNADOVM_PROVIDERS__ = """\
-Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule \
-Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime \
-Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado \
-Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory \
-Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory \
-Dtornado.load.device.implementation.spirv=uk.ac.manchester.tornado.drivers.spirv.runtime.SPIRVDeviceFactory \
-Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor \
-Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel """

# ########################################################
# BACKEND FILES AND MODULES
# ########################################################
__COMMON_EXPORTS__ = "/etc/exportLists/common-exports"
__OPENCL_EXPORTS__ = "/etc/exportLists/opencl-exports"
__PTX_EXPORTS__    = "/etc/exportLists/ptx-exports"
__SPIRV_EXPORTS__  = "/etc/exportLists/spirv-exports"
__TORNADOVM_ADD_MODULES__ = "--add-modules ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common"
__PTX_MODULE__     = "tornado.drivers.ptx"
__OPENCL_MODULE__  = "tornado.drivers.opencl"

# ########################################################
# JAVA FLAGS
# ########################################################
__JAVA_GC_JDK11__    = "-XX:+UseParallelOldGC -XX:-UseBiasedLocking "
__JAVA_GC_JDK16__   = "-XX:+UseParallelGC "
__JAVA_BASE_OPTIONS__ = "-server -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI "

# We do not satisfy the Graal compiler assertions because we only support a subset of the Java specification.
# This allows us have the GraalIR in states which normally would be illegal.
__GRAAL_ENABLE_ASSERTIONS__ = "-ea -da:org.graalvm.compiler... "

class TornadoVMRunnerTool():

    def __init__(self):
        try:
            self.sdk = os.environ["TORNADO_SDK"]
        except:
            print("Please ensure the TORNADO_SDK environment variable is set correctly")
            sys.exit(0)

        try:
            self.java_home = os.environ["JAVA_HOME"]
        except:
            print("Please ensure the JAVA_HOME environment variable is set correctly")
            sys.exit(0)

        self.java_command = self.java_home + "/bin/java"
        self.java_version = self.getJavaVersion()
        self.checkCompatibilityWithTornadoVM()
        self.platform = sys.platform
        self.listOfBackends = self.getInstalledBackends(False)
        self.isGraalVM = self.isGraalVM()

    def getJavaVersion(self):
        versionCommand = subprocess.Popen(shlex.split(self.java_command + " -version"), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, stderr = versionCommand.communicate()
        match = re.search(r"version \"\d+", str(stderr))
        if (match != None):
            version = match.group(0).split("\"")
            version = int(version[1]) 
            return version
        else:
            print("[ERROR] JDK Version not found")
            sys.exit(0)

    def isGraalVM(self):
        versionCommand = subprocess.Popen(shlex.split(self.java_command + " -version"), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, stderr = versionCommand.communicate()
        match = re.search(r"GraalVM", str(stderr))
        if (match != None):
            return True
        else:
            return False

    
    def checkCompatibilityWithTornadoVM(self):
        if (self.java_version == 9 or self.java_version == 10):
            print("TornadoVM does not support Java 9 and 10")
            sys.exit(0)

        if (self.java_version == 8):
            print("[WARNING] TornadoVM using Java 8 is deprecated.")

    def printRelease(self):
        f = open(self.sdk + "/etc/tornado.release")
        releaseVersion = f.read()
        print(releaseVersion)

    def getInstalledBackends(self, verbose=False):
        if (verbose):
            print("Backends installed: ")
        tornadoBackendFilePath = self.sdk + "/etc/tornado.backend"
        listBackends = []
        with open(tornadoBackendFilePath, 'r') as tornadoBackendFile:
            lines = tornadoBackendFile.read().splitlines()
            for line in lines:
                if "tornado.backends" in line:
                    backends = line.replace("tornado.backends=", "").replace("-backend", "")
                    backends = backends.split(",")
                    for b in backends:
                        if (verbose):
                            print("\t - " + b)
                        listBackends.append(b)
    
        return listBackends

    def printVersion(self):
        self.printRelease()
        self.getInstalledBackends(True)

    def buildJavaCommand(self, args):

        javaFlags = ""

        ## build TornadoVM Flags
        tornadoFlags = " "

        if (args.debug):
            tornadoFlags = tornadoFlags + __TORNADOVM_DEBUG__ 

        if (args.threadInfo):
            tornadoFlags = tornadoFlags + __TORNADOVM_THREAD_INFO__

        if (args.printKernel):
            tornadoFlags = tornadoFlags + __TORNADOVM_PRINT_KERNEL__  

        if (args.igv):
            tornadoFlags = tornadoFlags + __TORNADOVM_IGV__

        if (args.igvLowTier):
            tornadoFlags = tornadoFlags + __TORNADOVM__IGV_LOW_TIER

        if (args.printBytecodes):
            tornadoFlags = tornadoFlags + __TORNADOVM_PRINT_BC__

        if (args.enableProfiler != None):
            if (args.enableProfiler == "silent"):
                tornadoFlags = tornadoFlags + __TORNADOVM_ENABLE_PROFILER_SILENT__
            elif (args.enableProfiler == "console"):
                tornadoFlags = tornadoFlags + __TORNADOVM_ENABLE_PROFILER_CONSOLE__
            else:
                print("[ERROR] Please select --enableProfiler <silent|console>")
                sys.exit(0)

        if (args.dumpProfiler != None):
            tornadoFlags = tornadoFlags + __TORNADOVM_DUMP_PROFILER__ + " " + args.dumpProfiler + " "
            

        tornadoFlags = tornadoFlags + "-Djava.library.path=" + self.sdk + "/lib "
        if (self.java_version == 8):
            tornadoFlags = tornadoFlags + " -Djava.ext.dirs=" + self.sdk + "/share/java/tornado "
        else:
            tornadoFlags = tornadoFlags + " --module-path .:"+ self.sdk + "/share/java/tornado "

        tornadoAddModules = __TORNADOVM_ADD_MODULES__

        if (args.enableAssertions):
            javaFlags = javaFlags + __GRAAL_ENABLE_ASSERTIONS__

        javaFlags = javaFlags + " " + __JAVA_BASE_OPTIONS__ + tornadoFlags + __TORNADOVM_PROVIDERS__ + " "

        if (self.java_version == 8):
            javaFlags = javaFlags + " -XX:-UseJVMCIClassLoader "
        else:
            common = self.sdk + __COMMON_EXPORTS__
            opencl = self.sdk + __OPENCL_EXPORTS__
            ptx = self.sdk + __PTX_EXPORTS__
            spirv = self.sdk + __SPIRV_EXPORTS__
            upgradeModulePath = "--upgrade-module-path " + self.sdk + "/share/java/graalJars "

            if (self.isGraalVM == False):
                javaFlags = javaFlags + upgradeModulePath 

            if (self.java_version >= 16):
                javaFlags = javaFlags + __JAVA_GC_JDK16__ 
            else:
                javaFlags = javaFlags + __JAVA_GC_JDK11__ 

            javaFlags = javaFlags + " @" + common + " "

            if ("opencl" in self.listOfBackends):
                javaFlags = javaFlags + "@" + opencl + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("spirv" in self.listOfBackends):
                javaFlags = javaFlags + "@" + opencl + " @" + spirv + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("ptx" in self.listOfBackends):
                javaFlags = javaFlags + "@" + ptx + " "
                tornadoAddModules = tornadoAddModules + "," + __PTX_MODULE__

            javaFlags = javaFlags + tornadoAddModules + " "

        return self.java_command + javaFlags

    
    def executeCommand(self, args):
        javaFlags = self.buildJavaCommand(args)

        if (args.printFlags):
            print(javaFlags)
            sys.exit(0)

        if (args.showDevices):
            command = javaFlags + "uk.ac.manchester.tornado.drivers.TornadoDeviceQuery verbose"
            os.system(command)
            sys.exit(0)

        params = ""
        if (args.applicationParameters != None):
            params = args.applicationParameters

        if (args.moduleApplication != None):
            command = comjavaFlagsmand + " -m " + str(args.moduleApplication) + " " + params
        else:       
            command = javaFlags + " " + str(args.application) + " " + params

        ## Execute the command
        os.system(command)
       

def parseArguments():
    """ Parse command line arguments """
    parser = argparse.ArgumentParser(description='Tool for running TornadoVM Applications')
    parser.add_argument('--version', action="store_true", dest="version", default=False, help="Print version of TornadoVM")
    parser.add_argument('--debug', action="store_true", dest="debug", default=False, help="Enable debug mode")
    parser.add_argument('--threadInfo', action="store_true", dest="threadInfo", default=False, help="Print thread deploy information per task on the accelerator")
    parser.add_argument('--igv', action="store_true", dest="igv", default=False, help="Debug Compilation Graphs using Ideal Graph Visualizer (IGV)")
    parser.add_argument('--igvLowTier', action="store_true", dest="igvLowTier", default=False, help="Debug Low Tier Compilation Graphs using Ideal Graph Visualizer (IGV)")
    parser.add_argument('--printKernel', action="store_true", dest="printKernel", default=False, help="Print generated kernel (OpenCL, PTX or SPIR-V)")
    parser.add_argument('--printBytecodes', action="store_true", dest="printBytecodes", default=False, help="Print the generated internal TornadoVM bytecodes")
    parser.add_argument('--enableProfiler', action="store", dest="enableProfiler", default=None, help="Enable the profiler {silent|console}")
    parser.add_argument('--dumpProfiler', action="store", dest="dumpProfiler", default=None, help="Dump the profiler to a file")
    parser.add_argument('--printFlags', action="store_true", dest="printFlags", default=False, help="Print the TornadoVM flags")
    parser.add_argument('--displayOptions', action="store_true", dest="displayOptions", default=False, help="Print most common TornadoVM options")
    parser.add_argument('--devices', action="store_true", dest="showDevices", default=False, help="Print information about the  accelerators available")
    parser.add_argument('--ea', action="store_true", dest="enableAssertions", default=False, help="Enable assertions")
    parser.add_argument('--jvm', action="store", dest="jvmOptions", default=False, help="Pass JVM options")
    parser.add_argument('-m', action="store", dest="moduleApplication", default=None, help="Application using Java modules")
    parser.add_argument('--params', action="store", dest="applicationParameters", default=None, help="Command-line parameters for the application")
    parser.add_argument("application", nargs="?")
    args = parser.parse_args()
    return args

if __name__ == "__main__":

    args = parseArguments() 

    tornadoVMRunner = TornadoVMRunnerTool()

    if (args.version):
        tornadoVMRunner.printVersion()
        sys.exit(0)

    tornadoVMRunner.executeCommand(args)
