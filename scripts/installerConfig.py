#!/usr/bin/env python3

# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2023, APT Group, Department of Computer Science,
# School of Engineering, The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

import platform

__X86_64__ = "x86_64"
if (platform.system().lower().startswith("darwin")):
    __ARM__ = "arm64"
else:
    __ARM__ = "aarch64"
__LINUX__ = "linux"
__APPLE__ = "darwin"

__JDK17__ = "jdk17"
__JDK21__ = "jdk21"
__GRAALVM17__ = "graalvm-jdk-17"
__GRAALVM21__ = "graalvm-jdk-21"
__MANDREL17__ = "mandrel-jdk-17"
__MANDREL20__ = "mandrel-jdk-20"
__CORRETTO17__ = "corretto-jdk-17"
__CORRETTO21__ = "corretto-jdk-21"
__MICROSOFT17__ = "microsoft-jdk-17"
__MICROSOFT20__ = "microsoft-jdk-20"
__ZULU17__ = "zulu-jdk-17"
__ZULU21__ = "zulu-jdk-21"

## cmake 
CMAKE = {
    __LINUX__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.tar.gz",
        __ARM__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-aarch64.tar.gz",
    },
    __APPLE__: {
        __X86_64__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
        __ARM__: "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
    }
}

## Maven 
MAVEN = {
    __LINUX__: {
        __X86_64__: "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
        __ARM__:    "https://archive.apache.org/dist/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
    },
    __APPLE__: {
        __X86_64__: None,
        __ARM__: None,
    }
}

## JDK
JDK = {
    __JDK17__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/17/latest/jdk-17_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/17/latest/jdk-17_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/17/latest/jdk-17_macos-aarch64_bin.tar.gz",
        }
    },
    __JDK21__: {
        __LINUX__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://download.oracle.com/java/21/latest/jdk-21_macos-x64_bin.tar.gz",
            __ARM__: "https://download.oracle.com/java/21/latest/jdk-21_macos-aarch64_bin.tar.gz",
        }
    },
    __GRAALVM17__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_macos-aarch64_bin.tar.gz",
        }
    },
    __GRAALVM21__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0_linux-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0_linux-aarch64_bin.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0_macos-x64_bin.tar.gz",
            __ARM__: "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0_macos-aarch64_bin.tar.gz",
        }
    },
    __CORRETTO17__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-17-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-17-aarch64-macos-jdk.tar.gz",
        }
    },
    __CORRETTO21__: {
        __LINUX__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-macos-jdk.tar.gz",
            __ARM__: "https://corretto.aws/downloads/latest/amazon-corretto-21-aarch64-macos-jdk.tar.gz",
        }
    },
    __MANDREL17__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.0.0.0-Final/mandrel-java17-linux-amd64-23.0.0.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.0.0.0-Final/mandrel-java17-linux-aarch64-23.0.0.0-Final.tar.gz",
        },
        __APPLE__: {
            __ARM__: None,
        }
    },
    __MANDREL20__: {
        __LINUX__: {
            __X86_64__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.0.0.0-Final/mandrel-java20-linux-amd64-23.0.0.0-Final.tar.gz",
            __ARM__: "https://github.com/graalvm/mandrel/releases/download/mandrel-23.0.0.0-Final/mandrel-java20-linux-aarch64-23.0.0.0-Final.tar.gz",
        },
        __APPLE__: {
            __ARM__: None,
        }
    },
    __MICROSOFT17__: {
        __LINUX__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-17.0.8.1-linux-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-17.0.8.1-linux-aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://aka.ms/download-jdk/microsoft-jdk-17.0.8.1-macOS-x64.tar.gz",
            __ARM__: "https://aka.ms/download-jdk/microsoft-jdk-17.0.8.1-macOS-aarch64.tar.gz",
        }
    },
    __MICROSOFT20__: {
        __LINUX__: {
            __X86_64__: None,
            __ARM__: None,
        },
        __APPLE__: {
            __X86_64__: None,
            __ARM__: None,
        }
    },
    __ZULU17__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu17.40.19-ca-jdk17.0.6-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-macosx_aarch64.tar.gz",
        }
    },
    __ZULU21__: {
        __LINUX__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-linux_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zzulu21.28.85-ca-jdk21.0.0-linux_aarch64.tar.gz",
        },
        __APPLE__: {
            __X86_64__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-macosx_x64.tar.gz",
            __ARM__: "https://cdn.azul.com/zulu/bin/zulu21.28.85-ca-jdk21.0.0-macosx_aarch64.tar.gz",
        }
    },
}
