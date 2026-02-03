#!/usr/bin/env python3

#
# Copyright (c) 2024, 2026, APT Group, Department of Computer Science,
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
import xml.etree.ElementTree as ET
from pathlib import Path

def generate_backend_profiles_as_xml_entries(BACKEND_PROFILES):
    xml_entries = []
    for backend in BACKEND_PROFILES:
        xml_entry_line = f'<entry key="{backend}" value="true" />'
        xml_entries.append(xml_entry_line)

    return "\n".join(xml_entries)

def read_template(template_path):
    with open(template_path, 'r') as file:
        template_content = file.read()
    return template_content

def write_generated_template(xml_content, xml_file_path):
    with open(xml_file_path, "w") as file:
        file.write(xml_content)

def compute_tornado_backend_variant(backend_profiles):
    """
    Compute the tornado.backend property value from backend profiles.

    This follows the same logic as bin/compile:
    - Single backend: "opencl", "ptx", or "spirv"
    - Multiple backends: sorted and joined with "-" (e.g., "opencl-ptx")
    - All three backends: "full"

    Args:
        backend_profiles: List of backend profile names (e.g., ["opencl-backend", "ptx-backend"])

    Returns:
        str: The tornado.backend value (e.g., "opencl", "ptx", "opencl-ptx", "full")
    """
    # Extract backend names from profiles (e.g., "opencl-backend" -> "opencl")
    backend_list = [b.replace("-backend", "") for b in backend_profiles]

    # Check if all backends are present -> use "full"
    all_backends = {"opencl", "ptx", "spirv"}
    if set(backend_list) == all_backends:
        return "full"

    # Sort and join for consistent naming
    backend_list_sorted = sorted(backend_list)
    return "-".join(backend_list_sorted)


def define_and_get_internal_maven_content(xml_templates_directory, project_directory, java_home, backend_profiles):
    maven_directory = os.path.join(project_directory, "etc", "dependencies", "apache-maven-3.9.3")
    xml_internal_maven_build_content_directory = os.path.join(xml_templates_directory, "maven_template.xml")
    xml_internal_maven_build_content = read_template(xml_internal_maven_build_content_directory)

    if "graal" in java_home:
        java_profile = "graal-jdk-21"
    else:
        java_profile = "jdk21"

    xml_backend_profiles = generate_backend_profiles_as_xml_entries(backend_profiles)

    # Compute tornado.backend property value for SDK directory naming
    tornado_backend_variant = compute_tornado_backend_variant(backend_profiles)

    xml_internal_maven_build_content = xml_internal_maven_build_content.replace("[@@MAVEN_DIRECTORY@@]", maven_directory)
    xml_internal_maven_build_content = xml_internal_maven_build_content.replace("[@@JAVA_PROFILE@@]", java_profile)
    xml_internal_maven_build_content = xml_internal_maven_build_content.replace("[@@BACKEND_PROFILES@@]", xml_backend_profiles)
    xml_internal_maven_build_content = xml_internal_maven_build_content.replace("[@@PROJECT_DIR@@]", project_directory)
    xml_internal_maven_build_content = xml_internal_maven_build_content.replace("[@@TORNADO_BACKEND@@]", tornado_backend_variant)

    return xml_internal_maven_build_content

def define_and_get_tornadovm_build_content(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles):
    intellij_build_script = os.path.join(project_directory, "bin", "intellij_build.py")
    xml_TornadoVM_build_content_directory = os.path.join(xml_templates_directory, "tornadovm_build_template.xml")
    xml_TornadoVM_build_content = read_template(xml_TornadoVM_build_content_directory)

    python_version = subprocess.check_output([str(python_home), "--version"]).decode().strip()
    python_version_name = python_version.split()[1]
    python_sdk_home = str(python_home)
    python_sdk_name = f"Python {python_version_name}"

    # Compute BACKEND value (e.g., "opencl" or "opencl,ptx")
    # backend_profiles is comma-separated (e.g., "opencl-backend,ptx-backend")
    backend_value = backend_profiles.replace("-backend", "")

    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@JAVA_HOME@@]", java_home)
    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@BACKEND@@]", backend_value)
    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@PYTHON_SDK_HOME@@]", python_sdk_home)
    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@PYTHON_SDK_NAME@@]", python_sdk_name)
    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@PROJECT_DIR@@]", project_directory)
    xml_TornadoVM_build_content = xml_TornadoVM_build_content.replace("[@@INTELLIJ_BUILD_SCRIPT@@]", intellij_build_script)

    return xml_TornadoVM_build_content

