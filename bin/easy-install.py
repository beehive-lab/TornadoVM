#!/usr/bin/python

## Script for Tornado installation

import os
import sys

__TORNADO_ENV_FILE__ = "etc/tornado.env"
__SOURCE_COMMAND__ = ". " + __TORNADO_ENV_FILE__


def tornadoCommandsInstall():
	""" Installation commands for Tornado """
	commands = []
	commands.append(__SOURCE_COMMAND__)
	commands.append("python scripts/generatePom.py")
	commands.append("mvn clean")
	commands.append("mvn install")
	commands.append("cd drivers/opencl/jni-bindings")
	commands.append("autoreconf -f -i -s")
	commands.append("./configure --prefix=${PWD} --with-jdk=${JAVA_HOME} ")
	commands.append("make ")
	commands.append("make install ")

	return commands

def compileTornado():
	
	try:
		os.path.isfile(__TORNADO_ENV_FILE__)
	except:
		print "File " + __TORNADO_ENV_FILE__ + " does not exist. Please provide the config file"
		sys.exit(-1)

	tornadoConfigurationFile = open(__TORNADO_ENV_FILE__)

	commands = tornadoCommandsInstall()	
	installCommand = ""

	for c in commands:
		installCommand = installCommand + c + " && " 

	installCommand = installCommand + " echo done"
	#print installCommand
	os.system(installCommand)

def runTornado():
	""" Run example in tornado """
	os.system(__SOURCE_COMMAND__ + """ 
	tornado tornado.examples.HelloWorld
	""")
	 
if __name__ == "__main__":
	compileTornado()
	runTornado()

	## TODO:
	# Commando for unittest (server and Jenkins compilation)
	# Performance results (Jenkins)
