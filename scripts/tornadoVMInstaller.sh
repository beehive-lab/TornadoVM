#!/usr/bin/env bash

#  Copyright (c) 2020-2021, APT Group, Department of Computer Science,
#  The University of Manchester.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

DIRECTORY_DEPENDENCIES="etc/dependencies"

function getPlatform() {
    platform=$(uname | tr '[:upper:]' '[:lower:]')
    echo "$platform"
}

function checkJavaVersion() {
    platform=$(getPlatform)
    pass=$1
    if [ -z "$JAVA_HOME" ];
    then
	    echo "JAVA_HOME is not set. Use OpenJDK 8 >= 141 <= 1.9"
            if [[ "$platform" == 'linux' ]]; then
	    	# shellcheck disable=SC2028
	    	echo "\t You can use \`ls -l /etc/alternatives/java\` to get the PATHs"
	    elif [[ "$platform" == 'darwin' ]]; then
		    echo "\t You can use export JAVA_HOME=\$(/usr/libexec/java_home)"
	    fi
 	    pass=0
    else
 	    echo "JDK Version: OK"
    fi
    return $pass
}

function checkPrerequisites() {
    currentver="$(gcc -dumpversion)"
    requiredver="5.5.0"
    pass=1
    # if [ "$(printf '%s\n' "$requiredver" "$currentver" | sort -V | head -n1)" = "$requiredver" ]; then
    #     echo "GCC Vesion: OK"
    # else
    #     echo "Error: GCC Version is less than 5.5.0"
    #     pass=0
    # fi

    #$pass=checkJavaVersion($pass)

    if [[ $pass == 0 ]]; then
        exit
    fi
}

# Download OpenJDK with JVMCI support
function downloadOpenJDK8() {
    export JDK_BASE=$(pwd)
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        echo "Downloading JDK8 with JVMCI... ~100MB"
        wget https://github.com/graalvm/graal-jvmci-8/releases/download/jvmci-21.3-b05/openjdk-8u302+06-jvmci-21.3-b05-linux-amd64.tar.gz
	    tar xvzf openjdk-8u302+06-jvmci-21.3-b05-linux-amd64.tar.gz
        export JAVA_HOME=$JDK_BASE/openjdk1.8.0_302-jvmci-21.3-b05
    elif [[ "$platform" == 'darwin' ]]; then
        echo "JDK8 with JVMCI for Mac OSx is not supported for Graal 21.3"
        cd ../ && rm -rf $dirname
	exit 0
    else
        echo "OS platform not supported"
        cd ../ & rm -rf $dirname
        exit 0
    fi
}

function downloadOpenJDK11() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.13%2B8/OpenJDK11U-jdk_x64_linux_hotspot_11.0.13_8.tar.gz
        tar -xf OpenJDK11U-jdk_x64_linux_hotspot_11.0.13_8.tar.gz
        export JAVA_HOME=$PWD/jdk-11.0.13+8
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.13%2B8/OpenJDK11U-jdk_x64_mac_hotspot_11.0.13_8.tar.gz
        tar -xf OpenJDK11U-jdk_x64_mac_hotspot_11.0.13_8.tar.gz
        export JAVA_HOME=$PWD/jdk-11.0.13+8/Contents/Home/
    fi
}

function downloadOpenJDK17() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://download.java.net/java/GA/jdk17.0.1/2a2082e5a09d4267845be086888add4f/12/GPL/openjdk-17.0.1_linux-x64_bin.tar.gz
        tar -xf openjdk-17.0.1_linux-x64_bin.tar.gz
        export JAVA_HOME=$PWD/jdk-17.0.1
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://download.java.net/java/GA/jdk17.0.1/2a2082e5a09d4267845be086888add4f/12/GPL/openjdk-17.0.1_macos-x64_bin.tar.gz
        tar -xf openjdk-17.0.1_macos-x64_bin.tar.gz
        export JAVA_HOME=$PWD/jdk-17.0.1/Contents/Home/
    fi
}

function downloadGraalVMJDK11() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java11-linux-amd64-21.3.0.tar.gz
	tar -xf graalvm-ce-java11-linux-amd64-21.3.0.tar.gz
	export JAVA_HOME=$PWD/graalvm-ce-java11-21.3.0
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java11-darwin-amd64-21.3.0.tar.gz
        tar -xf graalvm-ce-java11-darwin-amd64-21.3.0.tar.gz
        export JAVA_HOME=$PWD/graalvm-ce-java11-21.3.0/Contents/Home/
    fi
}

