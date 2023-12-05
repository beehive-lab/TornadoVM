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

import subprocess
import sys


def run_git_command(command):
    try:
        result = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
            text=True,
        )
        return result.stdout.strip().split("\n")
    except subprocess.CalledProcessError as e:
        print(f"Error executing command: {e.stderr}")
        return []


def run_rstcheck(file):
    rstcheck_command = ["rstcheck", "--ignore-languages", "bash", file]
    try:
        subprocess.run(rstcheck_command, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Error executing rstcheck for file: {file}")
        sys.exit(1)


def main():
    # Define the git command
    git_command = ["git", "diff", "--diff-filter=d", "--cached", "--name-only"]

    # Run the git command and get the list of changed rst files
    changed_files = run_git_command(git_command)
    rst_files = [file for file in changed_files if file.endswith(".rst")]

    # Iterate over the list and run rstcheck for each rst file
    for file in rst_files:
        print(f"Running rstcheck for file: {file}")
        run_rstcheck(file)


if __name__ == "__main__":
    main()
