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

__JDK21__        = "jdk21"
__GRAALVM21__    = "graal-jdk-21"
__MANDREL21__    = "mandrel-jdk-21"
__CORRETTO21__   = "corretto-jdk-21"
__MICROSOFT21__  = "microsoft-jdk-21"
__ZULU21__       = "zulu-jdk-21"
__TEMURIN21__    = "temurin-jdk-21"
__SAPMACHINE21__ = "sapmachine-jdk-21"
__LIBERICA21__   = "liberica-jdk-21"

# JDK 22-26. JDK 27 isn't GA yet (due 2026-09-15): its builds are weekly-rotating early access
# until then, so a pinned URL here would go stale almost immediately - add it once it GAs.
#
# Not every vendor ships every feature version, so some keys below are intentionally absent
# rather than mapped to an all-None entry:
#   - GraalVM CE has no JDK 26 build; its newest track ("25 Innovation") still bundles JDK 25.
#   - Mandrel has no JDK 26 build; its newest track (25.0.x) still targets JDK 25.
#   - Microsoft Build of OpenJDK ships LTS only (21, 25, ...): no JDK 22/23/24/26 builds exist.
__JDK22__        = "jdk22"
__GRAALVM22__    = "graal-jdk-22"
__MANDREL22__    = "mandrel-jdk-22"
__CORRETTO22__   = "corretto-jdk-22"
__ZULU22__       = "zulu-jdk-22"
__TEMURIN22__    = "temurin-jdk-22"
__SAPMACHINE22__ = "sapmachine-jdk-22"
__LIBERICA22__   = "liberica-jdk-22"

__JDK23__        = "jdk23"
__GRAALVM23__    = "graal-jdk-23"
__MANDREL23__    = "mandrel-jdk-23"
__CORRETTO23__   = "corretto-jdk-23"
__ZULU23__       = "zulu-jdk-23"
__TEMURIN23__    = "temurin-jdk-23"
__SAPMACHINE23__ = "sapmachine-jdk-23"
__LIBERICA23__   = "liberica-jdk-23"