function downloadGraalVMJDK17() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java17-linux-amd64-21.3.0.tar.gz
        tar -xf graalvm-ce-java17-linux-amd64-21.3.0.tar.gz
        export JAVA_HOME=$PWD/graalvm-ce-java17-21.3.0
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.3.0/graalvm-ce-java17-darwin-amd64-21.3.0.tar.gz
        tar -xf graalvm-ce-java17-darwin-amd64-21.3.0.tar.gz
        export JAVA_HOME=$PWD/graalvm-ce-java17-21.3.0/Contents/Home/
    fi
}

function downloadCorretto11() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz
        tar xf amazon-corretto-11-x64-linux-jdk.tar.gz
        export JAVA_HOME=$PWD/amazon-corretto-11.0.13.8.1-linux-x64/
    elif [[ "$platform" == 'darwin' ]]; then
	wget https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz
        tar xf amazon-corretto-11-x64-macos-jdk.tar.gz
        export JAVA_HOME=$PWD/amazon-corretto-11.jdk/Contents/Home
    fi
}

function downloadCorretto17() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz
        tar xf amazon-corretto-17-x64-linux-jdk.tar.gz
        export JAVA_HOME=$PWD/amazon-corretto-17.0.1.12.1-linux-x64
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://corretto.aws/downloads/latest/amazon-corretto-17-x64-macos-jdk.tar.gz
        tar xf amazon-corretto-17-x64-macos-jdk.tar.gz
        export JAVA_HOME=$PWD/amazon-corretto-17.jdk/Contents/Home
    fi
}

function downloadMandrel11() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://github.com/graalvm/mandrel/releases/download/mandrel-21.3.0.0-Final/mandrel-java11-linux-amd64-21.3.0.0-Final.tar.gz
        tar xf mandrel-java11-linux-amd64-21.3.0.0-Final.tar.gz
        export JAVA_HOME=$PWD/mandrel-java11-21.3.0.0-Final
    elif [[ "$platform" == 'darwin' ]]; then
        echo "OS Not supported"
        cd ../ && rm -rf $dirname
        exit 0
    fi
}

function downloadMandrel17() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://github.com/graalvm/mandrel/releases/download/mandrel-21.3.0.0-Final/mandrel-java17-linux-amd64-21.3.0.0-Final.tar.gz
        tar xf mandrel-java17-linux-amd64-21.3.0.0-Final.tar.gz
        export JAVA_HOME=$PWD/mandrel-java17-21.3.0.0-Final
    elif [[ "$platform" == 'darwin' ]]; then
        echo "OS Not supported"
        cd ../ && rm -rf $dirname
        exit 0
    fi
}

function downloadWindowsJDK11() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://aka.ms/download-jdk/microsoft-jdk-11.0.13.8.1-linux-x64.tar.gz
        tar xf microsoft-jdk-11.0.13.8.1-linux-x64.tar.gz
        export JAVA_HOME=$PWD/jdk-11.0.13+8
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://aka.ms/download-jdk/microsoft-jdk-11.0.13.8.1-macOS-x64.tar.gz
        tar xf microsoft-jdk-11.0.12.7.1-macos-x64.tar.gz
        export JAVA_HOME=$PWD/jdk-11.0.12+7/Contents/Home
    fi
}

function downloadWindowsJDK17() {
    platform=$(getPlatform)
    if [[ "$platform" == 'linux' ]]; then
        wget https://aka.ms/download-jdk/microsoft-jdk-17.0.1.12.1-linux-x64.tar.gz
        tar xf microsoft-jdk-17.0.1.12.1-linux-x64.tar.gz
        export JAVA_HOME=$PWD/jdk-17.0.1+12
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://aka.ms/download-jdk/microsoft-jdk-17.0.1.12.1-macOS-x64.tar.gz
        tar xf microsoft-jdk-11.0.13.8.1-macOS-x64.tar.gz
        export JAVA_HOME=$PWD/jdk-11.0.13+8/Contents/Home
    fi
}

function downloadCMake01() {
    platform=$1
    if [[ "$platform" == 'linux' ]]; then
	    wget https://github.com/Kitware/CMake/releases/download/v3.22.1/cmake-3.22.1-linux-x86_64.tar.gz
    elif [[ "$platform" == 'darwin' ]]; then
        wget https://github.com/Kitware/CMake/releases/download/v3.22.1/cmake-3.22.1-macos-universal.tar.gz
    else
        echo "OS platform not supported"
        exit 0
    fi
}

