#!/usr/bin/env python3

#
# Copyright (c) 2013-2025, APT Group, Department of Computer Science,
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
import config_utils as cutils


def is_win_or_bat():
    """
    Returns boolean indicating whether the operating system is Windows or not.

    Returns:
        bool: True if the operating system is Windows, False otherwise.
    """
    return os.name == 'nt'


def detect_backends_from_sdk_path(sdk_path):
    """
    Detect the backends from the SDK directory name.

    The SDK path follows the pattern:
    .../dist/tornadovm-VERSION-BACKEND-PLATFORM/tornadovm-VERSION-BACKEND/

    For example:
    - tornadovm-2.2.1-dev-opencl-linux-amd64 → opencl
    - tornadovm-2.2.1-dev-ptx-linux-amd64 → ptx
    - tornadovm-2.2.1-dev-spirv-linux-amd64 → spirv
    - tornadovm-2.2.1-dev-full-linux-amd64 → opencl,ptx,spirv

    Args:
        sdk_path (str): The absolute path to the SDK directory

    Returns:
        str: Comma-separated backend string (e.g., "opencl-backend" or "opencl-backend,ptx-backend")
    """
    # Get the SDK directory name (e.g., "tornadovm-2.2.1-dev-opencl")
    sdk_dir_name = os.path.basename(sdk_path)

    # Known backends
    known_backends = ["opencl", "ptx", "spirv"]
    detected_backends = []

    # Check for "full" which means all backends
    if "-full-" in sdk_dir_name or sdk_dir_name.endswith("-full"):
        detected_backends = ["opencl", "ptx", "spirv"]
    else:
        # Check for individual backends in the directory name
        for backend in known_backends:
            # Match patterns like "-opencl-" or "-opencl" at the end
            if f"-{backend}-" in sdk_dir_name or sdk_dir_name.endswith(f"-{backend}"):
                detected_backends.append(backend)

    # Convert to backend format (e.g., "opencl" -> "opencl-backend")
    if detected_backends:
        return ",".join([f"{b}-backend" for b in detected_backends])

    # Fallback: return empty or default
    print(f"[WARNING] Could not detect backend from SDK path: {sdk_path}")
    return ""


def update_paths():
    """
    Run the 'update_paths.py' script and update TORNADOVM_HOME in current process.

    This function executes the 'update_paths.py' script to update environment paths
    and symlinks, then reads the new SDK path from the bin/sdk symlink to update
    the TORNADOVM_HOME environment variable in the current process.
    """
    python_cmd = "python" if is_win_or_bat() else "python3"
    update_paths_script = os.path.join(".", "bin", "update_paths.py")
    subprocess.run([python_cmd, update_paths_script], stdout=subprocess.PIPE)

    # Read the actual SDK path from the bin/sdk symlink
    # This is necessary because environment variables set in subprocess don't propagate back
    sdk_symlink = os.path.join("bin", "sdk")
    if os.path.islink(sdk_symlink):
        # Resolve the symlink to get the absolute path
        sdk_path = os.path.realpath(sdk_symlink)
        os.environ['TORNADOVM_HOME'] = sdk_path
        print(f"TORNADOVM_HOME updated to: {sdk_path}")
    elif os.path.isdir(sdk_symlink):
        # On Windows, it might be a junction - get the absolute path
        sdk_path = os.path.abspath(sdk_symlink)
        os.environ['TORNADOVM_HOME'] = sdk_path
        print(f"TORNADOVM_HOME updated to: {sdk_path}")
    else:
        print(f"[WARNING] Could not find SDK symlink at {sdk_symlink}")


def update_backend_file(selected_backends_str):
    """
    Update the 'tornado.backend' file with selected backends.

    This function updates the 'tornado.backend' file in the Tornado SDK with the selected backend configurations.

    Args:
        selected_backends_str (str): Comma-separated string of selected backends.
    """
    tornado_sdk_path = os.environ.get("TORNADOVM_HOME")
    backend_file_path = os.path.join(tornado_sdk_path, "etc", "tornado.backend")
    with open(backend_file_path, "w") as backend_file:
        backend_file.write(f"tornado.backends={selected_backends_str}")


