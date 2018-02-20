#!/usr/bin/env python

## Wrapper that sets the Tornado ClassPaths and Invoke Javac compiler

import subprocess
import os
import sys

try:
	preffix = os.environ["TORNADO_SDK"]
except:
	print "[ERROR] TORNADO_SDK is not defined"
	sys.exit(-1)

classPathPrefix= preffix + "/"
jarFilesPaths = classPathPrefix + "share/java/tornado"

classPathVar = "."

process = subprocess.Popen(['ls', jarFilesPaths], stdout=subprocess.PIPE)
out, err = process.communicate()
files = out.split("\n")

for f in files:
	classPathVar = classPathVar +  ":" + classPathPrefix + "/share/java/tornado/" +  f 


command = "javac -classpath \"" + classPathVar  +  "\" " + sys.argv[1]

print command
os.system(command) 

