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

import platform

__X86_64__   = "x86_64"
__ARM__      = "arm64"
__RISCV_64__ = "riscv64" 

__LINUX__   = "linux"
__APPLE__   = "darwin"
__WINDOWS__ = "windows"

__JDK25__        = "jdk25"
__GRAALVM25__    = "graal-jdk-25"
__MANDREL25__    = "mandrel-jdk-25"
__CORRETTO25__   = "corretto-jdk-25"
__MICROSOFT25__  = "microsoft-jdk-25"
__ZULU25__       = "zulu-jdk-25"
__TEMURIN25__    = "temurin-jdk-25"
__SAPMACHINE25__ = "sapmachine-jdk-25"
__LIBERICA25__   = "liberica-jdk-25"

## cmake
CMAKE = {
    __LINUX__: {
        __X86_64__   : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.tar.gz",
        __ARM__      : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-aarch64.tar.gz",
        __RISCV_64__ : None,
    },
    __APPLE__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
        __ARM__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
        __RISCV_64__ : None,
    },
    __WINDOWS__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.26.3/cmake-3.26.3-windows-x86_64.zip",
        __ARM__: None,
        __RISCV_64__ : None,
    },
}

## Maven
MAVEN = {
    __LINUX__: {
        __X86_64__  : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __ARM__     : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __RISCV_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
    },
    __APPLE__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __ARM__   : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __RISCV_64__ : None,
    },
    __WINDOWS__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip",
        __ARM__: None,
        __RISCV_64__ : None,
    },
}

## JDK
JDK = {
    __JDK25__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_linux-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_macos-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/21/archive/jdk-21.0.1_windows-x64_bin.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __GRAALVM25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.1/graalvm-community-jdk-21.0.1_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.1/graalvm-community-jdk-21.0.1_linux-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.1/graalvm-community-jdk-21.0.1_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.1/graalvm-community-jdk-21.0.1_macos-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.1/graalvm-community-jdk-21.0.1_windows-x64_bin.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __CORRETTO25__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-linux-jdk.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-macos-jdk.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: None,
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __MANDREL25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.0.0-Final/mandrel-java21-linux-amd64-23.1.0.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.0.0-Final/mandrel-java21-linux-aarch64-23.1.0.0-Final.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: None,
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: None,
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __MICROSOFT25__: {
        __LINUX__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-linux-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-linux-aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-macos-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-macos-aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-windows-x64.zip",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-windows-aarch64.zip",
           __RISCV_64__: None,
        },
    },
    __ZULU25__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-linux_aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-macosx_aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: None,
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __TEMURIN25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_x64_linux_hotspot_21.0.1_12.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.1_12.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_x64_mac_hotspot_21.0.1_12.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.1_12.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.3_9.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __SAPMACHINE25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_linux-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_macos-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_windows-x64_bin.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },

    __LIBERICA25__ : {
        __LINUX__: {
            __X86_64__  : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-linux-amd64.tar.gz",
            __ARM__     : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-linux-aarch64.tar.gz",
            __RISCV_64__: "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-linux-riscv64.tar.gz",
       },
        __APPLE__ : {
            __X86_64__:  "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-macos-amd64.tar.gz",
            __ARM__   : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-macos-aarch64.tar.gz",
           __RISCV_64__: None,
       },
       __WINDOWS__: {
           __X86_64__: "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-windows-amd64.zip",
           __ARM__   : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-windows-aarch64.zip",
           __RISCV_64__: None,
      },
    },
}
