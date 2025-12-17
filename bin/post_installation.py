#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import subprocess
import config_utils as cutils

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
    tornado_sdk_path = os.environ.get("TORNADOVM_HOME")
    backend_file_path = os.path.join(tornado_sdk_path, "etc", "tornado.backend")
    with open(backend_file_path, "w") as backend_file:
        backend_file.write(f"tornado.backends={selected_backends_str}")


def copy_graal_jars():
    """
    Copy GraalVM JAR files to the Tornado SDK.

    This function checks the Java version and copies GraalVM JAR files to the Tornado SDK's 'share/java/graalJars'
    directory if the Java environment is not GraalVM.
    """
    tornado_sdk_path = os.environ.get("TORNADOVM_HOME")
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
                if os.name == 'nt':
                    subprocess.run(["copy", "/b", "/y", source_file, destination_file], shell=True)
                else:
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
    if os.name == 'nt':
        cutils.runPyInstaller(os.getcwd(), os.environ['TORNADOVM_HOME'])


if __name__ == "__main__":
    main()
