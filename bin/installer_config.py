#!/usr/bin/env python3

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

import platform

__X86_64__ = "x86_64"
__ARM__ = "arm64"

__LINUX__ = "linux"
__APPLE__ = "darwin"
__WINDOWS__ = "windows"

__JDK22__ = "jdk22"
__GRAALVM22__ = "graal-jdk-22"
__MANDREL22__ = "mandrel-jdk-22"
__CORRETTO22__ = "corretto-jdk-22"
__MICROSOFT22__ = "microsoft-jdk-22"
__ZULU22__ = "zulu-jdk-22"
__TEMURIN22__ = "temurin-jdk-22"
__SAPMACHINE21__ = "sapmachine-jdk-22"

## cmake
CMAKE = {
    __LINUX__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.tar.gz",
        __ARM__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-aarch64.tar.gz",
    },
    __APPLE__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
        __ARM__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
    },
    __WINDOWS__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.26.3/cmake-3.26.3-windows-x86_64.zip",
        __ARM__: None,
    },
}

## Maven
MAVEN = {
    __LINUX__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
        __ARM__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
    },
    __APPLE__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
        __ARM__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
    },
    __WINDOWS__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.zip",
        __ARM__: None,
    },
}

## JDK
JDK = {
    __JDK22__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/22/latest/jdk-22_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/22/latest/jdk-22_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/22/latest/jdk-22_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/22/latest/jdk-22_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __GRAALVM22__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-22.0.2/graalvm-community-jdk-22.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-22.0.2/graalvm-community-jdk-22.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-22.0.2/graalvm-community-jdk-22.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-22.0.2/graalvm-community-jdk-22.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-22.0.2/graalvm-community-jdk-22.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __CORRETTO22__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-22-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-22-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-22-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-22-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-22-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },
    __MANDREL22__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.0.2.0-Final/mandrel-java22-linux-amd64-24.0.2.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.0.2.0-Final/mandrel-java22-linux-aarch64-24.0.2.0-Final.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.0.2.0-Final/mandrel-java22-macos-aarch64-24.0.2.0-Final.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.0.2.0-Final/mandrel-java22-windows-amd64-24.0.2.0-Final.zip",
            __ARM__: None,
        },
    },
    __MICROSOFT22__: {
        __LINUX__: {
            __X86_64__: None,
            __ARM__: None,
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: None,
        },
        __WINDOWS__: {
            __X86_64__: None,
            __ARM__: None,
        },
    },
    __ZULU22__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: None,
            __ARM__: None,
        },
    },
    __TEMURIN22__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_x64_linux_hotspot_22.0.1_8.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_aarch64_linux_hotspot_22.0.1_8.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_x64_mac_hotspot_22.0.1_8.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_aarch64_mac_hotspot_22.0.1_8.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.1%2B8/OpenJDK22U-jdk_x64_windows_hotspot_22.0.1_8.zip",
            __ARM__: None,
        },
    },
    __SAPMACHINE22__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-22.0.2/sapmachine-jdk-22.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-22.0.2/sapmachine-jdk-22.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-22.0.2/sapmachine-jdk-22.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-22.0.2/sapmachine-jdk-22.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-22.0.2/sapmachine-jdk-22.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
}