function unZipAndSetCmake() {
    platform=$1
    if [[ "$platform" == 'linux' ]]; then
        tar xzf cmake-3.22.1-linux-x86_64.tar.gz
        export PATH=`pwd`/cmake-3.22.1-linux-x86_64/bin:$PATH
        export CMAKE_ROOT=`pwd`/cmake-3.22.1-linux-x86_64/
    elif [[ "$platform" == 'darwin' ]]; then
        tar xfz cmake-3.22.1-macos-universal.tar.gz
        export PATH=`pwd`/cmake-3.22.1-macos-universal/CMake.app/Contents/bin:$PATH
        export CMAKE_ROOT=`pwd`/cmake-3.22.1-macos-universal/CMake.app/Contents
    else
        echo "OS platform not supported"
        exit 0
    fi
}

# Download CMAKE
function downloadCMake() {
    platform=$(getPlatform)
    FILE="cmake-3.22.1-linux-x86_64.tar.gz"
    if [ ! -f "$FILE" ]; then
        downloadCMake01 $platform
        unZipAndSetCmake $platform
    else
        unZipAndSetCmake $platform
    fi
}

function resolveBackends() {
    if [ $opencl ]; then
        b="${b}opencl,"
    fi

    if [ $ptx ]; then
        b="${b}ptx,"
    fi

    if [ $spirv ]; then
        b="${b}spirv,"
    fi

    # Remove last comma
    b=${b%?}
    backend="BACKEND=$b"
}

# Download and Install TornadoVM
function setupTornadoVMStandalone() {
    if [ ! -d TornadoVM ]; then
        git clone --depth 1 https://github.com/beehive-lab/TornadoVM
    else
        cd TornadoVM
        git pull
        cd -
    fi
    cd TornadoVM
    export PATH=$PWD/bin/bin:$PATH
    export TORNADO_SDK=$PWD/bin/sdk
    resolveBackends
    make $1 $backend
}

function setupTornadoVM() {
    export PATH=$PWD/bin/bin:$PATH
    export TORNADO_SDK=$PWD/bin/sdk
    resolveBackends
    make $1 $backend
}

function setupVariables() {
    DIR=$1
    echo -e "To use TornadoVM, export the following variables:\n"
    echo "Creating Source File ....... "
	  echo "export JAVA_HOME=$JAVA_HOME" > source.sh
	  echo "export PATH=$PWD/bin/bin:\$PATH" >> source.sh
	  echo "export TORNADO_SDK=$PWD/bin/sdk" >> source.sh
	  echo "export CMAKE_ROOT=$CMAKE_ROOT" >> source.sh
	  echo "export TORNADO_ROOT=$PWD " >> source.sh
    echo "........................... [OK]"

    echo -e "\nTo run TornadoVM, run \`. source.sh\`"
}

function installForOpenJDK8() {
    checkPrerequisites
    dirname=$DIRECTORY_DEPENDENCIES"/TornadoVM-OpenJDK8"
    mkdir -p $dirname
    cd $dirname
    downloadOpenJDK8
    downloadCMake
    cd -
    setupTornadoVM jdk-8
    setupVariables $dirname
}

