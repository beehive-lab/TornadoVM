#!/usr/bin/python3

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


class Colors:
    RED = "\033[1;31m"
    BLUE = "\033[1;34m"
    CYAN = "\033[1;36m"
    GREEN = "\033[0;32m"
    RESET = "\033[0;0m"
    BOLD = "\033[;1m"
    REVERSE = "\033[;7m"


__TORNADO_PROJECTS__ = [
    "benchmarks",
    "matrices",
    "drivers/drivers-common" "drivers/ptx" "drivers/opencl",
    "examples",
    "runtime",
    "unittests",
    "tornado-api",
    "tornado-annotation",
]

__PATH_TO_ECLIPSE_SETTINGS__ = "scripts/templates/eclipse-settings/files/"


def setEclipseSettings():
    for project in __TORNADO_PROJECTS__:

        print(
            Colors.GREEN
            + "Generating eclipse files for the module: "
            + Colors.BOLD
            + project
            + Colors.RESET
        )

        settingsDirectory = project + "/.settings"

        if not os.path.exists(settingsDirectory):
            print("\tCreating Directory")
            os.mkdir(settingsDirectory)

        command = "cp " + __PATH_TO_ECLIPSE_SETTINGS__ + "* " + project + "/.settings/"
        print("\t" + command)
        os.system(command)


if __name__ == "__main__":
    setEclipseSettings()