__JDK24__        = "jdk24"
__GRAALVM24__    = "graal-jdk-24"
__MANDREL24__    = "mandrel-jdk-24"
__CORRETTO24__   = "corretto-jdk-24"
__ZULU24__       = "zulu-jdk-24"
__TEMURIN24__    = "temurin-jdk-24"
__SAPMACHINE24__ = "sapmachine-jdk-24"
__LIBERICA24__   = "liberica-jdk-24"

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
    __JDK21__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __GRAALVM21__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __CORRETTO21__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },
    __MANDREL21__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.12.0-Final/mandrel-java21-linux-amd64-23.1.12.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.12.0-Final/mandrel-java21-linux-aarch64-23.1.12.0-Final.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.12.0-Final/mandrel-java21-macos-aarch64-23.1.12.0-Final.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.1.12.0-Final/mandrel-java21-windows-amd64-23.1.12.0-Final.zip",
            __ARM__: None,
        },
    },
    __MICROSOFT21__: {
        __LINUX__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-linux-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-macos-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-windows-x64.zip",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-21.0.3-windows-aarch64.zip",
        },
    },
    __ZULU21__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.52.15-ca-jdk21.0.12-win_x64.zip",
            __ARM__: None,
        },
    },
    __TEMURIN21__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.12_8.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_mac_hotspot_21.0.12_8.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12_8.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12_8.zip",
            __ARM__: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_aarch64_windows_hotspot_21.0.12_8.zip",
        },
    },
    __SAPMACHINE21__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.3/sapmachine-jdk-21.0.3_windows-x64_bin.zip",
            __ARM__: None,
        },
    },

    __LIBERICA21__ : {
        __LINUX__: {
            __X86_64__  : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-linux-amd64.tar.gz",
            __ARM__     : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-linux-aarch64.tar.gz",
       },
        __APPLE__ : {
            __X86_64__:  "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-macos-amd64.tar.gz",
            __ARM__   : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-macos-aarch64.tar.gz",
       },
       __WINDOWS__: {
           __X86_64__: "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-windows-amd64.zip",
           __ARM__   : "https://download.bell-sw.com/java/21.0.5+11/bellsoft-jdk21.0.5+11-windows-aarch64.zip",
      },
    },
    ## JDK - versions 22-26
    __JDK22__: {  # 22 is EOL non-LTS: Oracle pins the final patch instead of a rolling "latest" alias
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/22/archive/jdk-22.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __JDK23__: {  # 23 is EOL non-LTS: pinned to its final patch, same reason as 22 above
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/23/archive/jdk-23.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/23/archive/jdk-23.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/23/archive/jdk-23.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/23/archive/jdk-23.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/23/archive/jdk-23.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __JDK24__: {  # 24 is EOL non-LTS: pinned to its final patch, same reason as 22 above
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/24/archive/jdk-24.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/24/archive/jdk-24.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/24/archive/jdk-24.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/24/archive/jdk-24.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.oracle.com/java/24/archive/jdk-24.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
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
    __GRAALVM23__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.2/graalvm-community-jdk-23.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.2/graalvm-community-jdk-23.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.2/graalvm-community-jdk-23.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.2/graalvm-community-jdk-23.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-23.0.2/graalvm-community-jdk-23.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __GRAALVM24__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
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
    __CORRETTO23__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-23-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-23-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-23-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-23-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-23-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },
    __CORRETTO24__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-24-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-24-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-24-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-24-aarch64-macos-jdk.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-24-x64-windows-jdk.zip",
            __ARM__: None,
        },
    },
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
    __MANDREL22__: {  # Mandrel never ships Intel-macOS builds (no ax entry, any version)
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
    __MANDREL23__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.1.2.0-Final/mandrel-java23-linux-amd64-24.1.2.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.1.2.0-Final/mandrel-java23-linux-aarch64-24.1.2.0-Final.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.1.2.0-Final/mandrel-java23-macos-aarch64-24.1.2.0-Final.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.1.2.0-Final/mandrel-java23-windows-amd64-24.1.2.0-Final.zip",
            __ARM__: None,
        },
    },
    __MANDREL24__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.2.2.0-Final/mandrel-java24-linux-amd64-24.2.2.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.2.2.0-Final/mandrel-java24-linux-aarch64-24.2.2.0-Final.tar.gz",
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.2.2.0-Final/mandrel-java24-macos-aarch64-24.2.2.0-Final.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-24.2.2.0-Final/mandrel-java24-windows-amd64-24.2.2.0-Final.zip",
            __ARM__: None,
        },
    },
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
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-win_x64.zip",
            __ARM__: None,
        },
    },
    __ZULU23__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-win_x64.zip",
            __ARM__: None,
        },
    },
    __ZULU24__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-macosx_aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-win_x64.zip",
            __ARM__: None,
        },
    },
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
    __TEMURIN22__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_x64_linux_hotspot_22.0.2_9.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_aarch64_linux_hotspot_22.0.2_9.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_x64_mac_hotspot_22.0.2_9.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_aarch64_mac_hotspot_22.0.2_9.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_x64_windows_hotspot_22.0.2_9.zip",
            __ARM__: None,
        },
    },
    __TEMURIN23__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_x64_linux_hotspot_23.0.2_7.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_aarch64_linux_hotspot_23.0.2_7.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_x64_mac_hotspot_23.0.2_7.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_aarch64_mac_hotspot_23.0.2_7.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_x64_windows_hotspot_23.0.2_7.zip",
            __ARM__: "https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.2%2B7/OpenJDK23U-jdk_aarch64_windows_hotspot_23.0.2_7.zip",
        },
    },
    __TEMURIN24__: {
        __LINUX__: {
            __X86_64__: "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_x64_linux_hotspot_24.0.2_12.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_aarch64_linux_hotspot_24.0.2_12.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_x64_mac_hotspot_24.0.2_12.tar.gz",
            __ARM__: "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_aarch64_mac_hotspot_24.0.2_12.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.2%2B12/OpenJDK24U-jdk_x64_windows_hotspot_24.0.2_12.zip",
            __ARM__: None,
        },
    },
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
    __SAPMACHINE23__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-23.0.2/sapmachine-jdk-23.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-23.0.2/sapmachine-jdk-23.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-23.0.2/sapmachine-jdk-23.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-23.0.2/sapmachine-jdk-23.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-23.0.2/sapmachine-jdk-23.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
    __SAPMACHINE24__: {
        __LINUX__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-24.0.2/sapmachine-jdk-24.0.2_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-24.0.2/sapmachine-jdk-24.0.2_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-24.0.2/sapmachine-jdk-24.0.2_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-24.0.2/sapmachine-jdk-24.0.2_macos-aarch64_bin.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://github.com/SAP/SapMachine/releases/download/sapmachine-24.0.2/sapmachine-jdk-24.0.2_windows-x64_bin.zip",
            __ARM__: None,
        },
    },
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
    __LIBERICA22__: {
        __LINUX__: {
            __X86_64__: "https://download.bell-sw.com/java/22.0.2+11/bellsoft-jdk22.0.2+11-linux-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/22.0.2+11/bellsoft-jdk22.0.2+11-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.bell-sw.com/java/22.0.2+11/bellsoft-jdk22.0.2+11-macos-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/22.0.2+11/bellsoft-jdk22.0.2+11-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.bell-sw.com/java/22.0.2+11/bellsoft-jdk22.0.2+11-windows-amd64.zip",
            __ARM__: None,
        },
    },
    __LIBERICA23__: {
        __LINUX__: {
            __X86_64__: "https://download.bell-sw.com/java/23.0.2+9/bellsoft-jdk23.0.2+9-linux-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/23.0.2+9/bellsoft-jdk23.0.2+9-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.bell-sw.com/java/23.0.2+9/bellsoft-jdk23.0.2+9-macos-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/23.0.2+9/bellsoft-jdk23.0.2+9-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.bell-sw.com/java/23.0.2+9/bellsoft-jdk23.0.2+9-windows-amd64.zip",
            __ARM__: None,
        },
    },
    __LIBERICA24__: {
        __LINUX__: {
            __X86_64__: "https://download.bell-sw.com/java/24.0.2+12/bellsoft-jdk24.0.2+12-linux-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/24.0.2+12/bellsoft-jdk24.0.2+12-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.bell-sw.com/java/24.0.2+12/bellsoft-jdk24.0.2+12-macos-amd64.tar.gz",
            __ARM__: "https://download.bell-sw.com/java/24.0.2+12/bellsoft-jdk24.0.2+12-macos-aarch64.tar.gz",
        },
        __WINDOWS__: {
            __X86_64__: "https://download.bell-sw.com/java/24.0.2+12/bellsoft-jdk24.0.2+12-windows-amd64.zip",
            __ARM__: None,
        },
    },
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
