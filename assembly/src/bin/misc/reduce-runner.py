#!/usr/env python 

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

__OPTIONS__ = " -Dtornado.opencl.timer.kernel=True " 

command_base_mult = "tornado " + __OPTIONS__ + " uk.ac.manchester.tornado.examples.reductions.ReductionMultiplyFloats "
command_base_sum =  "tornado " + __OPTIONS__ + " uk.ac.manchester.tornado.examples.reductions.ReductionSumFloats "

size = 4096

sizes = []

for i in range(0,15):
	sizes.append(size)
	size = size * 2

print sizes

## Run MULT Version
print "MULT\n"
for i in sizes:
	command = command_base_mult + str(i)
	print command
	os.system(command)

## Run SUM version
print "SUM\n"
for i in sizes:
	command = command_base_sum + str(i)
	print command
	os.system(command)

