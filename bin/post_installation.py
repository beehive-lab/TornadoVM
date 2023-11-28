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


def update_paths():
    """
    Run the 'update_paths.py' script.

    This function executes the 'update_paths.py' script to update environment paths.
    """
    subprocess.run(["python3", "./bin/update_paths.py"], stdout=subprocess.PIPE)


def update_backend_file(selected_backends_str):
    """
    Update the 'tornado.backend' file with selected backends.

    This function updates the 'tornado.backend' file in the Tornado SDK with the selected backend configurations.

    Args:
    selected_backends_str (str): Comma-separated string of selected backends.
    """
    tornado_sdk_path = os.environ.get("TORNADO_SDK")
    backend_file_path = os.path.join(tornado_sdk_path, "etc", "tornado.backend")
    with open(backend_file_path, "w") as backend_file:
        backend_file.write(f"tornado.backends={selected_backends_str}")


def copy_graal_jars():
    """
    Copy GraalVM JAR files to the Tornado SDK.

    This function checks the Java version and copies GraalVM JAR files to the Tornado SDK's 'share/java/graalJars'
    directory if the Java environment is not GraalVM.
    """
    tornado_sdk_path = os.environ.get("TORNADO_SDK")
    java_version_output = subprocess.check_output(
        ["java", "-version"], stderr=subprocess.STDOUT, universal_newlines=True
    )

    if "GraalVM" not in java_version_output:
        graal_jars_dir = os.path.join(os.getcwd(), "graalJars")
        destination_dir = os.path.join(tornado_sdk_path, "share", "java", "graalJars")
        os.makedirs(destination_dir, exist_ok=True)
        for filename in os.listdir(graal_jars_dir):
            source_file = os.path.join(graal_jars_dir, filename)
            destination_file = os.path.join(destination_dir, filename)
            if os.path.isfile(source_file):
                subprocess.run(["cp", source_file, destination_file])


def main():
    """
    Main function to update paths, backend file, and copy GraalVM JAR files.

    This function is the entry point of the script and calls other functions to perform necessary updates.
    """
    update_paths()
    selected_backends_str = os.environ.get("selected_backends", "")
    update_backend_file(selected_backends_str)
    copy_graal_jars()


if __name__ == "__main__":
    main()
