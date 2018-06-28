#!/usr/bin/bash

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


cd $TORNADO_SDK 

cd share/java/tornado/

mvn install:install-file -Dfile=tornado-collections-0.0.2-SNAPSHOT.jar -DgroupId=tornado-collections -DartifactId=tornado-collections -Dversion=0.0.2-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=tornado-runtime-0.0.2-SNAPSHOT.jar -DgroupId=tornado-runtime -DartifactId=tornado-runtime -Dversion=0.0.2-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=tornado-drivers-opencl-0.0.2-SNAPSHOT.jar -DgroupId=tornado-drivers-opencl -DartifactId=tornado-drivers-opencl -Dversion=0.0.2-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=tornado-drivers-opencl-jni-0.0.2-SNAPSHOT-libs.jar -DgroupId=tornado-drivers-opencl-jni -DartifactId=tornado-drivers-opencl-jni -Dversion=0.0.2-SNAPSHOT -Dpackaging=jar


echo "Torando SDK installed locally"

