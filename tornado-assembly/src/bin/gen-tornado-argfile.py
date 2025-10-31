#!/usr/bin/env python3
#
# Copyright (c) 2025, APT Group, Department of Computer Science,
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

"""
gen-tornado-argfile.py
Generate tornado-argfile from tornado --printJavaFlags and export lists
"""

import os
import re
import subprocess
import sys
from pathlib import Path


def get_project_root():
    """Get the project root directory (two levels up from TORNADO_SDK)"""
    tornado_sdk = os.environ.get('TORNADO_SDK')
    if not tornado_sdk:
        print("[ERROR] TORNADO_SDK environment variable not set")
        sys.exit(1)
    return Path(tornado_sdk).parent.parent


def get_tornado_flags():
    """Run tornado --printJavaFlags and return the flags as a list"""
    try:
        result = subprocess.run(
            ['tornado', '--printJavaFlags'],
            capture_output=True,
            text=True,
            check=True
        )
        flags_output = result.stdout.strip()

        # Strip off 'java ' prefix if present
        if flags_output.startswith('java '):
            flags_output = flags_output[5:]

        # Split flags by whitespace
        return flags_output.split()
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Failed to run tornado --printJavaFlags: {e}")
        sys.exit(1)
    except FileNotFoundError:
        print("[ERROR] 'tornado' command not found. Make sure it's in your PATH.")
        sys.exit(1)


def strip_comments(file_path):
    """Read a file and return lines that are not comments or empty"""
    lines = []
    try:
        with open(file_path, 'r') as f:
            for line in f:
                # Skip lines that are empty or start with # (possibly with leading whitespace)
                if not re.match(r'^\s*#', line) and not re.match(r'^\s*$', line):
                    lines.append(line.rstrip())
        return lines
    except FileNotFoundError:
        print(f"[WARNING] Export list file not found: {file_path}")
        return []


def generate_argfile(backends):
    """Generate the tornado-argfile based on backends"""
    tornado_sdk = os.environ.get('TORNADO_SDK')
    project_root = get_project_root()

    output_file = project_root / 'tornado-argfile'
    export_common = Path(tornado_sdk) / 'etc' / 'exportLists' / 'common-exports'
    export_opencl = Path(tornado_sdk) / 'etc' / 'exportLists' / 'opencl-exports'
    export_spirv = Path(tornado_sdk) / 'etc' / 'exportLists' / 'spirv-exports'
    export_ptx = Path(tornado_sdk) / 'etc' / 'exportLists' / 'ptx-exports'

    # Clean old file
    print("[INFO] Cleaning old file")
    if output_file.exists():
        output_file.unlink()

    print("[INFO] Generating TornadoVM argfile")

    # Get Java flags from tornado command
    java_flags = get_tornado_flags()

    # Parse backends
    backend_list = [b.strip() for b in backends.split(',')]

    # Start building the output content
    output_lines = []

    # === JVM mode and memory settings ===
    output_lines.append("# === JVM mode and memory settings ===")
    for flag in java_flags:
        if flag.startswith('-XX') or flag == '-server' or flag == '--enable-preview':
            output_lines.append(flag)
    output_lines.append("")

    # === Native library path ===
    output_lines.append("# === Native library path ===")
    for flag in java_flags:
        if flag.startswith('-Djava.library.path'):
            output_lines.append(flag)
    output_lines.append("")

    # === Tornado runtime classes ===
    output_lines.append("# === Tornado runtime classes ===")
    for flag in java_flags:
        if flag.startswith('-Dtornado'):
            output_lines.append(flag)
    output_lines.append("")

    # === Module system ===
    output_lines.append("# === Module system ===")
    i = 0
    while i < len(java_flags):
        flag = java_flags[i]
        if flag in ['--module-path', '--upgrade-module-path', '--add-modules']:
            if i + 1 < len(java_flags):
                output_lines.append(f"{flag} {java_flags[i+1]}")
                i += 2
                continue
        i += 1
    output_lines.append("")

    # === Export lists ===
    output_lines.append("# === Export lists ===")
    output_lines.append("")

    # Common exports (always included)
    output_lines.append(f"# ===: {export_common.name} ===")
    output_lines.extend(strip_comments(export_common))
    output_lines.append("")

    # Backend-specific exports
    for backend in backend_list:
        if backend == 'opencl':
            output_lines.append(f"# === {export_opencl.name} ===")
            output_lines.extend(strip_comments(export_opencl))
            output_lines.append("")
        elif backend == 'ptx':
            output_lines.append(f"# === {export_ptx.name} ===")
            output_lines.extend(strip_comments(export_ptx))
            output_lines.append("")
        elif backend == 'spirv':
            output_lines.append(f"# === {export_spirv.name} ===")
            output_lines.extend(strip_comments(export_spirv))
            output_lines.append("")

    # Write to file
    with open(output_file, 'w') as f:
        f.write('\n'.join(output_lines) + '\n')

    print("[INFO] Done. Generated fresh argfile")
    print(f"[INFO] File path: {output_file}")


def main():
    if len(sys.argv) < 2:
        print("Usage: gen-tornado-argfile.py <backends>")
        print("Example: gen-tornado-argfile.py opencl,ptx,spirv")
        sys.exit(1)

    backends = sys.argv[1]
    generate_argfile(backends)


if __name__ == "__main__":
    main()
