#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornado
#
# Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
# Authors: Juan Fumero
#

import sys
import os
import subprocess

__ALLOWED_BRANCHES__ = ("origin/feature/56-sdk/juan", "origin/develop", "origin/master", "feature/56-sdk/juan", "develop", "master")
__GIT_URL_REPOSITORY__ = "git@github.com:beehive-lab/tornado-sdk-internal.git"
__TEMPORAL_DIRECTORY__ = "temporal/"
__OUTPUT_FILE__ = ".unittestingStatus"
__REPOSITORY_NAME__ = "tornado-sdk-internal"
__MESSAGE_HEADER__ = '"[AUTOMATIC] '

__EMULATION__ = True

__LINUX_BRANCH__ = "linux-x86"

def checkUnittestStatus():
	try:
		f = open(".unittestingStatus")
		content = f.read()
		if (content.startswith("OK")):
			return True
		else:
			return False
	except:
		print("Log file for unittests not found - execute `make tests` first")
		sys.exit(1)


def executeCommand(command):
	command = command.split(" ")
	p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()
	print "OUTPUT: " + out
	print "STDERR: " + err
	return (out, err)
	

def newCommit():

	if (os.path.exists(__TEMPORAL_DIRECTORY__) == False):
		os.makedirs(__TEMPORAL_DIRECTORY__)

	## Clone existing version
	command = "git clone -b " + __LINUX_BRANCH__ + " " + __GIT_URL_REPOSITORY__ + " " + __TEMPORAL_DIRECTORY__ + __REPOSITORY_NAME__ 
	executeCommand(command)

	## Copy new files
	tornadoSDK = os.environ['TORNADO_SDK']
	command = "cp -R " + tornadoSDK + "/* " + __TEMPORAL_DIRECTORY__ + __REPOSITORY_NAME__
	print command
	os.system(command)

	## Get message of the last commit in Tornado
	command = "git log --format=%B -n 1"
	out, err =executeCommand(command)
	message = __MESSAGE_HEADER__ + out + '"'

	## Commit new version
	command = "cd " + __TEMPORAL_DIRECTORY__ + __REPOSITORY_NAME__ + "&& git commit -a -m " + message
	os.system(command)
		

def push():
	command = "cd " + __TEMPORAL_DIRECTORY__ + __REPOSITORY_NAME__ + " && git push -u origin " + __LINUX_BRANCH__
	print command 
	if ( __EMULATION__ == False):
		os.system(command)
	

def clean():
	command = "rm -Rf " + __TEMPORAL_DIRECTORY__ + __REPOSITORY_NAME__
	#os.system(command)
	command = "mv " + __OUTPUT_FILE__ + " .lastUnitTestsStatus"
	os.system(command)


def publicNewVersionSDK():

	cmd = "git rev-parse --abbrev-ref HEAD".split(" ")
	p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()

	## Remove the last character \n
	currentBranchName = out[0:-1]

	## For now, we only publish if there is a new version in develop
	if (currentBranchName in __ALLOWED_BRANCHES__):
		newCommit()
		push()
		clean()
	else:
		print "Version not publish because the current branch is not " + __ALLOWED_BRANCH__


def main():
	status = checkUnittestStatus()

	## Patterm: status = status and anotherCheck() 

	if (status):
		publicNewVersionSDK()
	else:
		print "Do not push into the SDK repository"


if __name__ == "__main__":
	main()	

