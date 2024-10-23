#!/usr/bin/env python3

#
# Copyright (c) 2024, APT Group, Department of Computer Science,
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

def define_and_get_internal_maven_content(TORNADO_ROOT, JAVA_HOME, BACKEND_PROFILES):
    maven_directory = os.path.join(str(TORNADO_ROOT), "etc", "dependencies", "apache-maven-3.9.3")
    if "graal" in JAVA_HOME:
        JAVA_PROFILE = "graal-jdk-21"
    else:
        JAVA_PROFILE = "jdk21"
    
    xml_internal_maven_build_content = f"""<?xml version="1.0" encoding="UTF-8"?>
    <component name="ProjectRunConfigurationManager">
      <configuration default="false" name="_internal_TornadoVM_Maven-cleanAndinstall" type="MavenRunConfiguration" factoryName="Maven">
        <MavenSettings>
          <option name="myGeneralSettings">
            <MavenGeneralSettings>
              <option name="alwaysUpdateSnapshots" value="false" />
              <option name="checksumPolicy" value="NOT_SET" />
              <option name="emulateTerminal" value="true" />
              <option name="failureBehavior" value="NOT_SET" />
              <option name="localRepository" value="" />
              <option name="mavenHome" value= "{maven_directory}" />
              <option name="nonRecursive" value="false" />
              <option name="outputLevel" value="INFO" />
              <option name="printErrorStackTraces" value="false" />
              <option name="showDialogWithAdvancedSettings" value="false" />
              <option name="threads" />
              <option name="useMavenConfig" value="true" />
              <option name="usePluginRegistry" value="false" />
              <option name="userSettingsFile" value="" />
              <option name="workOffline" value="false" />
            </MavenGeneralSettings>
          </option>
          <option name="myRunnerSettings" />
          <option name="myRunnerParameters">
            <MavenRunnerParameters>
              <option name="cmdOptions" />
              <option name="profiles">
                <set />
              </option>
              <option name="goals">
                <list>
                  <option value="clean" />
                  <option value="install" />
                </list>
              </option>
              <option name="pomFileName" />
              <option name="profilesMap">
                <map>
                  <entry key="{JAVA_PROFILE}" value="true" />
                  <entry key="{BACKEND_PROFILES}" value="true" />
                </map>
              </option>
              <option name="projectsCmdOptionValues">
                <list />
              </option>
              <option name="resolveToWorkspace" value="false" />
              <option name="workingDirPath" value="{TORNADO_ROOT}" />
            </MavenRunnerParameters>
          </option>
        </MavenSettings>
        <method v="2" />
      </configuration>
    </component>"""
    return xml_internal_maven_build_content

def define_and_get_tornadovm_build_content(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME, BACKEND_PROFILES):
    post_installation_script_directory = os.path.join(str(TORNADO_ROOT), "bin", "post_installation.py")
    python_version = subprocess.check_output([str(PYTHON_HOME), "--version"]).decode().strip()
    python_version_name = python_version.split()[1]
    python_sdk_home = str(PYTHON_HOME)
    python_sdk_name = f"Python {python_version_name}"

    xml_TornadoVM_build_content = f"""<?xml version="1.0" encoding="UTF-8"?>
    <component name="ProjectRunConfigurationManager">
        <configuration default="false" name="TornadoVM-Build" type="PythonConfigurationType"
                       factoryName="Python">
            <module name="tornado-annotation"/>
            <option name="INTERPRETER_OPTIONS" value=""/>
            <option name="PARENT_ENVS" value="true"/>
            <envs>
                <env name="PYTHONUNBUFFERED" value="1"/>
                <env name="BACKEND" value="opencl"/>
                <env name="TORNADO_SDK" value="{TORNADO_SDK}"/>
                <env name="JAVA_HOME" value="{JAVA_HOME}" />
                <env name="selected_backends" value="{BACKEND_PROFILES}"/>
            </envs>
            <option name="SDK_HOME" value="{python_sdk_home}"/>
            <option name="SDK_NAME" value="{python_sdk_name}"/>
            <option name="WORKING_DIRECTORY" value="{TORNADO_ROOT}"/>
            <option name="IS_MODULE_SDK" value="false"/>
            <option name="ADD_CONTENT_ROOTS" value="true"/>
            <option name="ADD_SOURCE_ROOTS" value="true"/>
            <EXTENSION ID="PythonCoverageRunConfigurationExtension" runner="coverage.py"/>
            <option name="SCRIPT_NAME" value="{post_installation_script_directory}"/>
            <option name="PARAMETERS" value=""/>
            <option name="SHOW_COMMAND_LINE" value="false"/>
            <option name="EMULATE_TERMINAL" value="false"/>
            <option name="MODULE_MODE" value="false"/>
            <option name="REDIRECT_INPUT" value="false"/>
            <option name="INPUT_FILE" value=""/>
            <method v="2">
                <option name="RunConfigurationTask" enabled="true"
                        run_configuration_name="_internal_TornadoVM_Maven-cleanAndinstall"
                        run_configuration_type="MavenRunConfiguration"/>
            </method>
        </configuration>
    </component>"""
    return xml_TornadoVM_build_content

