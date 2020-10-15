#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2020, APT Group, Department of Computer Science,
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


## Wrapper that sets the Tornado Classpath/Modulepath and invokes the Javac compiler

import os
import subprocess
import sys

try:
	TORNADO_SDK = os.environ["TORNADO_SDK"]
except:
	print("[ERROR] TORNADO_SDK is not defined")
	sys.exit(-1)

try:
	javaHome = os.environ["JAVA_HOME"]
except:
	print("[ERROR] JAVA_HOME is not defined.")
	sys.exit(-1)

try:
	classpathEnviron = os.environ["CLASSPATH"]
except:
	classpathEnviron = ""
	pass

__DEFAULT_MODULES__ = "ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common"
PTX_MODULE = "tornado.drivers.ptx"
OPENCL_MODULE = "tornado.drivers.opencl"

def appendBackendModules():
    global __DEFAULT_MODULES__, PTX_MODULE, OPENCL_MODULE
    availableBackendsFile = TORNADO_SDK + "/etc/tornado.backend"
    with open(availableBackendsFile, "r") as backendsFile:
        backends = backendsFile.read()
        if "ptx-backend" in backends:
            __DEFAULT_MODULES__ += "," + PTX_MODULE

        if "opencl-backend" in backends:
            __DEFAULT_MODULES__ += "," + OPENCL_MODULE

def getJavaVersion():
    return subprocess.Popen(javaHome + '/bin/java -version 2>&1 | awk -F[\\\"\.] -v OFS=. \'NR==1{print $2,$3}\'', stdout=subprocess.PIPE, shell=True).communicate()[0][:-1]

JDK_11_VERSION = "11.0"
JDK_8_VERSION = "1.8"
# Get java version
javaVersion = getJavaVersion()

__JAR_FILES_PATH__ = TORNADO_SDK + "/share/java/tornado/"

def trimModulesParamString(indexToSearch):
    moduleParamIndex = sys.argv.index(indexToSearch)
    parameter = sys.argv[moduleParamIndex + 1]
    # Trim any existing existing quotes from the parameter
    parameter = parameter[1:-1] if (parameter.startswith("\"") and parameter.endswith("\"")) else parameter
    del sys.argv[moduleParamIndex:moduleParamIndex + 2]
    return parameter

def runWithModulepath():
    defaultModulePath = __JAR_FILES_PATH__

    command = javaHome + "/bin/javac "

    if ("--add-modules" in sys.argv):
        addModulesParam = trimModulesParamString("--add-modules")
        command += "--add-modules " + addModulesParam + "," + __DEFAULT_MODULES__
    else:
        command += "--add-modules " + __DEFAULT_MODULES__

    if ("--module-path" in sys.argv):
        modulePathParam = trimModulesParamString("--module-path")
        command += " --module-path \"" + modulePathParam + ":" + defaultModulePath + "\""
    else:
        command += " --module-path \"" + defaultModulePath + "\""

    # We pass the rest of the parameters to javac
    command += " " + ' '.join(sys.argv[1:])
    print(command)
    os.system(command)

def runWithClasspath():

    classPathPrefix= TORNADO_SDK + "/"
    classPathVar = "."

    process = subprocess.Popen(['ls', __JAR_FILES_PATH__], stdout=subprocess.PIPE)
    out, err = process.communicate()
    jarFiles = out.decode('utf-8').split("\n")

    if (classpathEnviron != ""):
    	classPathVar = classPathVar + ":" + classpathEnviron

    for f in jarFiles:
    	classPathVar = classPathVar +  ":" + __JAR_FILES_PATH__ + f

    command = javaHome + "/bin/javac -classpath \"" + classPathVar  +  "\" " + sys.argv[1]
    print(command)
    os.system(command)

useModuleSystem = any("module-info.java" in argument for argument in sys.argv) and javaVersion == JDK_11_VERSION
if (useModuleSystem):
    appendBackendModules()
    runWithModulepath()
else:
    runWithClasspath()

