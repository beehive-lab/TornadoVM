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
            __X86_64__: "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_linux-x64_bin.tar.gz"",
            __ARM__: "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_linux-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_macos-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_windows-x64_bin.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
    __GRAALVM25__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "None",
            __ARM__: "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_macos-aarch64_bin.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_windows-x64_bin.zip",
            __ARM__: None,
           __RISCV_64__: None,
        },
    },
       __CORRETTO25__: {
           __LINUX__: {
               __X86_64__: "https://corretto.aws/downloads/resources/25.0.2.10.1/amazon-corretto-25.0.2.10.1-linux-x64.tar.gz",
               __ARM__: "https://corretto.aws/downloads/resources/25.0.2.10.1/amazon-corretto-25.0.2.10.1-linux-aarch64.tar.gz",
               __RISCV_64__: None,
           },
           __APPLE__: {
               __X86_64__: "https://corretto.aws/downloads/resources/25.0.2.10.1/amazon-corretto-25.0.2.10.1-macosx-x64.tar.gz",
               __ARM__: "https://corretto.aws/downloads/resources/25.0.2.10.1/amazon-corretto-25.0.2.10.1-macosx-aarch64.tar.gz",
               __RISCV_64__: None,
           },
           __WINDOWS__: {
               __X86_64__: "https://corretto.aws/downloads/resources/25.0.2.10.1/amazon-corretto-25.0.2.10.1-windows-x64-jdk.zip",
               __ARM__: None,
               __RISCV_64__: None,
           },
       },
        __MANDREL25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.2.0-Final/mandrel-java25-linux-amd64-25.0.2.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.2.0-Final/mandrel-java25-linux-aarch64-25.0.2.0-Final.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.2.0-Final/mandrel-java25-macos-aarch64-25.0.2.0-Final.tar.gz",
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
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-linux-aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __WINDOWS__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-windows-x64.zip",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jjdk-25.0.2-windows-aarch64.zip",
           __RISCV_64__: None,
        },
    }
    __ZULU25__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-jdk25.0.2-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-jdk25.0.2-linux_aarch64.tar.gz",
           __RISCV_64__: None,
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-jdk25.0.2-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-jdk25.0.2-macosx_aarch64.tar.gz",
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
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.2_10.tar.gz",
            __RISCV_64__: null,
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_mac_hotspot_25.0.2_10.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.2_10.tar.gz",
            __RISCV_64__: null,
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_x64_windows_hotspot_25.0.2_10.zip",
            __ARM__: null,
            __RISCV_64__: null,
        },
    },
    __SAPMACHINE25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.2/sapmachine-jdk-25.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.2/sapmachine-jdk-25.0.2_linux-aarch64_bin.tar.gz",
            __RISCV_64__: null,
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.2/sapmachine-jdk-25.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.2/sapmachine-jdk-25.0.2_macos-aarch64_bin.tar.gz",
            __RISCV_64__: null,
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.2/sapmachine-jdk-25.0.2_windows-x64_bin.zip",
            __ARM__: null,
            __RISCV_64__: null,
        },
    },

   __LIBERICA25__: {
       __LINUX__: {
           __X86_64__: "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-linux-amd64.tar.gz",
           __ARM__:    "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-linux-aarch64.tar.gz",
           __RISCV_64__:"https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-linux-riscv64.tar.gz",
       },
       __APPLE__: {
           __X86_64__: "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-macos-amd64.tar.gz",
           __ARM__:    "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-macos-aarch64.tar.gz",
           __RISCV_64__: null,
       },
       __WINDOWS__: {
           __X86_64__: "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-windows-amd64.zip",
           __ARM__:    "https://download.bell-sw.com/java/25.0.2+12/bellsoft-jdk25.0.2+12-windows-aarch64.zip",
           __RISCV_64__: null,
       },
   },
}
