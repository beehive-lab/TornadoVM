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

import os
import sys
import argparse
import platform
import tarfile
import config

try:
    import wget
except:
    print("Install the wget Python module: pip install wget")
    exit()


__DIRECTORY_DEPENDENCIES__ = "etc/dependencies"
__VERSION__ = "v0.15.1"

__SUPPORTED_JDKS__ = ["jdk11", "jdk17", "graalvm-jdk-11",  "graalvm-jdk-17",  
                      "mandrel-jdk-11", "mandrel-jdk-17", "windows-jdk-11", 
                      "windows-jdk-17", "corretto-jdk-11", "corretto-jdk-17", 
                      "zulu-jdk-11", "zulu-jdk-17" ]

__SUPPORTED_BACKENDS__ =  [ "opencl", "spirv", "ptx" ]

__X86_64__ = config.__X86_64__
__ARM__    = config.__ARM__ 
__LINUX__  = config.__LINUX__
__APPLE__  = config.__APPLE__

class TornadoInstaller():

    def __init__(self):
        self.workDirName = ""
        self.osPlatform = self.getOSPlatform()
        self.hardware = self.getMachineArc()
        self.env = {}
        self.env["PATH"] = [self.getCurrentDirectory() + "/bin/bin:"]
        self.env["TORNADO_SDK"] = self.getCurrentDirectory() + "/bin/sdk"

    def getOSPlatform(self):
        return platform.system().lower()

    def getMachineArc(self):
        return platform.uname().machine.lower()

    def processFileName(self, url):
        return url.split("/")[-1]

    def setWorkDir(self, jdk):
        self.workDirName = __DIRECTORY_DEPENDENCIES__ + "/TornadoVM-" + jdk
        if not os.path.exists(self.workDirName):
            os.makedirs(self.workDirName)

    def getCurrentDirectory(self):
        return os.getcwd()

    def downloadCMake(self):
        if (self.hardware == __X86_64__ and self.osPlatform == __LINUX__ ):
            url = config.CMAKE[__LINUX__][__X86_64__]
        elif (self.hardware == __ARM__ and self.osPlatform == __LINUX__ ):
            url = config.CMAKE[__LINUX__][__ARM__]
        elif (self.osPlatform == __APPLE__):
            url = config.CMAKE[__APPLE__][__ARM__]
        
        fileName = self.processFileName(url)
        print("Checking dependency: " + fileName)
        fullPath = self.workDirName + "/" + fileName

        if not os.path.exists(fullPath):
            wget.download(url, fullPath)

        ## Uncompress file
        tar = tarfile.open(fullPath, 'r:gz')
        tar.extractall(self.workDirName)
        extractedDirectory = tar.getnames()[0].split("/")[0]
        tar.close()

        currentDirectory = self.getCurrentDirectory()

        extraPath = ""
        if (self.osPlatform == __APPLE__):
            extraPath = "/CMake.app/Contents"

        ## Update env-variables
        self.env["CMAKE_ROOT"] = currentDirectory + "/" + self.workDirName + "/" + extractedDirectory + extraPath
        self.env["PATH"].append(currentDirectory + "/" + self.workDirName + "/" + extractedDirectory + extraPath + "/bin")


    def downloadMaven(self):
        if (self.hardware == __X86_64__ and self.osPlatform == __LINUX__ ):
            url = config.MAVEN[__LINUX__][__X86_64__]
        elif (self.hardware == __ARM__ and self.osPlatform == __LINUX__ ):
            url = config.MAVEN[__LINUX__][__ARM__]
        elif (self.osPlatform == __APPLE__):
            url = config.MAVEN[__APPLE__][__ARM__]
            if (url == None):
                return
            
        fileName = self.processFileName(url)
        print("\nChecking dependency: " + fileName)
        fullPath = self.workDirName + "/" + fileName

        if not os.path.exists(fullPath):
            wget.download(url, fullPath)

        ## Uncompress file
        tar = tarfile.open(fullPath, 'r:gz')
        tar.extractall(self.workDirName)
        extractedDirectory = tar.getnames()[0].split("/")[0]
        tar.close()

        currentDirectory = self.getCurrentDirectory()

        extraPath = ""
        if (self.osPlatform == __APPLE__):
            extraPath = "/Maven.app/Contents"

        ## Update env-variables
        self.env["PATH"].append(currentDirectory + "/" + self.workDirName + "/" + extractedDirectory + extraPath + "/bin")

    def downloadJDK(self, jdk):
        if (self.hardware == __X86_64__ and self.osPlatform == __LINUX__ ):
            url = config.JDK[jdk][__LINUX__][__X86_64__]
        elif (self.hardware == __ARM__ and self.osPlatform == __LINUX__ ):
            url = config.JDK[jdk][__LINUX__][__ARM__]
        elif (self.osPlatform == __APPLE__):
            url = config.JDK[jdk][__APPLE__][__ARM__]
        
        fileName = self.processFileName(url)
        print("\nChecking dependency: " + fileName)
        fullPath = self.workDirName + "/" + fileName

        if not os.path.exists(fullPath):
            wget.download(url, fullPath)

        ## Uncompress file
        tar = tarfile.open(fullPath, 'r:gz')
        extractedDirectory = tar.getnames()[0].split("/")[0]
        try:
            tar.extractall(self.workDirName, numeric_owner=True)
        except:
            pass
        tar.close()

        currentDirectory = self.getCurrentDirectory()
        
        extraPath = ""
        if (self.osPlatform == __APPLE__):
            extraPath = "/Contents/Home"

        ## Update env-variables
        self.env["JAVA_HOME"] = currentDirectory + "/" + self.workDirName + "/" + extractedDirectory + extraPath


    def setEnvironmentVariables(self):        
        ## 1. Path
        paths = self.env["PATH"]
        allPaths = ""
        for p in paths:
            allPaths = allPaths + p + ":"
        allPaths = allPaths + os.environ["PATH"]
        os.environ["PATH"] = allPaths

        ## 2. CMAKE_ROOT
        os.environ["CMAKE_ROOT"] = self.env["CMAKE_ROOT"]

        ## 3. JAVA_HOME
        os.environ["JAVA_HOME"] = self.env["JAVA_HOME"]

        ## 4. TORNADO_SDK
        os.environ["TORNADO_SDK"] = self.env["TORNADO_SDK"]


    def compileTornadoVM(self, makeJDK, backend):
        command = "make " + makeJDK + " " + backend
        os.system(command)

    def createSourceFile(self):
        print(" ------------------------------------------")
        print("        TornadoVM installation done        ")
        print(" ------------------------------------------")
        print("Creating source file ......................")

        paths = self.env["PATH"]
        allPaths = ""
        for p in paths:
            allPaths = allPaths + p + ":"

        fileContent = "export JAVA_HOME=" + self.env["JAVA_HOME"] + "\n"
        fileContent = fileContent + "export PATH=" + allPaths + "$PATH" "\n"
        fileContent = fileContent + "export CMAKE_ROOT=" + self.env["CMAKE_ROOT"] + "\n"
        fileContent = fileContent + "export TORNADO_SDK=" + self.env["TORNADO_SDK"] + "\n"
        f = open("source.sh", "w")
        f.write(fileContent)
        f.close()

        print("........................................[ok]")
        print(" ")
        print(" ")
        print("To run TornadoVM, first run `. source.sh`")


    def install(self, args):
        if (args.jdk == None):
            print("[Error] Define one JDK to install TornadoVM. ")
            sys.exit(0)
        
        if (args.jdk not in __SUPPORTED_JDKS__):
            print("JDK not supported. Please install with one of the JDKs from the supported list")
            for jdk in __SUPPORTED_JDKS__:
                print("\t" + jdk)
            sys.exit(0)

        if (args.backend == None):
            print("[Error] Specify at least one backend { opencl,ptx,spirv } ")
            sys.exit(0)
        
        backend = args.backend.replace(" ", "").lower()
        for b in backend.split(","):
            if (b not in __SUPPORTED_BACKENDS__):
                print(b + " is not a supported backend")
                sys.exit(0)

        backend="BACKEND=" + backend

        makeJDK = "jdk-11-plus"
        if (args.jdk.startswith("graal")):
            makeJDK = "graal-jdk-11-plus"

        self.setWorkDir(args.jdk)
        self.downloadCMake()
        self.downloadMaven()
        self.downloadJDK(args.jdk)
        self.setEnvironmentVariables()
        self.compileTornadoVM(makeJDK, backend)
        self.createSourceFile()


