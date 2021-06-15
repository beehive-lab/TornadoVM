#!/usr/bin/env python2.7

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
# Authors: Michalis Papadimitriou
#

import csv
import sys


def split(infile, outfile):
	with open(infile, 'r') as f:
		reader = f.readlines()
		with open(outfile, 'w') as out:
			o = csv.writer(out)
			for row in reader:
				line = row.split(',')
				temp = []
				first = line[0].split('=')[1].strip()
				lista = first.split('-')
				for a in lista:
					temp.append(a)
				for el in line[1:]:
					asdf = el.split('=')
					temp.append(asdf[1].strip())
				o.writerow(temp)


def main(outputExtension="out2csv.txt"):
	filename = sys.argv[1]
	split(filename, outputExtension)


if __name__ == '__main__':
	main()
