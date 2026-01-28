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
    - Single backend: "opencl", "ptx", or "spirv"
    - Multiple backends: "opencl,ptx" or "opencl,ptx,spirv"

    The script will:
    1. Invoke Maven with the correct profiles and -Dtornado.backend property
    2. Run post-installation steps (update paths, generate setvars, etc.)
"""

import os
import subprocess
import sys


def is_win_or_bat():
    """Returns True if running on Windows."""
    return os.name == 'nt'


def get_java_profile():
    """
    Determine the Java profile based on JAVA_HOME.

    Returns:
        str: "graal-jdk-21" if using GraalVM, "jdk21" otherwise.
    """
    java_home = os.environ.get("JAVA_HOME", "")
    if "graal" in java_home.lower():
        return "graal-jdk-21"
    return "jdk21"


def compute_tornado_backend_variant(backends):
    """
    Compute the tornado.backend property value from backend list.

    Args:
        backends: List of backend names (e.g., ["opencl", "ptx"])

    Returns:
        str: The tornado.backend value (e.g., "opencl", "ptx", "opencl-ptx", "full")
    """
    all_backends = {"opencl", "ptx", "spirv"}
    if set(backends) == all_backends:
        return "full"

    # Sort and join for consistent naming
    return "-".join(sorted(backends))


def run_maven(backends):
    """
    Run Maven with the specified backend profiles.

    Args:
        backends: List of backend names (e.g., ["opencl", "ptx"])

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
        print("  BACKEND=ptx")
        print("  BACKEND=opencl,ptx")
        print("  BACKEND=opencl,ptx,spirv")
        sys.exit(1)

    # Parse backends (comma-separated)
    backends = [b.strip().lower() for b in backend_env.split(",")]

    # Validate backends
    valid_backends = {"opencl", "ptx", "spirv"}
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
