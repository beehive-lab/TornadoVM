#!/usr/bin/env python3

#
# Copyright (c) 2026, APT Group, Department of Computer Science,
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
IntelliJ Build Script for TornadoVM.

This script is invoked from IntelliJ's TornadoVM-Build run configuration.
It reads the BACKEND environment variable and invokes Maven with the correct
profiles, then runs post-installation steps.

Usage:
    Set the BACKEND environment variable to the desired backend(s):
    - Single backend: "opencl" or "cuda"
    - Multiple backends: "opencl,cuda"

    The script will:
    1. Invoke Maven with the correct profiles and -Dtornado.backend property
    2. Run post-installation steps (update paths, generate setvars, etc.)
"""

import os
import re
import subprocess
import sys


def is_win_or_bat():
    """Returns True if running on Windows."""
    return os.name == 'nt'


def query_java_version(java_home):
    """
    Run `$JAVA_HOME/bin/java -version` and return its output.

    Args:
        java_home: Path to the JDK to interrogate.

    Returns:
        str: The `java -version` output, or "" if it could not be run.
    """
    java_cmd = os.path.join(java_home, "bin", "java")
    try:
        return subprocess.check_output(
            [java_cmd, "-version"], stderr=subprocess.STDOUT, universal_newlines=True
        )
    except (OSError, subprocess.CalledProcessError):
        return ""


def parse_major_version(java_version_output):
    """
    Extract the JDK feature/major version from `java -version` output, e.g.
    'openjdk version "22.0.2" ...' -> 22, 'openjdk version "27-ea" ...' -> 27.

    Returns:
        int: The major version, or None if it could not be parsed.
    """
    match = re.search(r'version "(\d+)', java_version_output)
    return int(match.group(1)) if match else None


def get_java_profile():
    """
    Return the build profile for the JDK that JAVA_HOME points at.

    jdk21 is the enable-preview SDK for JDK 21; one jdk22plus profile covers every JDK
    from 22 up, GraalVM included.

    Returns:
        str: "jdk21" or "jdk22plus".
    """
    java_home = os.environ.get("JAVA_HOME", "")
    if not java_home:
        print("[WARNING] JAVA_HOME is not set; assuming the jdk22plus profile.")
        return "jdk22plus"

    java_version_output = query_java_version(java_home)
    major = parse_major_version(java_version_output)
    if major is None:
        print(f"[WARNING] Could not determine the JDK version of {java_home}; "
              "assuming the jdk22plus profile.")
        return "jdk22plus"

    if major < 21:
        # `java -version` reports JDK 8 as "1.8.0_x", so quote the version line itself
        # rather than the parsed major, which would read as a bare "JDK 1".
        version_line = java_version_output.strip().splitlines()[0]
        print("[ERROR] TornadoVM requires JDK 21 or newer, but JAVA_HOME is older:")
        print(f"  {java_home}")
        print(f"  {version_line}")
        sys.exit(1)

    return "jdk21" if major == 21 else "jdk22plus"


def compute_tornado_backend_variant(backends):
    """
    Compute the tornado.backend property value from backend list.

    Args:
        backends: List of backend names (e.g., ["opencl", "cuda"])

    Returns:
        str: The tornado.backend value (e.g., "opencl", "cuda", "opencl-cuda", "full")
    """
    all_backends = {"opencl", "cuda"}
    if set(backends) == all_backends:
        return "full"

    # Sort and join for consistent naming
    return "-".join(sorted(backends))


def run_maven(backends):
    """
    Run Maven with the specified backend profiles.

    Args:
        backends: List of backend names (e.g., ["opencl", "cuda"])

    Returns:
        int: Maven return code
    """
    project_root = os.getcwd()

    # Use Maven Wrapper (mvnw)
    if is_win_or_bat():
        mvn_cmd = os.path.join(project_root, "mvnw.cmd")
    else:
        mvn_cmd = os.path.join(project_root, "mvnw")

    # Get Java profile
    java_profile = get_java_profile()

    # Build the profiles list
    profiles = [java_profile] + [f"{b}-backend" for b in backends]
    profiles_arg = ",".join(profiles)

    # Compute tornado.backend property
    tornado_backend = compute_tornado_backend_variant(backends)

    # Build Maven command
    maven_args = [
        mvn_cmd,
        f"-P{profiles_arg}",
        f"-Dtornado.backend={tornado_backend}",
        "clean",
        "install"
    ]

    print("=" * 70)
    print("TornadoVM Build (IntelliJ)")
    print("=" * 70)
    print(f"Backends:        {', '.join(backends)}")
    print(f"Java Profile:    {java_profile}")
    print(f"JAVA_HOME:       {os.environ.get('JAVA_HOME', '<unset>')}")
    print(f"Tornado Backend: {tornado_backend}")
    print(f"Maven Command:   {' '.join(maven_args)}")
    print("=" * 70)
    print()

    # Run Maven
    result = subprocess.run(maven_args, cwd=project_root)
    return result.returncode


def run_post_installation():
    """
    Run post-installation steps by invoking post_installation.py.
    """
    python_cmd = "python" if is_win_or_bat() else "python3"
    post_install_script = os.path.join(".", "bin", "post_installation.py")

    print()
    print("Running post-installation steps...")
    print()

    result = subprocess.run([python_cmd, post_install_script], cwd=os.getcwd())
    return result.returncode


def main():
    """Main entry point for IntelliJ builds."""
    # Get BACKEND from environment variable
    backend_env = os.environ.get("BACKEND", "")

    if not backend_env:
        print("[ERROR] BACKEND environment variable is not set.")
        print()
        print("Please set the BACKEND environment variable in your IntelliJ run configuration.")
        print("Examples:")
        print("  BACKEND=opencl")
        print("  BACKEND=cuda")
        print("  BACKEND=opencl,cuda")
        print("  BACKEND=opencl,cuda")
        sys.exit(1)

    # Parse backends (comma-separated)
    backends = [b.strip().lower() for b in backend_env.split(",")]

    # Validate backends
    valid_backends = {"opencl", "cuda", "metal"}
    for backend in backends:
        if backend not in valid_backends:
            print(f"[ERROR] Invalid backend: {backend}")
            print(f"Valid backends are: {', '.join(sorted(valid_backends))}")
            sys.exit(1)

    # Run Maven
    maven_result = run_maven(backends)

    if maven_result != 0:
        print()
        print(f"[ERROR] Maven build failed with exit code {maven_result}")
        sys.exit(maven_result)

    # Run post-installation
    post_result = run_post_installation()

    if post_result != 0:
        print()
        print(f"[ERROR] Post-installation failed with exit code {post_result}")
        sys.exit(post_result)


if __name__ == "__main__":
    main()
