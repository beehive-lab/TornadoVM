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
gen-tornado-argfile-template.py
Generate tornado-argfile from tornado --printJavaFlags and export lists
"""

import os
import re
import subprocess
import sys
from pathlib import Path


def get_project_root():
    """Get the project root directory (two levels up from TORNADOVM_HOME)"""
    tornado_sdk = os.environ.get("TORNADOVM_HOME")
    if not tornado_sdk:
        print("[ERROR] TORNADOVM_HOME environment variable not set")
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
        # Use the tornado script from TORNADOVM_HOME/bin instead of relying on PATH
        tornado_sdk = os.environ.get("TORNADOVM_HOME")
        if not tornado_sdk:
            print("[ERROR] TORNADOVM_HOME environment variable not set")
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
                print("[ERROR] Neither tornado.exe nor tornado.py found in TORNADOVM_HOME/bin")
                sys.exit(1)
        else:  # Unix-like systems (Linux, macOS)
            tornado_script = Path(tornado_sdk) / "bin" / "tornado"
            cmd = [str(tornado_script), "--printJavaFlags"]

        result = subprocess.run(
            cmd, capture_output=True, text=True, check=True
        )
        flags_output = result.stdout.strip()

        # Split flags by whitespace and drop the leading java-binary token. --printJavaFlags emits
        # the full launcher command, so the first token is the (possibly absolute) path to java.
        flags = flags_output.split()
        if flags and (flags[0].endswith("java") or flags[0].endswith("java.exe")):
            flags = flags[1:]
        return flags
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
        tornado_sdk (str): Path to the TORNADOVM_HOME directory

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
        "cuda": export_lists_dir / "cuda-exports",
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
    Generate tornado-argfile.template as a FAITHFUL reproduction of the exact JVM flags the
    tornado launcher runs (``tornado --printJavaFlags``), so ``java @<file> -m <module>/<Main>``
    launches TornadoVM identically.

    Two things are transformed so the flags work as a single Java argument file:
    - ``@<export-list>`` references are INLINED. Java does not recursively expand a nested
      ``@argfile`` referenced from inside another argfile, so the export-list contents are copied
      in verbatim (this is why an earlier template that kept the ``@`` refs failed).
    - Absolute ``$TORNADOVM_HOME`` paths (module-path entries incl. the reflection-path
      ``share/java/jvmci`` module dir, native library path) are replaced with the
      ``${TORNADOVM_HOME}`` placeholder for portability (expand via envsubst before use).

    Every other flag from ``--printJavaFlags`` is preserved verbatim - crucially the
    ``--add-exports java.base/jdk.internal.*=jdk.internal.vm.ci`` and
    ``-Djdk.internal.vm.ci.enabled=true`` flags the reflection (JVMCI-absent) path needs, and the
    ``--module-path`` that already includes the jvmci module dir. The earlier version rebuilt the
    argfile from cherry-picked categories and dropped both, producing an unusable file on JDK 27.

    Args:
        backends (str): Comma-separated backend list. Advisory only - the flags come from the
            installed SDK via --printJavaFlags, which already carries the built backends' exports.
        output_dir (str): Optional output directory. If None, uses TORNADOVM_HOME.
    """
    tornado_sdk = os.environ.get("TORNADOVM_HOME")

    if output_dir:
        output_file = Path(output_dir) / "tornado-argfile.template"
    else:
        output_file = Path(tornado_sdk) / "tornado-argfile.template"

    if output_file.exists():
        output_file.unlink()

    java_flags = get_tornado_flags()

    # Templatize the SDK path so the file is relocatable. tornado --printJavaFlags builds its paths
    # from $TORNADOVM_HOME, so replacing that exact prefix (and its real target, in case it is a
    # symlink) yields ${TORNADOVM_HOME}-relative paths.
    sdk_candidates = [tornado_sdk, str(Path(tornado_sdk).resolve())]

    def templatize(token):
        for base in sdk_candidates:
            if base and base in token:
                token = token.replace(base, "${TORNADOVM_HOME}")
        return token

    # Options that take a following value token (keep the pair on one line).
    valued_options = {"--module-path", "-p", "--add-modules", "--add-exports",
                      "--add-opens", "--add-reads", "--patch-module", "--upgrade-module-path"}

    output_lines = [
        "# Generated by gen-tornado-argfile-template.py - faithful copy of `tornado --printJavaFlags`.",
        "# Expand ${TORNADOVM_HOME} (e.g. `envsubst < this > tornado.args`), then run:",
        "#   java @tornado.args -m <module>/<Main-Class> [app args]",
        "",
    ]

    i = 0
    n = len(java_flags)
    while i < n:
        token = java_flags[i]
        if token.startswith("@"):
            # Inline the export-list file (nested @argfiles are not expanded inside an argfile).
            include = token[1:]
            output_lines.append(f"# --- inlined {os.path.basename(include)} ---")
            output_lines.extend(strip_comments(include))
            output_lines.append("")
            i += 1
        elif token in valued_options and i + 1 < n:
            output_lines.append(f"{token} {templatize(java_flags[i + 1])}")
            i += 2
        else:
            output_lines.append(templatize(token))
            i += 1

    with open(output_file, "w") as f:
        f.write("\n".join(output_lines) + "\n")

    print(f"[INFO] Wrote {output_file} ({backends})")

def main():
    if len(sys.argv) < 2:
        print("Usage: gen-tornado-argfile-template.py <backends> [output_dir]")
        print("Example: gen-tornado-argfile-template.py opencl,ptx,spirv")
        print("Example: gen-tornado-argfile-template.py opencl /path/to/output/dir")
        sys.exit(1)

    backends = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else None
    generate_argfile(backends, output_dir)


if __name__ == "__main__":
    main()
