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


## Wrapper that sets the Tornado ClassPaths and Invoke Javac compiler

import subprocess
import os
import sys

try:
	preffix = os.environ["TORNADO_SDK"]
except:
	print "[ERROR] TORNADO_SDK is not defined"
	sys.exit(-1)

try:
	classpathEnviron = os.environ["CLASSPATH"]
except:
	classpathEnviron = ""
	pass

classPathPrefix= preffix + "/"
jarFilesPaths = classPathPrefix + "share/java/tornado"

classPathVar = "."

process = subprocess.Popen(['ls', jarFilesPaths], stdout=subprocess.PIPE)
out, err = process.communicate()
files = out.split("\n")

if (classpathEnviron != ""):
	classPathVar = classPathVar + ":" + classpathEnviron

for f in files:
	classPathVar = classPathVar +  ":" + classPathPrefix + "/share/java/tornado/" +  f 

command = "javac -classpath \"" + classPathVar  +  "\" " + sys.argv[1]

print command
os.system(command) 