def copy_graal_jars():
    """
    Copy GraalVM JAR files to the Tornado SDK.

    This function checks the Java version and copies GraalVM JAR files to the Tornado SDK's 'share/java/graalJars'
    directory if the Java environment is not GraalVM.
    """
    tornado_sdk_path = os.environ.get("TORNADOVM_HOME")
    java_version_output = subprocess.check_output(
        ["java", "-version"], stderr=subprocess.STDOUT, universal_newlines=True
    )

    if "GraalVM" not in java_version_output:
        graal_jars_dir = os.path.join(os.getcwd(), "graalJars")
        destination_dir = os.path.join(tornado_sdk_path, "share", "java", "graalJars")
        os.makedirs(destination_dir, exist_ok=True)
        for filename in os.listdir(graal_jars_dir):
            source_file = os.path.join(graal_jars_dir, filename)
            destination_file = os.path.join(destination_dir, filename)
            if os.path.isfile(source_file):
                if is_win_or_bat():
                    subprocess.run(["copy", "/b", "/y", source_file, destination_file], shell=True)
                else:
                    subprocess.run(["cp", source_file, destination_file])


def generate_setvars_files():
    """
    Generate setvars.cmd (Windows) or setvars.sh (Unix/Linux/Mac) file with the correct TORNADOVM_HOME path.

    This function creates an environment setup script at the project root that:
    - Set TORNADOVM_HOME to the correct SDK location (SDKMAN compliant)
    - Add the SDK bin directory to PATH
    - Preserve the current JAVA_HOME setting
    """
    project_root = os.getcwd()
    tornado_sdk = os.environ.get('TORNADOVM_HOME')
    java_home = os.environ.get('JAVA_HOME', '')

    if not tornado_sdk:
        print("Warning: TORNADOVM_HOME not set, skipping setvars file generation")
        return

    if is_win_or_bat():
        # Generate setvars.cmd for Windows
        setvars_cmd_path = os.path.join(project_root, "setvars.cmd")
        setvars_cmd_content = f"""@echo off
REM TornadoVM Environment Setup Script (Windows)
REM Generated automatically by the build process

set JAVA_HOME={java_home}
set TORNADOVM_HOME={tornado_sdk}
set PATH=%TORNADOVM_HOME%\\bin;%PATH%
"""

        with open(setvars_cmd_path, 'w', newline='\r\n') as f:
            f.write(setvars_cmd_content)

        print(f"Generated utilities:")
        print(f"   [Windows-env]: {setvars_cmd_path}")
    else:
        # Generate setvars.sh for Unix/Linux/Mac
        setvars_sh_path = os.path.join(project_root, "setvars.sh")
        setvars_sh_content = f"""#!/bin/bash
# TornadoVM Environment Setup Script (Unix/Linux/Mac)
# Generated automatically by the build process

export JAVA_HOME="{java_home}"
export TORNADOVM_HOME="{tornado_sdk}"
export PATH="$TORNADOVM_HOME/bin:$PATH"
"""

        with open(setvars_sh_path, 'w', newline='\n') as f:
            f.write(setvars_sh_content)

        # Make setvars.sh executable on Unix systems
        os.chmod(setvars_sh_path, 0o755)

        print(f"Generated utilities:")
        print(f"   [Unix-env]: {setvars_sh_path}")


