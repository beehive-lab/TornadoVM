#!/usr/bin/env python3

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
