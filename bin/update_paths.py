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


def update_tornado_paths():
    """
    Update PATH and TORNADO_SDK symbolic links to the latest Tornado SDK.

    This function determines the latest Tornado SDK in the 'dist/tornado-sdk/' directory
    and updates the symbolic links 'bin' and 'sdk' to point to the latest SDK version.

    Raises:
        FileNotFoundError: If no files are found in 'dist/tornado-sdk/' directory.
    """
    tornado_sdk_dir = os.path.join("dist", "tornado-sdk")
    files_in_sdk_dir = os.listdir(tornado_sdk_dir)
    if files_in_sdk_dir:
        file = files_in_sdk_dir[0]
    else:
        raise FileNotFoundError(f"No files found in '{tornado_sdk_dir}' directory")

    log_messages = []  # Create an empty list to store log messages

    log_messages.append(
        "###########################################################################"
    )
    log_messages.append("\x1b[32mTornado build success\x1b[39m")
    log_messages.append(f"Updating PATH and TORNADO_SDK to {file}")

    # Change to the 'bin' directory
    os.chdir("bin")

    try:
        # Get the commit hash
        commit = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], universal_newlines=True
        ).strip()
        log_messages.append(f"Commit  : {commit}")
    except subprocess.CalledProcessError:
        log_messages.append("Warning: Unable to retrieve commit hash.")

    # Remove existing 'bin' and 'sdk' links
    for symlink in ["bin", "sdk"]:
        try: ## hack to avoid Windows junction handling
            os.unlink(symlink)
        except FileNotFoundError:
            pass

    # Change back to the parent directory
    os.chdir("..")

    # Create symbolic links 'bin' and 'sdk'
    if os.name == 'nt':
        subprocess.run(["mklink", "/j", os.path.join("bin", "bin"), os.path.join(os.getcwd(), tornado_sdk_dir, file, "bin")], shell=True)
        subprocess.run(["mklink", "/j", os.path.join("bin", "sdk"), os.path.join(os.getcwd(), tornado_sdk_dir, file)], shell=True)
    else:
        os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file, "bin"), "bin/bin")
        os.symlink(os.path.join(os.getcwd(), tornado_sdk_dir, file), "bin/sdk")

    log_messages.append(
        "###########################################################################"
    )

    # Print all log messages at the end
    for message in log_messages:
        print(message)


if __name__ == "__main__":
    update_tornado_paths()
