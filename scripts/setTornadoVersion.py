#!/usr/bin/env python 

""" Script to update the tornado version using the short revision 
    number in git. 
	Eg: 0.0.2-74e80a5

	How to run: 
	    $ python scripts/setTornadoVersion.py 
"""
import subprocess

## Line to update with the last snapshot version (short)
__MAJOR__ = "0.0.2"
__LAST_REVISION__ = "74e80a5"

## Do not change this line
__CURRENT_VERSION__ = __MAJOR__ + "-" + __LAST_REVISION__

def updateVersion():
	""" It updates the version replacing SNAPSHOT
	    for the reference number of the last comming
 	"""

	command = "git rev-parse --short HEAD"
	referenceNumber = subprocess.check_output(command, shell=True)

	newReference = __CURRENT_VERSION__.replace(__LAST_REVISION__, referenceNumber)
	newReference = newReference[:-1]
	print "New reference number: " + str(newReference) 

	command = "find -iname pom.xml"
	print "Files to inspect:"
	output = subprocess.check_output(command, shell=True)
	for f in output.split("\n"):
		print "\t" + f

	command = "find -iname pom.xml -print | xargs sed -i 's/" + __CURRENT_VERSION__ + "/" + newReference + "/g'"
	print command

	output = subprocess.check_output(command, shell=True)


if __name__ == "__main__":
	updateVersion()
