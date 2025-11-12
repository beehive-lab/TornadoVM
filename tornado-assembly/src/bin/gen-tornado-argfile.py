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
        # Use the tornado script from TORNADO_SDK/bin instead of relying on PATH
        tornado_sdk = os.environ.get("TORNADO_SDK")
        if not tornado_sdk:
            print("[ERROR] TORNADO_SDK environment variable not set")
            sys.exit(1)

        # On Windows, use the compiled tornado.exe executable if available,
        # otherwise fall back to tornado.py with python
        # On Unix-like systems, use the bash script
        if os.name == 'nt':  # Windows
            tornado_exe = Path(tornado_sdk) / "bin" / "tornado.exe"
            tornado_py = Path(tornado_sdk) / "bin" / "tornado.py"

            if tornado_exe.exists():
                tornado_script = tornado_exe
                cmd = [str(tornado_script), "--printJavaFlags"]
            elif tornado_py.exists():
                tornado_script = tornado_py
                cmd = [sys.executable, str(tornado_script), "--printJavaFlags"]
            else:
                print("[ERROR] Neither tornado.exe nor tornado.py found in TORNADO_SDK/bin")
                sys.exit(1)
        else:  # Unix-like systems (Linux, macOS)
            tornado_script = Path(tornado_sdk) / "bin" / "tornado"
            cmd = [str(tornado_script), "--printJavaFlags"]

        result = subprocess.run(
            cmd, capture_output=True, text=True, check=True
        )
        flags_output = result.stdout.strip()

        # Strip off 'java ' prefix if present
        if flags_output.startswith("java "):
            flags_output = flags_output[5:]

        # Split flags by whitespace
        return flags_output.split()
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Failed to run tornado --printJavaFlags: {e}")
        print(f"[ERROR] stderr: {e.stderr if hasattr(e, 'stderr') else 'N/A'}")
        sys.exit(1)
    except FileNotFoundError as e:
        print(f"[ERROR] Command not found: {e}")
        print(f"[ERROR] tornado script path: {tornado_script}")
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


def generate_argfile(backends, output_dir=None):
    """
    Generate the tornado-argfile.template based on selected backends.

    This function creates a Java argument file template (@argfile) that contains all the JVM
    flags needed to run TornadoVM applications. The template uses ${TORNADO_SDK} placeholders
    which can be expanded using envsubst. The argfile includes:
    - JVM mode and memory settings (-XX flags, -server, etc.)
    - Native library paths (with ${TORNADO_SDK} placeholder)
    - TornadoVM runtime properties
    - Module system configuration (--module-path, --add-modules with ${TORNADO_SDK} placeholder)
    - Module exports (--add-exports) for common and backend-specific modules

    Args:
        backends (str): Comma-separated list of backends (e.g., "opencl,ptx,spirv")
        output_dir (str): Optional output directory. If None, uses TORNADO_SDK directory
    """
    tornado_sdk = os.environ.get("TORNADO_SDK")
    project_root = get_project_root()

    # Generate argfile template in specified directory or SDK directory
    if output_dir:
        output_file = Path(output_dir) / "tornado-argfile.template"
    else:
        output_file = Path(tornado_sdk) / "tornado-argfile.template"
    export_paths = get_export_list_paths(tornado_sdk)

    # Clean old file
    print("[INFO] Cleaning old file")
    if output_file.exists():
        output_file.unlink()

    print("[INFO] Generating TornadoVM argfile template")

    # Get Java flags from tornado command
    java_flags = get_tornado_flags()

    # Parse backends (split comma-separated list and trim whitespace)
    # Normalize backend names by removing "-backend" suffix if present
    backend_list = [b.strip().replace("-backend", "") for b in backends.split(",")]

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
    # Note: This will be expanded by the tornado launcher or manually via envsubst
    output_lines.append("# === Native library path ===")
    # Use OS-appropriate path separator (\ on Windows, / on Unix)
    lib_path = f"-Djava.library.path=${{TORNADO_SDK}}{os.sep}lib"
    output_lines.append(lib_path)
    output_lines.append("")

    # === Tornado runtime classes ===
    # Extract TornadoVM-specific system properties
    output_lines.append("# === Tornado runtime classes ===")
    for flag in java_flags:
        if flag.startswith("-Dtornado"):
            output_lines.append(flag)
    output_lines.append("")

    # === Module system ===
    output_lines.append("# === Module system ===")
    # Add module paths with ${TORNADO_SDK} placeholders
    # Use OS-appropriate separators: ; on Windows, : on Unix for path lists
    # Use \ on Windows, / on Unix for file paths
    module_path = f"--module-path .{os.pathsep}${{TORNADO_SDK}}{os.sep}share{os.sep}java{os.sep}tornado"
    upgrade_module_path = f"--upgrade-module-path ${{TORNADO_SDK}}{os.sep}share{os.sep}java{os.sep}graalJars"
    output_lines.append(module_path)
    output_lines.append(upgrade_module_path)
    # Extract and add --add-modules
    i = 0
    while i < len(java_flags):
        flag = java_flags[i]
        if flag == "--add-modules":
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

    print("[INFO] Done. Generated argfile template")
    print(f"[INFO] Template path: {output_file}")


def main():
    if len(sys.argv) < 2:
        print("Usage: gen-tornado-argfile.py <backends> [output_dir]")
        print("Example: gen-tornado-argfile.py opencl,ptx,spirv")
        print("Example: gen-tornado-argfile.py opencl /path/to/output/dir")
        sys.exit(1)

    backends = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else None
    generate_argfile(backends, output_dir)


if __name__ == "__main__":
    main()
