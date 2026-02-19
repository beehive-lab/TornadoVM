#!/usr/bin/env python3

#
# Copyright (c) 2013-2024, APT Group, Department of Computer Science,
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
import shutil

def runPyInstaller(currentDirectory, tornadoSDKPath):
    path = os.path.join(tornadoSDKPath, "bin")
    os.chdir(path)

    ## List of scripts to compile
    scripts = ["tornado.py", "tornado-test", "tornado-benchmarks.py"]
    for s in scripts:
        print("creating " + s  + " binary ....  "),
        command = "pyinstaller " + s + " --onefile"
        os.system(command)
        print("ok ")

    # Move .exe files from dist/ subdirectory to bin/ directory
    dist_dir = os.path.join(path, "dist")
    if os.path.exists(dist_dir):
        for exe_file in os.listdir(dist_dir):
            if exe_file.endswith(".exe"):
                src = os.path.join(dist_dir, exe_file)
                dst = os.path.join(path, exe_file)
                shutil.move(src, dst)
                print(f"Moved {exe_file} to bin directory")

    os.chdir(currentDirectory)
