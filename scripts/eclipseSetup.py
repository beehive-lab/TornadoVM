#!/usr/bin/python3

import os

class Colors:
	RED   = "\033[1;31m"  
	BLUE  = "\033[1;34m"
	CYAN  = "\033[1;36m"	
	GREEN = "\033[0;32m"
	RESET = "\033[0;0m"
	BOLD    = "\033[;1m"
	REVERSE = "\033[;7m"

__TORNADO_PROJECTS__ = [
			"benchmarks",
			"matrices",
			"drivers/drivers-common"
			"drivers/ptx"
			"drivers/opencl",
			"examples",
			"runtime",
			"unittests",
			"tornado-api",
			"tornado-annotation",
	]

__PATH_TO_ECLIPSE_SETTINGS__ = "scripts/templates/eclipse-settings/files/"

def setEclipseSettings():
	for project in __TORNADO_PROJECTS__:

		print(Colors.GREEN + "Generating eclipse files for the module: " + Colors.BOLD + project + Colors.RESET)

		settingsDirectory = project + "/.settings"

		if (not os.path.exists(settingsDirectory)):
			print("\tCreating Directory")
			os.mkdir(settingsDirectory)

		command = "cp " + __PATH_TO_ECLIPSE_SETTINGS__ + "* " + project +  "/.settings/" 
		print("\t" + command)
		os.system(command)

if __name__ == "__main__":
	setEclipseSettings()
