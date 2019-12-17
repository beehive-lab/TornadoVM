#!/usr/bin/env python2.7

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


## Wrapper that sets the Tornado Classpath/Modulepath and invokes the Javac compiler

import subprocess
import os
import sys

try:
	TORNADO_SDK = os.environ["TORNADO_SDK"]
except:
	print "[ERROR] TORNADO_SDK is not defined"
	sys.exit(-1)

try:
	javaHome = os.environ["JAVA_HOME"]
except:
	print "[ERROR] JAVA_HOME is not defined"
	sys.exit(-1)

try:
	classpathEnviron = os.environ["CLASSPATH"]
except:
	classpathEnviron = ""
	pass

JDK_11_VERSION = "11.0"
JDK_8_VERSION = "1.8"
# Get java version
javaVersion = subprocess.Popen(javaHome + '/bin/java -version 2>&1 | awk -F[\\\"\.] -v OFS=. \'NR==1{print $2,$3}\'', stdout=subprocess.PIPE, shell=True).communicate()[0][:-1]

jarFilesPath = TORNADO_SDK + "/share/java/tornado/"

def runWithModulepath():
    defaultModulePath = jarFilesPath
    defaultModules = "ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.opencl,tornado.drivers.opencl"
    print("Using modulepath")

    command = javaHome + "/bin/javac "

    if ("--add-modules" in sys.argv):
        addModulesIndex = sys.argv.index("--add-modules")
        addModulesParam = sys.argv[addModulesIndex + 1]
        addModulesParam = addModulesParam[1:-1] if (addModulesParam.startswith("\"") and addModulesParam.endswith("\"")) else addModulesParam
        command += "--add-modules " + addModulesParam + "," + defaultModules
        del sys.argv[addModulesIndex:addModulesIndex + 2]
    else:
        command += "--add-modules " + defaultModules

    if ("--module-path" in sys.argv):
        modulePathIndex = sys.argv.index("--module-path")
        modulePathParam = sys.argv[modulePathIndex + 1]
        modulePathParam = modulePathParam[1:-1] if (modulePathParam.startswith("\"") and modulePathParam.endswith("\"")) else modulePathParam
        command += " --module-path \"" + modulePathParam + ":" + defaultModulePath + "\""
        del sys.argv[modulePathIndex:modulePathIndex + 2]
    else:
        command += " --module-path \"" + defaultModulePath + "\""

    # We pass the rest of the parameters to javac
    command += " " + ' '.join(sys.argv[1:])
    print(command)
    os.system(command)

def runWithClasspath():
    print("Using classpath")

    classPathPrefix= TORNADO_SDK + "/"
    classPathVar = "."

    process = subprocess.Popen(['ls', jarFilesPath], stdout=subprocess.PIPE)
    out, err = process.communicate()
    jarFiles = out.split("\n")

    if (classpathEnviron != ""):
    	classPathVar = classPathVar + ":" + classpathEnviron

    for f in jarFiles:
    	classPathVar = classPathVar +  ":" + jarFilesPath + f

    command = javaHome + "/bin/javac -classpath \"" + classPathVar  +  "\" " + sys.argv[1]
    print command
    os.system(command)

useModuleSystem = any("module-info.java" in argument for argument in sys.argv) and javaVersion == JDK_11_VERSION
if (useModuleSystem):
    runWithModulepath()
else:
    runWithClasspath()

