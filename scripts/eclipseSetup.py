#!/usr/bin/python

import os

class Colors:
	RED   = "\033[1;31m"  
	BLUE  = "\033[1;34m"
	CYAN  = "\033[1;36m"	
	GREEN = "\033[0;32m"
	RESET = "\033[0;0m"
	BOLD    = "\033[;1m"
	REVERSE = "\033[;7m"

## Wait still we merge with @James to include formatting in all the projects
__TORNADO_PROJECTS__ = [
			"benchmarks",
			"collections",
			"drivers/opencl",
			"examples",
			"runtime",
			"unittests",
			"tornado-api",
		   ]

__PATH_TO_ECLIPSE_SETTINGS__ = "scripts/templates/eclipse-settings/files/"

def setEclipseSettings():
	for project in __TORNADO_PROJECTS__:

		print Colors.GREEN + "Generating eclipse files for project: " + Colors.BOLD + project + Colors.RESET

		settingsDirectory = project + "/.settings"

		if (not os.path.exists(settingsDirectory)):
			print  "\tCreating Directory"
			os.mkdir(settingsDirectory)

		command = "cp " + __PATH_TO_ECLIPSE_SETTINGS__ + "* " + project +  "/.settings/" 
		print "\t" + command 
		os.system(command)

if __name__ == "__main__":

	setEclipseSettings()

