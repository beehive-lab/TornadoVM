#!/usr/bin/env python3

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2023, APT Group, Department of Computer Science,
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

import os
import subprocess

VERSION = "23.0.1"
COMPILER_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/compiler/compiler/{VERSION}/compiler-{VERSION}.jar"
COMPILER_MANAGEMENT_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/compiler/compiler-management/{VERSION}/compiler-management-{VERSION}.jar"
GRAAL_SDK_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/sdk/graal-sdk/{VERSION}/graal-sdk-{VERSION}.jar"
TRUFFLE_API_JAR_URL = f"https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/{VERSION}/truffle-api-{VERSION}.jar"

# Create a directory if it doesn't exist
if not os.path.exists('graalJars'):
    print("Creating directory graalJars under", os.getcwd())
    os.mkdir('graalJars')


# Function to download a file using wget
def download_with_wget(url, output_path):
    wget_command = ['wget', '-P', output_path, url]
    subprocess.run(wget_command, check=True)


# Download compiler.jar
if not os.path.exists(f'graalJars/compiler-{VERSION}.jar'):
    print(f"Downloading jar file for the graal compiler to graalJars/")
    download_with_wget(COMPILER_JAR_URL, 'graalJars')

# Download compiler-management.jar
if not os.path.exists(f'graalJars/compiler-management-{VERSION}.jar'):
    print(f"Downloading jar file for the graal compiler management bean to graalJars/")
    download_with_wget(COMPILER_MANAGEMENT_JAR_URL, 'graalJars')

# Download graal-sdk.jar
if not os.path.exists(f'graalJars/graal-sdk-{VERSION}.jar'):
    print(f"Downloading jar file for graal sdk to graalJars/")
    download_with_wget(GRAAL_SDK_JAR_URL, 'graalJars')

# Download truffle-api.jar
if not os.path.exists(f'graalJars/truffle-api-{VERSION}.jar'):
    print(f"Downloading jar file for graal truffle to graalJars/")
    download_with_wget(TRUFFLE_API_JAR_URL, 'graalJars')

print("Download complete.")