def listSupportedJDKs():
    print("""
    jdk11            : Install TornadoVM with OpenJDK 11 (Adoptium)"
    jdk17            : Install TornadoVM with OpenJDK 17 (Oracle OpenJDK)"
    graal-jdk-11     : Install TornadoVM with GraalVM and JDK 11 (GraalVM 22.2.0)"
    graal-jdk-17     : Install TornadoVM with GraalVM and JDK 17 (GraalVM 22.2.0)"
    corretto-11      : Install TornadoVM with Corretto JDK 11"
    corretto-17      : Install TornadoVM with Corretto JDK 17"
    mandrel-11       : Install TornadoVM with Mandrel 22.2.0 (JDK 11)"
    mandrel-17       : Install TornadoVM with Mandrel 22.2.0 (JDK 17)"
    microsoft-jdk-11 : Install TornadoVM with Microsoft JDK 11"
    microsoft-jdk-17 : Install TornadoVM with Microsoft JDK 17"
    zulu-jdk-11      : Install TornadoVM with Azul Zulu JDK 11"
    zulu-jdk-17      : Install TornadoVM with Azul Zulu JDK 17"

    Usage: 
     $ python scripts/tornadoVMInstaller.py  --jdk <JDK_VERSION> --backend <BACKEND>
    """)

def parseArguments():
    """ Parse command line arguments """
    parser = argparse.ArgumentParser(description="""TornadoVM installer tool. It will install all software dependencies except the GPU/FPGA drivers""")
    parser.add_argument('--version', action="store_true", dest="version", default=False, help="Print version of TornadoVM")
    parser.add_argument('--jdk', action="store", dest="jdk", default=None, help="Select one of the supported JDKs: { jdk11, jdk17, }")
    parser.add_argument('--backend', action="store", dest="backend", default=None, help="Select the backend to install: { opencl, ptx, spirv }")
    parser.add_argument('--listJDKs', action="store_true", dest="listJDKs", default=False, help="List all JDK supported versions")
    args = parser.parse_args()

    if (len(sys.argv) == 1):
        parser.print_help()
        sys.exit()

    return args

if __name__ == "__main__":
    args = parseArguments()    

    if (args.version):
        print(__VERSION__)
        sys.exit(0)
    if (args.listJDKs):
        listSupportedJDKs()
        sys.exit(0)

    installer = TornadoInstaller()
    installer.install(args)
