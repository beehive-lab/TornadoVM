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

__LINUX__   = "linux"
__APPLE__   = "darwin"
__WINDOWS__ = "windows"


# JDK 25-26 (TornadoVM needs JDK 25 or newer). JDK 27 isn't GA yet (due 2026-09-15): its builds are
# weekly-rotating early access until then, so a pinned URL here would go stale almost immediately -
# add it once it GAs.
#
# Not every vendor ships every feature version, so some keys below are intentionally absent
# rather than mapped to an all-None entry:
#   - GraalVM CE has no JDK 26 build; its newest track ("25 Innovation") still bundles JDK 25.
#   - Mandrel has no JDK 26 build; its newest track (25.0.x) still targets JDK 25.
#   - Microsoft Build of OpenJDK ships LTS only (25, ...): no JDK 26 build exists.

__JDK25__        = "jdk25"
__GRAALVM25__    = "graal-jdk-25"
__MANDREL25__    = "mandrel-jdk-25"
__CORRETTO25__   = "corretto-jdk-25"
__MICROSOFT25__  = "microsoft-jdk-25"
__ZULU25__       = "zulu-jdk-25"
__TEMURIN25__    = "temurin-jdk-25"
__SAPMACHINE25__ = "sapmachine-jdk-25"
__LIBERICA25__   = "liberica-jdk-25"

__JDK26__        = "jdk26"
__CORRETTO26__   = "corretto-jdk-26"
__ZULU26__       = "zulu-jdk-26"
__TEMURIN26__    = "temurin-jdk-26"
__SAPMACHINE26__ = "sapmachine-jdk-26"
__LIBERICA26__   = "liberica-jdk-26"

## cmake
CMAKE = {
    __LINUX__: {
        __X86_64__   : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.tar.gz",
        __ARM__      : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-aarch64.tar.gz",
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
        __X86_64__  : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __ARM__     : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
    },
    __APPLE__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
        __ARM__   : "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz",
    },
    __WINDOWS__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip",
        __ARM__: None,
    },
}

## JDK
JDK = {

    ## JDK - versions 22-26
    __JDK25__: {  # 25 is LTS and still in support: rolling "latest" alias, like 21 above
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/25/latest/jdk-25_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/25/latest/jdk-25_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/25/latest/jdk-25_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __JDK26__: {  # 26 is the newest release and not yet superseded: rolling "latest" alias
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/26/latest/jdk-26_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/26/latest/jdk-26_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/26/latest/jdk-26_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/26/latest/jdk-26_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/26/latest/jdk-26_windows-x64_bin.zip",
            __ARM__: None,
        },
    },

    ## GRAALVM - versions 22-26
    __GRAALVM25__: {  # GraalVM CE dropped Intel-macOS builds starting with the JDK 25 track (no ax entry)
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25.0.2/graalvm-community-jdk-25.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25.0.2/graalvm-community-jdk-25.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25.0.2/graalvm-community-jdk-25.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25.0.2/graalvm-community-jdk-25.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },

    ## CORRETTO - versions 22-26
    __CORRETTO25__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-25-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-25-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },
    __CORRETTO26__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-26-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-26-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-26-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-26-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-26-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },

    ## MANDREL - versions 22-26
    __MANDREL25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.4.0-Final/mandrel-java25-linux-amd64-25.0.4.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.4.0-Final/mandrel-java25-linux-aarch64-25.0.4.0-Final.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.4.0-Final/mandrel-java25-macos-aarch64-25.0.4.0-Final.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-25.0.4.0-Final/mandrel-java25-windows-amd64-25.0.4.0-Final.zip",
            __ARM__: None,
        },
    },

    ## MICROSOFT - versions 22-26
    __MICROSOFT25__: {
        __LINUX__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-linux-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-macos-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-windows-x64.zip",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-25.0.4-windows-aarch64.zip",
        },
    },

    ## ZULU - versions 22-26
    __ZULU25__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu25.36.15-ca-jdk25.0.4-win_x64.zip",
            __ARM__: None,
        },
    },
    __ZULU26__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu26.32.13-ca-jdk26.0.2-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu26.32.13-ca-jdk26.0.2-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu26.32.13-ca-jdk26.0.2-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu26.32.13-ca-jdk26.0.2-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu26.32.13-ca-jdk26.0.2-win_x64.zip",
            __ARM__: None,
        },
    },

    ## TEMURIN - versions 22-26
    __TEMURIN25__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4_7.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.4_7.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_mac_hotspot_25.0.4_7.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.4_7.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip",
            __ARM__: None,
        },
    },
    __TEMURIN26__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_x64_linux_hotspot_26.0.2_10.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_aarch64_linux_hotspot_26.0.2_10.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_x64_mac_hotspot_26.0.2_10.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_aarch64_mac_hotspot_26.0.2_10.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2%2B10/OpenJDK26U-jdk_x64_windows_hotspot_26.0.2_10.zip",
            __ARM__: None,
        },
    },

    ## SAPMACHINE - versions 22-26
    __SAPMACHINE25__: {  # SapMachine dropped Intel-macOS builds starting with the JDK 25 track (no ax entry)
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.4/sapmachine-jdk-25.0.4_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.4/sapmachine-jdk-25.0.4_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.4/sapmachine-jdk-25.0.4_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-25.0.4/sapmachine-jdk-25.0.4_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __SAPMACHINE26__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-26.0.2/sapmachine-jdk-26.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-26.0.2/sapmachine-jdk-26.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-26.0.2/sapmachine-jdk-26.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-26.0.2/sapmachine-jdk-26.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },

    ## LIBERICA - versions 22-26
    __LIBERICA25__: {
        __LINUX__: {
            __X86_64__: "https://download.bell-sw.com/java/25.0.4+9/bellsoft-jdk25.0.4+9-linux-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/25.0.4+9/bellsoft-jdk25.0.4+9-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.bell-sw.com/java/25.0.4+9/bellsoft-jdk25.0.4+9-macos-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/25.0.4+9/bellsoft-jdk25.0.4+9-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.bell-sw.com/java/25.0.4+9/bellsoft-jdk25.0.4+9-windows-amd64.zip",
            __ARM__: None,
        },
    },
    __LIBERICA26__: {
        __LINUX__: {
            __X86_64__: "https://download.bell-sw.com/java/26.0.2+13/bellsoft-jdk26.0.2+13-linux-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/26.0.2+13/bellsoft-jdk26.0.2+13-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.bell-sw.com/java/26.0.2+13/bellsoft-jdk26.0.2+13-macos-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/26.0.2+13/bellsoft-jdk26.0.2+13-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.bell-sw.com/java/26.0.2+13/bellsoft-jdk26.0.2+13-windows-amd64.zip",
            __ARM__: None,
        },
    },

}
