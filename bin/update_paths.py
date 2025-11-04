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
import sys


def select_tornado_sdk(tornado_sdk_dir):
    """
    Select the appropriate TornadoVM SDK from available builds.

    Selection priority:
    1. SDK with 'full' backend (all backends included)
    2. Most recently modified SDK

    The backend information is extracted from the archive filename:
    - tornadovm-1.2.0-opencl-linux-amd64.tar.gz → backend: opencl
    - tornadovm-1.2.0-full-linux-amd64.tar.gz → backend: full

    Args:
        tornado_sdk_dir: Path to the directory containing SDK builds

    Returns:
        str: Name of the selected SDK directory (extracted from archive filename)

    Raises:
        FileNotFoundError: If no SDK archives are found
    """
    # Get all archive files (zip or tar.gz) that start with 'tornadovm-'
    all_items = os.listdir(tornado_sdk_dir)

    # Find tornadovm archives
    sdk_archives = []
    for item in all_items:
        if item.startswith('tornadovm-') and (item.endswith('.zip') or item.endswith('.tar.gz')):
            # Extract the base name (remove extension)
            if item.endswith('.tar.gz'):
                base_name = item[:-7]  # Remove '.tar.gz'
            else:
                base_name = item[:-4]  # Remove '.zip'
            sdk_archives.append(base_name)

    # Remove duplicates (if both .zip and .tar.gz exist)
    sdk_dirs = list(set(sdk_archives))

    if not sdk_dirs:
        raise FileNotFoundError(f"No TornadoVM SDK archives found in '{tornado_sdk_dir}'")

    # Helper function to get archive file modification time
    def get_archive_mtime(base_name):
        # Check for .tar.gz first, then .zip
        tar_gz = os.path.join(tornado_sdk_dir, base_name + '.tar.gz')
        zip_file = os.path.join(tornado_sdk_dir, base_name + '.zip')
        if os.path.exists(tar_gz):
            return os.path.getmtime(tar_gz)
        elif os.path.exists(zip_file):
            return os.path.getmtime(zip_file)
        return 0

    # Prefer 'full' backend if available
    full_backends = [d for d in sdk_dirs if '-full-' in d.lower()]
    if full_backends:
        selected = max(full_backends, key=get_archive_mtime)
        return selected

    # Otherwise, use the most recently modified
    selected = max(sdk_dirs, key=get_archive_mtime)
    return selected


def update_tornado_paths():
    """
    Update PATH and TORNADO_SDK symbolic links to the latest TornadoVM SDK.

    This function determines the appropriate TornadoVM SDK in the 'dist/' directory
    (by examining archive files) and updates the symbolic links 'bin' and 'sdk' to point
    to the selected SDK version.

    The TORNADO_SDK environment variable will point to the selected SDK installation.

    Selection Strategy:
        - Prefers 'full' backend (includes all backends: OpenCL, PTX, SPIR-V)
        - Falls back to most recently built SDK if 'full' not available
        - Backend information is extracted from archive filename

    Raises:
        FileNotFoundError: If no SDK archives are found in 'dist/' directory.
    """
    tornado_sdk_dir = "dist"

    try:
        selected_sdk = select_tornado_sdk(tornado_sdk_dir)
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    log_messages = []  # Create an empty list to store log messages

    # Extract backend info from SDK name for display
    backend_info = "unknown"
    if '-full' in selected_sdk.lower():
        backend_info = "full (all backends)"
    elif '-opencl' in selected_sdk.lower():
        backend_info = "OpenCL"
    elif '-ptx' in selected_sdk.lower():
        backend_info = "PTX"
    elif '-spirv' in selected_sdk.lower():
        backend_info = "SPIR-V"

    log_messages.append(
        "###########################################################################"
    )
    log_messages.append("\x1b[32mTornadoVM build success\x1b[39m")
    log_messages.append(f"Updating PATH and TORNADO_SDK to {selected_sdk}")
    log_messages.append(f"Backend : {backend_info}")

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

    # Find the actual SDK directory inside the outer directory
    # Structure: dist/tornadovm-X.Y.Z-backend-platform/tornadovm-X.Y.Z-backend/
    outer_dir = os.path.join(tornado_sdk_dir, selected_sdk)

    # Look for the inner tornadovm-* directory
    inner_items = os.listdir(outer_dir)
    inner_sdk_dir = None
    for item in inner_items:
        if item.startswith('tornadovm-') and os.path.isdir(os.path.join(outer_dir, item)):
            inner_sdk_dir = item
            break

    if not inner_sdk_dir:
        print(f"Error: No TornadoVM SDK directory found inside '{outer_dir}'", file=sys.stderr)
        sys.exit(1)

    sdk_path = os.path.join(os.getcwd(), outer_dir, inner_sdk_dir)

    # Update TORNADO_SDK environment variable to point to the new SDK
    os.environ['TORNADO_SDK'] = sdk_path

    # Create symbolic links 'bin' and 'sdk'
    if os.name == 'nt':
        subprocess.run(["mklink", "/j", os.path.join("bin", "bin"), os.path.join(sdk_path, "bin")], shell=True)
        subprocess.run(["mklink", "/j", os.path.join("bin", "sdk"), sdk_path], shell=True)
    else:
        os.symlink(os.path.join(sdk_path, "bin"), "bin/bin")
        os.symlink(sdk_path, "bin/sdk")

    log_messages.append(
        "###########################################################################"
    )

    # Print all log messages at the end
    for message in log_messages:
        print(message)


if __name__ == "__main__":
    update_tornado_paths()
