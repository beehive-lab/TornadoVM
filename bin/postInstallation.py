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
import shutil
import subprocess

# Execute the Bash script to update PATHs
subprocess.run(["bash", "./bin/updatePATHS"])

# Update the compiled backends file
selected_backends = "your_selected_backends_here"  # Replace with your selected backends
TORNADO_SDK = os.environ.get("TORNADO_SDK")
JAVA_VERSION_OUTPUT = os.environ.get("JAVA_VERSION_OUTPUT")
JAVA_VERSION = os.environ.get("JAVA_VERSION")

if TORNADO_SDK:
    tornado_backend_path = os.path.join(TORNADO_SDK, "etc", "tornado.backend")
    with open(tornado_backend_path, "w") as backend_file:
        backend_file.write(f"tornado.backends={selected_backends}\n")
else:
    print("TORNADO_SDK environment variable is not set.")

# Place the Graal jars in the TornadoVM distribution only if the JDK 11+ rule is used.
if JAVA_VERSION and JAVA_VERSION_OUTPUT:
    if "GraalVM" not in JAVA_VERSION_OUTPUT and not JAVA_VERSION.startswith("1.8"):
        if TORNADO_SDK:
            graal_jars_directory = os.path.join(TORNADO_SDK, "share", "java", "graalJars")
            os.makedirs(graal_jars_directory, exist_ok=True)

            # Construct the source directory path based on the current working directory
            graal_jars_source_directory = os.path.join(os.getcwd(), "graalJars")

            for jar_file in os.listdir(graal_jars_source_directory):
                if jar_file.endswith(".jar"):
                    source_path = os.path.join(graal_jars_source_directory, jar_file)
                    destination_path = os.path.join(graal_jars_directory, jar_file)
                    shutil.copy(source_path, destination_path)
        else:
            print("TORNADO_SDK environment variable is not set.")
else:
    print("JAVA_VERSION or JAVA_VERSION_OUTPUT environment variable is not set.")