def generate_argfile_template(backend):
    """
    Generate the argfile template for your current build.
    The template contains ${TORNADOVM_HOME} placeholders that will be expanded at runtime.

    Note: SPIRV backend depends on OpenCL runtime, so when building with SPIRV,
    we automatically include OpenCL exports in the argfile.

    Args:
        backend (str): Comma-separated string of backends (e.g., "opencl" or "opencl,ptx,spirv")
    """
    # If SPIRV is in the backend list, ensure OpenCL is also included for exports
    # SPIRV can run on the OpenCL runtime, so OpenCL module must be available
    backend_list = [b.strip() for b in backend.split(",")]
    if "spirv" in backend_list and "opencl" not in backend_list:
        backend_list.insert(0, "opencl")  # Add OpenCL before SPIRV
        backend = ",".join(backend_list)

    tornado_home = os.environ.get('TORNADOVM_HOME')
    scripts_dir = os.path.join(f"{tornado_home}", "bin")
    current = os.getcwd()
    os.chdir(scripts_dir)

    # Use 'python' on Windows, 'python3' on Unix-like systems
    python_cmd = "python" if is_win_or_bat() else "python3"
    gen_script = os.path.join(scripts_dir, "gen-tornado-argfile-template.py")

    try:
        if is_win_or_bat():
            # On Windows with shell=True, pass as a single string
            result = subprocess.run(
                f'{python_cmd} "{gen_script}" {backend}',
                shell=True,
                check=True,
                capture_output=True,
                text=True
            )
        else:
            # On Unix-like systems, pass as a list without shell
            result = subprocess.run(
                [python_cmd, gen_script, backend],
                check=True,
                capture_output=True,
                text=True
            )

        # Print output for debugging
        if result.stdout:
            print(result.stdout)
        if result.stderr:
            print(result.stderr, file=sys.stderr)

    except subprocess.CalledProcessError as e:
        print(f"\n[ERROR] Failed to generate tornado-argfile")
        print(f"[ERROR] Command: {e.cmd}")
        print(f"[ERROR] Return code: {e.returncode}")
        if e.stdout:
            print(f"[ERROR] stdout: {e.stdout}")
        if e.stderr:
            print(f"[ERROR] stderr: {e.stderr}")
        sys.exit(-1)
    finally:
        os.chdir(current)


def expand_argfile_template():
    """
    Expand the tornado-argfile.template by replacing ${TORNADOVM_HOME} placeholders
    with the actual TORNADOVM_HOME path and generate the final tornado-argfile.

    This function creates a ready-to-use tornado-argfile in the TORNADOVM_HOME directory
    that can be used with @argfile syntax. The expansion is done in pure Python for
    cross-platform portability (Linux, macOS, Windows).
    """
    tornado_home = os.environ.get("TORNADOVM_HOME")
    if not tornado_home:
        print("[WARNING] TORNADOVM_HOME not set, skipping argfile expansion")
        return

    template_path = os.path.join(tornado_home, "tornado-argfile.template")
    output_path = os.path.join(tornado_home, "tornado-argfile")

    if not os.path.exists(template_path):
        print(f"[WARNING] Template file not found: {template_path}")
        return

    try:
        # Read the template file
        with open(template_path, "r") as template_file:
            template_content = template_file.read()

        # Expand ${TORNADOVM_HOME} placeholder with the actual path
        # This works across all platforms (Linux, macOS, Windows)
        expanded_content = template_content.replace("${TORNADOVM_HOME}", tornado_home)

        # Write the expanded argfile
        with open(output_path, "w") as output_file:
            output_file.write(expanded_content)

        print(f"   [argfile]: {output_path}")

    except Exception as e:
        print(f"[ERROR] Failed to expand argfile template: {e}")
        sys.exit(-1)


