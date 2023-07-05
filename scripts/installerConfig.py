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
  __ARM__  = "arm64"
else:
  __ARM__  = "aarch64"
__LINUX__  = "linux"
__APPLE__  = "darwin"

__JDK17__      = "jdk17"
__JDK11__      = "jdk11"
__GRAALVM11__  = "graalvm-jdk-11"
__GRAALVM17__  = "graalvm-jdk-17"
__MANDREL11__  = "mandrel-jdk-11"
__MANDREL17__  = "mandrel-jdk-17"
__CORRETTO11__ = "corretto-jdk-11"
__CORRETTO17__ = "corretto-jdk-17"
__MICROSOFT11__  = "microsoft-jdk-11"
__MICROSOFT17__  = "microsoft-jdk-17"
__ZULU11__     = "zulu-jdk-11"
__ZULU17__     = "zulu-jdk-17"
 
## cmake 
CMAKE = {
    __LINUX__ : {
        __X86_64__ : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.tar.gz",
        __ARM__    : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-aarch64.tar.gz",
    },
    __APPLE__ : {
        __X86_64__ : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",   
        __ARM__    : "https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-macos-universal.tar.gz",
    }
}

## Maven 
MAVEN = {
    __LINUX__ : {
        __X86_64__ : "https://dlcdn.apache.org/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
        __ARM__    : "https://dlcdn.apache.org/maven/maven-3/3.9.3/binaries/apache-maven-3.9.3-bin.tar.gz",
    },
    __APPLE__ : {
        __X86_64__ : None,   
        __ARM__    : None,
    }
}

## JDK
JDK = {
    __JDK11__ : {
        __LINUX__ : {
            __X86_64__ : "https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz",
            __ARM__    : None,
        },
        __APPLE__ : {
            __X86_64__ : "https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz",
            __ARM__    : None,
        }
    },
    __JDK17__ : {
        __LINUX__ : {
            __X86_64__ : "https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz",
            __ARM__    : "https://download.oracle.com/java/17/latest/jdk-17_linux-aarch64_bin.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://download.oracle.com/java/17/latest/jdk-17_macos-x64_bin.tar.gz",
            __ARM__    : "https://download.oracle.com/java/17/latest/jdk-17_macos-aarch64_bin.tar.gz",
        }
    },
    __GRAALVM11__ : {
        __LINUX__ : {
            __X86_64__ : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java11-linux-amd64-22.3.2.tar.gz",
            __ARM__    : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java11-linux-aarch64-22.3.2.tar.gz",
        },
        __APPLE__: {
            __X86_64__ : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java11-darwin-amd64-22.3.2.tar.gz",
            __ARM__    : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java11-darwin-amd64-22.3.2.tar.gz",
        }
    },
    __GRAALVM17__ : {
        __LINUX__ : {
            __X86_64__ : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java17-linux-amd64-22.3.2.tar.gz",
            __ARM__    : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java17-linux-aarch64-22.3.2.tar.gz",
        },
        __APPLE__: {
            __X86_64__ : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java17-darwin-amd64-22.3.2.tar.gz",
            __ARM__    : "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.2/graalvm-ce-java17-darwin-amd64-22.3.2.tar.gz",
        }
    },
    __CORRETTO11__ : {
        __LINUX__ : {
            __X86_64__ : "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz",
            __ARM__    : "https://corretto.aws/downloads/latest/amazon-corretto-11-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://corretto.aws/downloads/latest/amazon-corretto-11-x64-macos-jdk.tar.gz",
            __ARM__    : "https://corretto.aws/downloads/latest/amazon-corretto-11-aarch64-macos-jdk.tar.gz",
        }
    },
    __CORRETTO17__ : {
        __LINUX__ : {
            __X86_64__ : "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz",
            __ARM__    : "https://corretto.aws/downloads/latest/amazon-corretto-17-aarch64-linux-jdk.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-macos-jdk.tar.gz",
            __ARM__    : "https://corretto.aws/downloads/latest/amazon-corretto-17-aarch64-macos-jdk.tar.gz",
        }
    },
    __MANDREL11__ : {
        __LINUX__ : {
            __X86_64__ : None,
            __ARM__    : None,
        },
        __APPLE__ : {
            __X86_64__ : None,
            __ARM__    : None,
        }
    },
    __MANDREL17__ : {
        __LINUX__ : {
            __X86_64__ : "https://github.com/graalvm/mandrel/releases/download/mandrel-22.3.2.0-Final/mandrel-java17-linux-amd64-22.3.2.0-Final.tar.gz",
            __ARM__    : "https://github.com/graalvm/mandrel/releases/download/mandrel-22.3.2.0-Final/mandrel-java17-linux-aarch64-22.3.2.0-Final.tar.gz",
        },
        __APPLE__ : {
            __ARM__    : None,
        }
    },
    __MICROSOFT11__ : {
        __LINUX__ : {
            __X86_64__ : "https://aka.ms/download-jdk/microsoft-jdk-11.0.18-linux-x64.tar.gz",
            __ARM__    : "https://aka.ms/download-jdk/microsoft-jdk-11.0.18-linux-aarch64.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://aka.ms/download-jdk/microsoft-jdk-11.0.18-macOS-x64.tar.gz",
            __ARM__    : "https://aka.ms/download-jdk/microsoft-jdk-11.0.18-macOS-aarch64.tar.gz",
        }
    },
    __MICROSOFT17__ : {
        __LINUX__ : {
            __X86_64__ : "https://aka.ms/download-jdk/microsoft-jdk-17.0.6-linux-x64.tar.gz",
            __ARM__    : "https://aka.ms/download-jdk/microsoft-jdk-17.0.6-linux-aarch64.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://aka.ms/download-jdk/microsoft-jdk-17.0.6-macOS-x64.tar.gz",
            __ARM__    : "https://aka.ms/download-jdk/microsoft-jdk-17.0.6-macOS-aarch64.tar.gz",
        }
    },
    __ZULU11__ : {
        __LINUX__ : {
            __X86_64__ : "https://cdn.azul.com/zulu/bin/zulu11.56.19-ca-jdk11.0.15-linux_x64.tar.gz",
            __ARM__    : "https://cdn.azul.com/zulu-embedded/bin/zulu11.62.17-ca-jdk11.0.18-linux_aarch64.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://cdn.azul.com/zulu/bin/zulu11.56.19-ca-jdk11.0.15-macosx_x64.tar.gz",         
            __ARM__    : "https://cdn.azul.com/zulu/bin/zulu11.56.19-ca-jdk11.0.15-macosx_aarch64.tar.gz",
        }
    },
    __ZULU17__ : {
        __LINUX__ : {
            __X86_64__ : "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-linux_x64.tar.gz",
            __ARM__    : "https://cdn.azul.com/zulu/bin/zulu17.40.19-ca-jdk17.0.6-linux_aarch64.tar.gz",
        },
        __APPLE__ : {
            __X86_64__ : "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-macosx_x64.tar.gz",
            __ARM__    : "https://cdn.azul.com/zulu/bin/zulu17.34.19-ca-jdk17.0.3-macosx_aarch64.tar.gz",
        }
    },
}