function installForOpenJDK11() {
    checkPrerequisites
    dirname="TornadoVM-OpenJDK11"
    mkdir -p $dirname
    cd $dirname
    downloadOpenJDK11
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForOpenJDK17() {
    checkPrerequisites
    dirname=${DIRECTORY_DEPENDENCIES}"/TornadoVM-OpenJDK17"
    mkdir -p $dirname
    cd $dirname
    downloadOpenJDK17
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForGraalJDK11() {
    checkPrerequisites
    dirname="TornadoVM-GraalJDK11"
    mkdir -p $dirname
    cd $dirname
    downloadGraalVMJDK11
    downloadCMake
    cd -
    setupTornadoVM graal-jdk-11-plus
    setupVariables $dirname
}

function installForGraalJDK17() {
    checkPrerequisites
    dirname="TornadoVM-GraalJDK17"
    mkdir -p $dirname
    cd $dirname
    downloadGraalVMJDK17
    downloadCMake
    cd -
    setupTornadoVM graal-jdk-11-plus
    setupVariables $dirname
}

function installForCorrettoJDK11() {
    checkPrerequisites
    dirname="TornadoVM-Amazon-Corretto11"
    mkdir -p $dirname
    cd $dirname
    downloadCorretto11
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForCorrettoJDK17() {
    checkPrerequisites
    dirname="TornadoVM-Amazon-Corretto17"
    mkdir -p $dirname
    cd $dirname
    downloadCorretto17
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForMandrelJDK11() {
    checkPrerequisites
    dirname="TornadoVM-RedHat-Mandrel11"
    mkdir -p $dirname
    cd $dirname
    downloadMandrel11
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForMandrelJDK17() {
    checkPrerequisites
    dirname="TornadoVM-RedHat-Mandrel17"
    mkdir -p $dirname
    cd $dirname
    downloadMandrel17
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForWindowsJDK11() {
    checkPrerequisites
    dirname="TornadoVM-Windows-JDK11"
    mkdir -p $dirname
    cd $dirname
    downloadWindowsJDK11
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function installForWindowsJDK17() {
    checkPrerequisites
    dirname="TornadoVM-Windows-JDK17"
    mkdir -p $dirname
    cd $dirname
    downloadWindowsJDK17
    downloadCMake
    cd -
    setupTornadoVM jdk-11-plus
    setupVariables $dirname
}

function printHelp() {
    echo "TornadoVM installer for Linux and OSx"
    echo "Usage:"
    echo "       --jdk8           : Install TornadoVM with OpenJDK 8"
    echo "       --jdk11          : Install TornadoVM with OpenJDK 11"
    echo "       --jdk17          : Install TornadoVM with OpenJDK 17"
    echo "       --graal-jdk-11   : Install TornadoVM with GraalVM and JDK 11 (GraalVM 21.3.0)"
    echo "       --graal-jdk-17   : Install TornadoVM with GraalVM and JDK 17 (GraalVM 21.3.0)"
    echo "       --corretto-11    : Install TornadoVM with Corretto JDK 11"
    echo "       --corretto-17    : Install TornadoVM with Corretto JDK 17"
    echo "       --mandrel-11     : Install TornadoVM with Mandrel 21.3.0 (JDK 11)"
    echo "       --mandrel-17     : Install TornadoVM with Mandrel 21.3.0 (JDK 17)"
    echo "       --windows-jdk-11 : Install TornadoVM with Windows JDK 11"
    echo "       --windows-jdk-17 : Install TornadoVM with Windows JDK 17"
    echo "       --opencl         : Install TornadoVM and build the OpenCL backend"
    echo "       --ptx            : Install TornadoVM and build the PTX backend"
    echo "       --spirv          : Install TornadoVM and build the SPIR-V backend"
    echo "       --help           : Print this help"
    exit 0
}

function setBackend() {
  # shellcheck disable=SC2068
  for i in ${args[@]}
  do
    flag=$i
    if [[ "$flag" == '--opencl' ]]; then
      opencl=true
    elif [[ "$flag" == '--ptx' ]]; then
      ptx=true
    elif [[ "$flag" == '--spirv' ]]; then
      spirv=true
    fi
  done
}

POSITIONAL=()

if [[ $# == 0 ]]
then
    printHelp
    exit
fi

args=( "$@" )

setBackend

# shellcheck disable=SC2068
for i in ${args[@]}
do
  key=$i
  case $key in
  --help)
    printHelp
    shift
    ;;
  --jdk8)
    installForOpenJDK8
    shift
    ;;
  --jdk11)
    installForOpenJDK11
    shift
    ;;
  --jdk17)
    installForOpenJDK17
    shift
    ;;
  --graal-jdk-11)
    installForGraalJDK11
    shift
    ;;
  --graal-jdk-17)
    installForGraalJDK17
    shift
    ;;
  --corretto-11)
    installForCorrettoJDK11
    shift
    ;;
  --corretto-17)
    installForCorrettoJDK17
    shift
    ;;
  --mandrel-11)
    installForMandrelJDK11
    shift
    ;;
  --mandrel-17)
    installForMandrelJDK17
    shift
    ;;
  --windows-jdk-11)
    installForWindowsJDK11
    shift
    ;;
  --windows-jdk-17)
    installForWindowsJDK17
    shift
    ;;
  --opencl)
    shift
    ;;
  --ptx)
    shift
    ;;
  --spirv)
    shift
    ;;
  *)
    printHelp
    shift
    ;;
  esac
done
