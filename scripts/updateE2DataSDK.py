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

import os
import sys

__OUTPUT_DIRECTORY__ = "e2data-tornado-sdk"

__CLONE_INSTRUCTIONS__ = [
	"git clone git@github.com:E2Data/tornado-sdk-internal.git " + __OUTPUT_DIRECTORY__,
]

__LINUX_X86_BRANCH_UPDATE__ = [
	"cd " + __OUTPUT_DIRECTORY__,
	"git checkout linux-x86 ",
	"git checkout osx ",
	"git remote add upstream git@github.com:beehive-lab/tornado-sdk-internal.git",
	"git fetch upstream ",
	"git checkout linux-x86 ",
	"git merge upstream/linux-x86",
	"git push origin linux-x86 ",
]

__OSX_BRANCH_UPDATE__ = [
	"cd " + __OUTPUT_DIRECTORY__,
	"git checkout linux-x86 ",
	"git checkout osx ",
	"git remote add upstream git@github.com:beehive-lab/tornado-sdk-internal.git",
	"git fetch upstream ",
	"git checkout osx ",
	"git merge upstream/osx",
	"git push origin osx ",
]

def cloneRepository():
	command = __CLONE_INSTRUCTIONS__[0]
	os.system(command)

def syncBranch(listOfInstructions):
	cloneRepository()	
	command = listOfInstructions[0]
	for c in listOfInstructions[1:]:
		command = command + " && " + c
	print command
	os.system(command)

def syncE2DataRepository():
	syncBranch(__LINUX_X86_BRANCH_UPDATE__)
	#syncBranch(__OSX_BRANCH_UPDATE__)

def main():
	syncE2DataRepository()

if __name__ == "__main__":
	main()
