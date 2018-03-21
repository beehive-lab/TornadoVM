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
import subprocess

__ALLOWED_BRANCH__ = "develop"

def checkUnittestStatus():
	f = open(".unittestingStatus")
	content = f.read()
	if (content.startswith("OK")):
		return True
	else:
		return False

def newCommit():
	print "New Commit"

def push():
	print "Push new SDK"

def publicNewVersionSDK():

	cmd = "git rev-parse --abbrev-ref HEAD".split(" ")
	p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = p.communicate()

	currentBranchName = out

	## For now, we only publish if there is a new version in develop
	if (currentBranchName == __ALLOWED_BRANCH__):
		newCommit()
		push()
	else:
		print "Version not publish because the current branch is not " + __ALLOWED_BRANCH__


def main():
	status = checkUnittestStatus()
	if (status):
		publicNewVersionSDK()
	else:
		print "Do not push into the SDK repository"


if __name__ == "__main__":
	main()	