def define_and_get_tornadovm_tests_content(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles):
    test_script_directory = os.path.join(tornado_sdk, "bin", "tornado-test")
    xml_TornadoVM_tests_content_directory = os.path.join(xml_templates_directory, "tornadovm_tests_template.xml")
    xml_TornadoVM_tests_content = read_template(xml_TornadoVM_tests_content_directory)

    python_version = subprocess.check_output([str(python_home), "--version"]).decode().strip()
    python_version_name = python_version.split()[1]
    python_sdk_home = str(python_home)
    python_sdk_name = f"Python {python_version_name}"

    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@BACKEND_PROFILES@@]", backend_profiles)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@TORNADOVM_HOME@@]", tornado_sdk)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@JAVA_HOME@@]", java_home)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@PYTHON_SDK_HOME@@]", python_sdk_home)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@PYTHON_SDK_NAME@@]", python_sdk_name)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@PROJECT_DIR@@]", project_directory)
    xml_TornadoVM_tests_content = xml_TornadoVM_tests_content.replace("[@@TORNADO_TEST_DIR@@]", test_script_directory)

    return xml_TornadoVM_tests_content

def generate_internal_maven_build_xml(xml_templates_directory, project_directory, java_home, backend_profiles):
    xml_internal_content = define_and_get_internal_maven_content(xml_templates_directory, project_directory, java_home, backend_profiles)
    xml_build_directory = os.path.join(project_directory, ".build", "_internal_TornadoVM_Maven-cleanAndinstall.run.xml")
    print("Generating " + xml_build_directory)
    write_generated_template(xml_internal_content, xml_build_directory)

def generate_tornadovm_build_xml(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles):
    xml_build_content = define_and_get_tornadovm_build_content(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles)
    xml_build_directory = os.path.join(project_directory, ".build", "TornadoVM-Build.run.xml")
    print("Generating " + xml_build_directory)
    write_generated_template(xml_build_content, xml_build_directory)

def generate_tornadovm_tests_xml(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles):
    xml_tests_content = define_and_get_tornadovm_tests_content(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backend_profiles)
    xml_tests_directory = os.path.join(project_directory, ".build", "TornadoVM-Tests.run.xml")
    print("Generating " + xml_tests_directory)
    write_generated_template(xml_tests_content, xml_tests_directory)

def generate_xml_files(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backends):
    backends_separated_comma = ",".join(backends)
    generate_internal_maven_build_xml(xml_templates_directory, project_directory, java_home, backends)
    generate_tornadovm_build_xml(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backends_separated_comma)
    generate_tornadovm_tests_xml(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backends_separated_comma)
    print("IntelIj Files Generated ............... [ok]")

def cleanup_build_directory(build_directory_string):
    build_dir = Path(build_directory_string)
    if build_dir.exists() and build_dir.is_dir():
        for file in build_dir.iterdir():
            if file.name == ".gitignore":
                continue
            if file.is_file() or file.is_dir():
                # remove files and subdirectories recursively
                if file.is_file():
                    file.unlink()
                else:
                    import shutil
                    shutil.rmtree(file)
    # Ensure the directory exists
    build_dir.mkdir(parents=True, exist_ok=True)

def tornadovm_ide_init(tornado_sdk, java_home, backends):
    """
    Function to generate IDEA xml files for building and running TornadoVM.
    """

    if tornado_sdk == None:
        print("Cannot initiate ide. TORNADOVM_HOME is not defined")
        sys.exit(0)

    if java_home == None:
        print("Cannot initiate ide. JAVA_HOME is not defined")
        sys.exit(0)

    if backends == None:
        print("Cannot initiate ide. Backends are not defined")
        sys.exit(0)

    if os.name == 'nt':
        # Multiple paths of python executables may be listed, the first is selected as default
        python_home = subprocess.check_output(["where", "python"]).decode().strip().split("\r\n")
        python_home = Path(python_home[0])
    else:
        python_home = subprocess.check_output(["which", "python3"]).decode().strip()

    if python_home == None:
        print("python is not found in the PATH.")
        sys.exit(0)

    tornado_sdk_path = Path(tornado_sdk)
    project_directory = str(tornado_sdk_path)

    cleanup_build_directory(os.path.join(project_directory, ".build"))
    xml_templates_directory = os.path.join(project_directory, "scripts", "templates", "intellij-settings", "ideinit")

    generate_xml_files(xml_templates_directory, project_directory, tornado_sdk, java_home, python_home, backends)
