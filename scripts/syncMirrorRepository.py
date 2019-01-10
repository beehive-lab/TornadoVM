#!/usr/bin/env python

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
# Authors: Juan Fumero
#

import os
import sys
import localConfiguration

## LOCAL CONFIGURATION FILE EXAMPLE
# File: localConfiguration.py
# #!/usr/bin/python
#   url = "git clone url"
#   outputDirectory = "output"
#   INSTRUCTIONS_BRANCH_A__ = [
#	   "cd output",
# 	   "git push origin linux-x86"
#    ]
#   INSTRUCTIONS_BRANCH_B__ = [
#	   "cd output",
# 	   "git push origin osx"
#    ]
#   blocks_of_instructions = [INSTRUCTIONS_BRANCH_A__, INSTRUCTIONS_BRANCH_B__]

class MetaData:
	
	def __init__(self, repository, outputDirectory, blocks_of_instructions):
		self.repository = repository
		self.outputDirectory = outputDirectory
		self.blocks_of_instructions = blocks_of_instructions

	def getRepository(self):
		return self.repository

	def getOutputDirectory(self):
		return self.outputDirectory
	
	def getBlockOfInstructions(self):
		return self.blocks_of_instructions

def readConfiguration():
	repository = localConfiguration.url
	outputDirectory = localConfiguration.outputDirectory
	blocks_of_instructions = localConfiguration.blocks_of_instructions
	return MetaData(repository, outputDirectory, blocks_of_instructions)

def cloneRepository(metadata):
	command = metadata.getRepository() + " " + metadata.getOutputDirectory()
	print command
	os.system(command)

def syncBranch(listOfInstructions, metadata):
	cloneRepository(metadata)	
	command = listOfInstructions[0]
	for c in listOfInstructions[1:]:
		command = command + " && " + c
	print command
	os.system(command)

def syncRepository(metadata):
	for block in metadata.getBlockOfInstructions():
		syncBranch(block, metadata)

def main():
	metadata = readConfiguration()
	syncRepository(metadata)

if __name__ == "__main__":
	main()