def update_intellij_tests_config(backend_profiles):
    """
    Regenerate the TornadoVM-Tests.run.xml IntelliJ configuration with the updated TORNADOVM_HOME path.

    This ensures the IntelliJ test runner always has the correct absolute path to the SDK
    after each build.

    Args:
        backend_profiles (str): Comma-separated string of backends (e.g., "opencl-backend,ptx-backend")
    """
    project_root = os.getcwd()
    tornado_home = os.environ.get("TORNADOVM_HOME")
    java_home = os.environ.get("JAVA_HOME", "")

    if not tornado_home:
        print("[WARNING] TORNADOVM_HOME not set, skipping IntelliJ config update")
        return

    # Path to the template
    template_path = os.path.join(project_root, "scripts", "templates", "intellij-settings", "ideinit", "tornadovm_tests_template.xml")

    if not os.path.exists(template_path):
        print(f"[WARNING] IntelliJ template not found: {template_path}")
        return

    # Get Python info
    try:
        python_cmd = "python" if is_win_or_bat() else "python3"
        if is_win_or_bat():
            python_home = subprocess.check_output(["where", "python"]).decode().strip().split("\r\n")[0]
        else:
            python_home = subprocess.check_output(["which", "python3"]).decode().strip()

        python_version = subprocess.check_output([python_home, "--version"]).decode().strip()
        python_version_name = python_version.split()[1]
        python_sdk_name = f"Python {python_version_name}"
    except Exception as e:
        print(f"[WARNING] Could not determine Python version: {e}")
        return

    # Read template
    try:
        with open(template_path, "r") as f:
            template_content = f.read()
    except Exception as e:
        print(f"[ERROR] Failed to read template: {e}")
        return

    # Path to tornado-test script
    test_script_dir = os.path.join(tornado_home, "bin", "tornado-test")

    # Replace placeholders
    xml_content = template_content
    xml_content = xml_content.replace("[@@BACKEND_PROFILES@@]", backend_profiles)
    xml_content = xml_content.replace("[@@TORNADOVM_HOME@@]", tornado_home)
    xml_content = xml_content.replace("[@@JAVA_HOME@@]", java_home)
    xml_content = xml_content.replace("[@@PYTHON_SDK_HOME@@]", python_home)
    xml_content = xml_content.replace("[@@PYTHON_SDK_NAME@@]", python_sdk_name)
    xml_content = xml_content.replace("[@@PROJECT_DIR@@]", project_root)
    xml_content = xml_content.replace("[@@TORNADO_TEST_DIR@@]", test_script_dir)

    # Write the generated XML
    output_dir = os.path.join(project_root, ".build")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "TornadoVM-Tests.run.xml")

    try:
        with open(output_path, "w") as f:
            f.write(xml_content)
        print(f"   [IntelliJ]: {output_path}")
    except Exception as e:
        print(f"[ERROR] Failed to write IntelliJ config: {e}")


def main():
    """
    Main function to perform all post-installation tasks.

    This function is the entry point of the script and performs:
    1. Update paths and symlinks to the new SDK
    2. Update the tornado.backend file
    3. Copy GraalVM JARs if needed
    4. Generate setvars files (setvars.sh / setvars.cmd)
    5. Generate argfile template and expand it
    6. Update IntelliJ test configuration
    7. Run PyInstaller on Windows
    """
    # Update paths - this sets TORNADOVM_HOME environment variable from the SDK symlink
    update_paths()

    # Detect backends from the SDK path (more reliable than environment variable)
    # This handles cases where the Maven profile was changed but the IntelliJ config still has old values
    tornado_home = os.environ.get("TORNADOVM_HOME", "")
    if tornado_home:
        selected_backends_str = detect_backends_from_sdk_path(tornado_home)
        print(f"Detected backends: {selected_backends_str}")
    else:
        # Fallback to environment variable if SDK path detection fails
        selected_backends_str = os.environ.get("selected_backends", "")
        print(f"Using backends from environment: {selected_backends_str}")

    # Update backend file
    update_backend_file(selected_backends_str)

    # Copy Graal JARs if not using GraalVM
    copy_graal_jars()

    # Generate setvars files
    generate_setvars_files()

    # Generate argfile template and expand it
    # Convert backend format: "opencl-backend,ptx-backend" -> "opencl,ptx"
    backend_for_argfile = selected_backends_str.replace("-backend", "")
    generate_argfile_template(backend_for_argfile)
    expand_argfile_template()

    # Update IntelliJ TornadoVM-Tests configuration with the new TORNADOVM_HOME
    update_intellij_tests_config(selected_backends_str)

    # Run PyInstaller on Windows to create executables
    if is_win_or_bat():
        cutils.runPyInstaller(os.getcwd(), os.environ['TORNADOVM_HOME'])

    print("\nPost-installation completed successfully.")


if __name__ == "__main__":
    main()
