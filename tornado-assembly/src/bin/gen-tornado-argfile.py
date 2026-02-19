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
    tornado_sdk = os.environ.get("TORNADO_SDK")
    if not tornado_sdk:
        print("[ERROR] TORNADO_SDK environment variable not set")
        sys.exit(1)
    return Path(tornado_sdk).parent.parent


def get_tornado_flags():
    """
    Run tornado --printJavaFlags and return the flags as a list.

    The tornado command outputs all JVM flags needed to run TornadoVM,
    including module paths, exports, and runtime properties.

    Returns:
        list: List of Java flags split by whitespace
    """
    try:
        result = subprocess.run(
            ["tornado", "--printJavaFlags"], capture_output=True, text=True, check=True
        )
        flags_output = result.stdout.strip()

        # Strip off 'java ' prefix if present
        if flags_output.startswith("java "):
            flags_output = flags_output[5:]

        # Split flags by whitespace
        return flags_output.split()
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Failed to run tornado --printJavaFlags: {e}")
        sys.exit(1)
    except FileNotFoundError:
        print("[ERROR] 'tornado' command not found. Make sure it's in your PATH.")
        sys.exit(1)


def get_export_list_paths(tornado_sdk):
    """
    Get the paths to all export list files.

    Export lists contain --add-exports directives that expose internal JDK modules
    to TornadoVM. Each backend (OpenCL, PTX, SPIRV) requires specific module exports.

    Args:
        tornado_sdk (str): Path to the TORNADO_SDK directory

    Returns:
        dict: Dictionary mapping backend names to their export list file paths
    """
    export_lists_dir = Path(tornado_sdk) / "etc" / "exportLists"
    return {
        "common": export_lists_dir / "common-exports",
        "opencl": export_lists_dir / "opencl-exports",
        "spirv": export_lists_dir / "spirv-exports",
        "ptx": export_lists_dir / "ptx-exports",
        "metal": export_lists_dir / "metal-exports",
    }


def strip_comments(file_path):
    """
    Read a file and return lines that are not comments or empty.

    This function processes export list files by removing comment lines
    (starting with #) and empty lines, returning only the actual export directives.

    Args:
        file_path (Path): Path to the export list file

    Returns:
        list: List of non-comment, non-empty lines with trailing whitespace removed
    """
    lines = []
    try:
        with open(file_path, "r") as f:
            for line in f:
                # Skip lines that are empty or start with # (possibly with leading whitespace)
                if not re.match(r"^\s*#", line) and not re.match(r"^\s*$", line):
                    lines.append(line.rstrip())
        return lines
    except FileNotFoundError:
        print(f"[WARNING] Export list file not found: {file_path}")
        return []


def generate_argfile(backends):
    """
    Generate the tornado-argfile based on selected backends.

    This function creates a Java argument file (@argfile) that contains all the JVM
    flags needed to run TornadoVM applications. The argfile includes:
    - JVM mode and memory settings (-XX flags, -server, etc.)
    - Native library paths
    - TornadoVM runtime properties
    - Module system configuration (--module-path, --add-modules)
    - Module exports (--add-exports) for common and backend-specific modules

    Args:
        backends (str): Comma-separated list of backends (e.g., "opencl,ptx,spirv")
    """
    tornado_sdk = os.environ.get("TORNADO_SDK")
    project_root = get_project_root()

    output_file = project_root / "tornado-argfile"
    export_paths = get_export_list_paths(tornado_sdk)

    # Clean old file
    print("[INFO] Cleaning old file")
    if output_file.exists():
        output_file.unlink()

    print("[INFO] Generating TornadoVM argfile")

    # Get Java flags from tornado command
    java_flags = get_tornado_flags()

    # Parse backends (split comma-separated list and trim whitespace)
    backend_list = [b.strip() for b in backends.split(",")]

    # Start building the output content
    output_lines = []

    # === JVM mode and memory settings ===
    # Extract JVM-specific flags like -XX:+UseParallelGC, -server, --enable-preview
    output_lines.append("# === JVM mode and memory settings ===")
    for flag in java_flags:
        if flag.startswith("-XX") or flag == "-server" or flag == "--enable-preview":
            output_lines.append(flag)
    output_lines.append("")

    # === Native library path ===
    # Extract the java.library.path property that points to TornadoVM native libraries
    output_lines.append("# === Native library path ===")
    for flag in java_flags:
        if flag.startswith("-Djava.library.path"):
            output_lines.append(flag)
    output_lines.append("")

    # === Tornado runtime classes ===
    # Extract TornadoVM-specific system properties
    output_lines.append("# === Tornado runtime classes ===")
    for flag in java_flags:
        if flag.startswith("-Dtornado"):
            output_lines.append(flag)
    output_lines.append("")

    # === Module system ===
    # Extract module-related flags that take an argument (--module-path, --add-modules, etc.)
    # These flags come in pairs: flag + value
    output_lines.append("# === Module system ===")
    i = 0
    while i < len(java_flags):
        flag = java_flags[i]
        if flag in ["--module-path", "--upgrade-module-path", "--add-modules"]:
            if i + 1 < len(java_flags):
                output_lines.append(f"{flag} {java_flags[i+1]}")
                i += 2
                continue
        i += 1
    output_lines.append("")

    # === Export lists ===
    # Add --add-exports directives that expose internal JDK modules to TornadoVM
    output_lines.append("# === Export lists ===")
    output_lines.append("")

    # Common exports (always included regardless of backend)
    # These expose core compiler and runtime modules needed by all backends
    output_lines.append(f"# ===: {export_paths['common'].name} ===")
    output_lines.extend(strip_comments(export_paths["common"]))
    output_lines.append("")

    # Backend-specific exports
    # Each backend (opencl, ptx, spirv) requires access to different internal modules
    for backend in backend_list:
        if backend in export_paths:
            output_lines.append(f"# === {export_paths[backend].name} ===")
            output_lines.extend(strip_comments(export_paths[backend]))
            output_lines.append("")

    # Write to file (argfile format: one argument per line)
    with open(output_file, "w") as f:
        f.write("\n".join(output_lines) + "\n")

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