def define_and_get_tornadovm_tests_content(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME):
    test_script_directory = os.path.join(str(TORNADO_SDK), "bin", "tornado-test")
    python_version = subprocess.check_output([str(PYTHON_HOME), "--version"]).decode().strip()
    python_version_name = python_version.split()[1]
    python_sdk_home = str(PYTHON_HOME)
    python_sdk_name = f"Python {python_version_name}"

    xml_TornadoVM_tests_content = f"""<?xml version="1.0" encoding="UTF-8"?>
    <component name="ProjectRunConfigurationManager">
      <configuration default="false" name="TornadoVM-Tests" type="PythonConfigurationType" factoryName="Python">
        <module name="tornado-annotation" />
        <option name="ENV_FILES" value="" />
        <option name="INTERPRETER_OPTIONS" value="" />
        <option name="PARENT_ENVS" value="true" />
        <envs>
          <env name="BACKEND" value="opencl" />
          <env name="PYTHONUNBUFFERED" value="1" />
          <env name="selected_backends" value="opencl-backend" />
          <env name="TORNADO_SDK" value="{TORNADO_SDK}" />
          <env name="JAVA_HOME" value="{JAVA_HOME}" />
        </envs>
        <option name="SDK_HOME" value="{python_sdk_home}"/>
        <option name="SDK_NAME" value="{python_sdk_name}"/>
        <option name="WORKING_DIRECTORY" value="{TORNADO_ROOT}" />:wq
        <option name="IS_MODULE_SDK" value="false" />
        <option name="ADD_CONTENT_ROOTS" value="true" />
        <option name="ADD_SOURCE_ROOTS" value="true" />
        <EXTENSION ID="PythonCoverageRunConfigurationExtension" runner="coverage.py" />
        <option name="SCRIPT_NAME" value="{TORNADO_SDK}/bin/tornado-test" />
        <option name="PARAMETERS" value="--ea --verbose --quickPass" />
        <option name="SHOW_COMMAND_LINE" value="false" />
        <option name="EMULATE_TERMINAL" value="false" />
        <option name="MODULE_MODE" value="false" />
        <option name="REDIRECT_INPUT" value="false" />
        <option name="INPUT_FILE" value="" />
        <method v="2" />
      </configuration>
    </component>"""
    return xml_TornadoVM_tests_content

def generate_internal_maven_build_xml(TORNADO_ROOT, JAVA_HOME, BACKEND_PROFILES):
    xml_internal_content = define_and_get_internal_maven_content(TORNADO_ROOT, JAVA_HOME, BACKEND_PROFILES)
    xml_directory = os.path.join(str(TORNADO_ROOT), ".build", "_internal_TornadoVM_Maven-cleanAndinstall.run.xml")
    with open(xml_directory, "w") as file:
        file.write(xml_internal_content)

def generate_tornadovm_build_xml(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME, BACKEND_PROFILES):
    xml_build_content = define_and_get_tornadovm_build_content(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME, BACKEND_PROFILES)
    xml_directory = os.path.join(str(TORNADO_ROOT), ".build", "TornadoVM-Build.run.xml")
    with open(xml_directory, "w") as file:
        file.write(xml_build_content)

def generate_tornadovm_tests_xml(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME):
    xml_tests_content = define_and_get_tornadovm_tests_content(TORNADO_ROOT, TORNADO_SDK, JAVA_HOME, PYTHON_HOME)
    xml_directory = os.path.join(str(TORNADO_ROOT), ".build", "TornadoVM-Tests.run.xml")
    with open(xml_directory, "w") as file:
        file.write(xml_tests_content)

def cleanup_build_directory(buildDirectoryString):
    tornadoRootDirectory = Path(buildDirectoryString)
    if tornadoRootDirectory.exists() and tornadoRootDirectory.is_dir():
        for file in tornadoRootDirectory.iterdir():
            if file.is_file():
                file.unlink()

def tornadovm_ide_init(tornadoSDKPath, javaHome, backends):
    """
    Function to generate IDEA xml files for building and running TornadoVM.
    """

    if tornadoSDKPath == None:
        print("Cannot initiate ide. TORNADO_SDK is not defined")
        sys.exit(0)

    if javaHome == None:
        print("Cannot initiate ide. JAVA_HOME is not defined")
        sys.exit(0)

    if backends == None:
        print("Cannot initiate ide. Backends are not defined")
        sys.exit(0)

    backends_separated_comma = ",".join(backends)
    backends_separated_space = " ".join(backends)

    if os.name == 'nt':
        pythonHome = subprocess.check_output(["where", "python"]).decode().strip()
    else:
        pythonHome = subprocess.check_output(["which", "python3"]).decode().strip()

    if pythonHome == None:
        print("python is not found in the PATH.")
        sys.exit(0)

    tornadoRoot = Path(tornadoSDKPath)
    tornadoRoot = tornadoRoot.parents[1]

    cleanup_build_directory(os.path.join(str(tornadoRoot), ".build"))

    generate_internal_maven_build_xml(tornadoRoot, javaHome, backends_separated_space)
    generate_tornadovm_build_xml(tornadoRoot, tornadoSDKPath, javaHome, pythonHome, backends_separated_comma)
    generate_tornadovm_tests_xml(tornadoRoot, tornadoSDKPath, javaHome, pythonHome)
