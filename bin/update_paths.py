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


def update_tornado_paths():
    """
    Update PATH and TORNADO_SDK symbolic links to the latest Tornado SDK.

    This function determines the latest Tornado SDK in the 'dist/tornado-sdk/' directory
    and updates the symbolic links 'bin' and 'sdk' to point to the latest SDK version.

    :raises FileNotFoundError: If no files are found in 'dist/tornado-sdk/' directory.
    """
    tornado_sdk_dir = "dist/tornado-sdk/"
    files_in_sdk_dir = os.listdir(tornado_sdk_dir)
    if files_in_sdk_dir:
        file = files_in_sdk_dir[0]
    else:
        raise FileNotFoundError("No files found in 'dist/tornado-sdk/' directory")

    log_messages = []  # Create an empty list to store log messages

    log_messages.append(
        "###########################################################################"
    )
    log_messages.append("\x1b[32mTornado build success\x1b[39m")
    log_messages.append(f"Updating PATH and TORNADO_SDK to {file}")

    # Change to the 'bin/' directory
    os.chdir("bin/")

    try:
        # Get the commit hash
        commit = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], universal_newlines=True
        ).strip()
        log_messages.append(f"Commit  : {commit}")
    except subprocess.CalledProcessError:
        log_messages.append("Warning: Unable to retrieve commit hash.")

    # Remove existing 'bin' and 'sdk' directories
    for symlink in ["bin", "sdk"]:
        if os.path.islink(symlink):
            os.unlink(symlink)
        elif os.path.isdir(symlink):
            try:
                os.rmdir(symlink)
            except:
                import shutil

                shutil.rmtree(symlink)

    # Change back to the parent directory
    os.chdir("..")

    # Create symbolic links 'bin' and 'sdk'
    os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file, "bin/"), "bin/bin")
    os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file), "bin/sdk")

    log_messages.append(
        "###########################################################################"
    )

    # Print all log messages at the end
    for message in log_messages:
        print(message)


if __name__ == "__main__":
    update_tornado_paths()
