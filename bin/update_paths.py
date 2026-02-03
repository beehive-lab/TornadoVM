#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, 2026, APT Group, Department of Computer Science,
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

    The SDK can be identified by:
    - Archive files: tornadovm-1.2.0-opencl-linux-amd64.tar.gz or .zip
    - Directories: tornadovm-1.2.0-opencl-mac-aarch64/ (created by Maven assembly)

    Args:
        tornado_sdk_dir: Path to the directory containing SDK builds

    Returns:
        str: Name of the selected SDK directory (base name without extensions)

    Raises:
        FileNotFoundError: If no SDK archives or directories are found
    """
    # Get all items in dist directory
    all_items = os.listdir(tornado_sdk_dir)

    # Find tornadovm archives and directories
    sdk_candidates = []

    # Check for archive files
    for item in all_items:
        if item.startswith('tornadovm-') and (item.endswith('.zip') or item.endswith('.tar.gz')):
            # Extract the base name (remove extension)
            if item.endswith('.tar.gz'):
                base_name = item[:-7]  # Remove '.tar.gz'
            else:
                base_name = item[:-4]  # Remove '.zip'
            sdk_candidates.append(base_name)

    # Check for SDK directories (created by Maven assembly)
    for item in all_items:
        full_path = os.path.join(tornado_sdk_dir, item)
        if item.startswith('tornadovm-') and os.path.isdir(full_path):
            # This is a potential SDK directory
            sdk_candidates.append(item)

    # Remove duplicates (if both .zip and .tar.gz and dir exist)
    sdk_dirs = list(set(sdk_candidates))

    if not sdk_dirs:
        raise FileNotFoundError(f"No TornadoVM SDK archives or directories found in '{tornado_sdk_dir}'")

    # Helper function to get modification time (archive or directory)
    def get_mtime(base_name):
        # Check for .tar.gz first, then .zip, then directory
        tar_gz = os.path.join(tornado_sdk_dir, base_name + '.tar.gz')
        zip_file = os.path.join(tornado_sdk_dir, base_name + '.zip')
        dir_path = os.path.join(tornado_sdk_dir, base_name)

        if os.path.exists(tar_gz):
            return os.path.getmtime(tar_gz)
        elif os.path.exists(zip_file):
            return os.path.getmtime(zip_file)
        elif os.path.isdir(dir_path):
            # For directories, check the inner SDK directory for more accurate mtime
            # Structure: dist/tornadovm-X.Y.Z-backend-platform/tornadovm-X.Y.Z-backend/
            for item in os.listdir(dir_path):
                inner_path = os.path.join(dir_path, item)
                if item.startswith('tornadovm-') and os.path.isdir(inner_path):
                    # Use the mtime of the etc/tornado.backend file if it exists
                    backend_file = os.path.join(inner_path, "etc", "tornado.backend")
                    if os.path.exists(backend_file):
                        return os.path.getmtime(backend_file)
                    return os.path.getmtime(inner_path)
            return os.path.getmtime(dir_path)
        return 0

    # Prefer 'full' backend if available
    full_backends = [d for d in sdk_dirs if '-full-' in d.lower()]
    if full_backends:
        selected = max(full_backends, key=get_mtime)
        return selected

    # Otherwise, use the most recently modified
    selected = max(sdk_dirs, key=get_mtime)
    return selected


def update_tornado_paths():
    """
    Update PATH and TORNADOVM_HOME symbolic links to the latest TornadoVM SDK.

    This function determines the appropriate TornadoVM SDK in the 'dist/' directory
    (by examining archive files) and updates the symbolic links 'bin' and 'sdk' to point
    to the selected SDK version.

    The TORNADOVM_HOME environment variable will point to the selected SDK installation.

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
    log_messages.append(f"Updating PATH and TORNADOVM_HOME to {selected_sdk}")
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
        if os.name == 'nt':
            # On Windows, always try to remove junctions unconditionally.
            # os.path.exists() returns False for broken junctions (target doesn't exist),
            # but the junction file itself still exists and blocks mklink.
            # Using cmd /c ensures proper command execution.
            subprocess.run(f'cmd /c rmdir "{symlink}" 2>nul', shell=True)
        elif os.path.exists(symlink) or os.path.islink(symlink):
            try:
                # On Unix-like systems, unlink works for both files and symlinks
                os.unlink(symlink)
            except (FileNotFoundError, PermissionError, OSError) as e:
                print(f"Warning: Could not remove existing link '{symlink}': {e}")

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

    # Update TORNADOVM_HOME environment variable to point to the new SDK
    os.environ['TORNADOVM_HOME'] = sdk_path

    # Create symbolic links 'bin' and 'sdk'
    if os.name == 'nt':
        bin_link = os.path.join("bin", "bin")
        sdk_link = os.path.join("bin", "sdk")
        bin_target = os.path.join(sdk_path, "bin")

        # Create junctions using cmd /c mklink
        result1 = subprocess.run(f'cmd /c mklink /j "{bin_link}" "{bin_target}"', shell=True, capture_output=True, text=True)
        if result1.returncode != 0:
            print(f"Error creating bin junction: {result1.stderr.strip()}")

        result2 = subprocess.run(f'cmd /c mklink /j "{sdk_link}" "{sdk_path}"', shell=True, capture_output=True, text=True)
        if result2.returncode != 0:
            print(f"Error creating sdk junction: {result2.stderr.strip()}")
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
