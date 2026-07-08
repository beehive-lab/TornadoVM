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
    import tempfile

    bin_dir = os.path.join(tornadoSDKPath, "bin")
    work_dir = tempfile.mkdtemp(prefix="pyinstaller-build-")

    ## List of scripts to compile
    scripts = ["tornado.py", "tornado-test", "tornado-benchmarks.py"]
    for s in scripts:
        print("creating " + s + " binary ....  "),
        script_path = os.path.join(bin_dir, s)
        command = (
            f'pyinstaller "{script_path}" --onefile '
            f'--distpath "{bin_dir}" '
            f'--workpath "{work_dir}" '
            f'--specpath "{work_dir}"'
        )
        # Run from currentDirectory (repo root) -- NOT from bin_dir -- so the
        # cwd never looks like a PyInstaller output tree (which trips
        # PyInstaller's own "don't run me from inside dist/" safety check
        # when tornadoSDKPath itself contains a 'dist' segment, as it does
        # for our per-backend SDK build output paths).
        os.system(command)
        print("ok ")

    shutil.rmtree(work_dir, ignore_errors=True)
