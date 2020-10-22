#!/usr/bin/env bash

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2020, APT Group, Department of Computer Science,
# School of Engineering, The University of Manchester. All rights reserved.
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

if [ ! -d $PWD/graalJars ]; then
  echo "Creating directory graalJars under $PWD"
  mkdir $PWD/graalJars
fi

if [ ! -f $PWD/graalJars/compiler-20.2.0.jar ]; then
  echo "Downloading jar file for the graal compiler to $PWD/graalJars/"
  wget -q --show-progress -P $PWD/graalJars/ https://repo1.maven.org/maven2/org/graalvm/compiler/compiler/20.2.0/compiler-20.2.0.jar
fi

if [ ! -f $PWD/graalJars/compiler-management-20.2.0.jar ]; then
  echo "Downloading jar file for the graal compiler management bean to $PWD/graalJars/"
  wget -q --show-progress -P $PWD/graalJars/ https://repo1.maven.org/maven2/org/graalvm/compiler/compiler-management/20.2.0/compiler-management-20.2.0.jar
fi

if [ ! -f $PWD/graalJars/graal-sdk-20.2.0.jar ]; then
  echo "Downloading jar file for graal sdk to $PWD/graalJars/"
  wget -q --show-progress -P $PWD/graalJars/ https://repo1.maven.org/maven2/org/graalvm/sdk/graal-sdk/20.2.0/graal-sdk-20.2.0.jar
fi

if [ ! -f $PWD/graalJars/truffle-api-20.2.0.jar ]; then
  echo "Downloading jar file for graal truffle to $PWD/graalJars/"
  wget -q --show-progress -P $PWD/graalJars/ https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/20.2.0/truffle-api-20.2.0.jar
fi