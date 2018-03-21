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

__ALLOWED_BRANCH__ = "feature/56-sdk/juan"
__GIT_URL_REPOSITORY__ = "git@github.com:beehive-lab/tornado-sdk-internal.git"
__MESSAGE__ = '"[AUTOMATIC] TORNADO-SDK-LINUX"'

__EMULATION__ = True

__LINUX_BRANCH__ = "linux-x86"

def checkUnittestStatus():
	f = open(".unittestingStatus")
	content = f.read()
	if (content.startswith("OK")):
		return True
	else:
		return False

def executeCommand(command):
	command = command.split(" ")
	p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()
	print "OUTPUT: " + out
	print "STDERR: " + err
	return (out, err)
	

def newCommit():
	## Clone existing version
	command = "git clone " + __GIT_URL_REPOSITORY__ + " /tmp/tornado-sdk" 
	executeCommand(command)

	## change-branch
	command = "cd /tmp/tornado-sdk git checkout " + __LINUX_BRANCH__
	
	## Copy new files
	tornadoSDK = os.environ['TORNADO_SDK']
	command = "cp -R " + tornadoSDK + "/* /tmp/tornado-sdk/"
	print command
	os.system(command)

	## Commit new version
	command = "cd /tmp/tornado-sdk && git commit -a -m " + __MESSAGE__
	os.system(command)
		

def push():

	command = "cd /tmp/tornado-sdk && git push -u origin " + __LINUX_BRANCH__
	print command 
	if ( __EMULATION__ == False):
		os.system(command)
	

def clean():
	command = "rm -Rf /tmp/tornado-sdk"
	os.system(command)

def publicNewVersionSDK():

	cmd = "git rev-parse --abbrev-ref HEAD".split(" ")
	p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()

	currentBranchName = out

	## For now, we only publish if there is a new version in develop
	if (currentBranchName.startswith(__ALLOWED_BRANCH__)):
		newCommit()
		push()
		#clean()
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

