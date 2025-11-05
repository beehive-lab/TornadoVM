@echo off
REM Copyright (c) 2013-2025, APT Group, Department of Computer Science,
REM The University of Manchester.
REM
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM
REM http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

REM ########################################################
REM TornadoVM Dependency Checker and Launcher Wrapper (Windows)
REM ########################################################

setlocal enabledelayedexpansion

REM Check Python3
where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Python3 is not installed
    echo.
    echo TornadoVM requires Python3 to run.
    echo.
    echo To install Python3:
    echo   1. Download from: https://www.python.org/downloads/
    echo   2. Or use winget: winget install Python.Python.3
    echo   3. Or use Chocolatey: choco install python
    echo.
    exit /b 1
)

REM Check JAVA_HOME
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME environment variable is not set
    echo.
    echo TornadoVM requires JAVA_HOME to be set to a JDK 21 installation.
    echo.
    echo To set JAVA_HOME:
    echo   1. Right-click "This PC" ^> Properties ^> Advanced system settings
    echo   2. Click "Environment Variables"
    echo   3. Add JAVA_HOME pointing to your JDK 21 installation
    echo      Example: C:\Program Files\Java\jdk-21
    echo   4. Add %%JAVA_HOME%%\bin to PATH
    echo.
    echo To install JDK 21:
    echo   - Download from: https://adoptium.net/
    echo   - Or use SDKMAN on WSL: sdk install java 21.0.1-graal
    echo.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java executable not found in JAVA_HOME: %JAVA_HOME%\bin\java.exe
    exit /b 1
)

REM Check TORNADO_SDK
if not defined TORNADO_SDK (
    echo [ERROR] TORNADO_SDK environment variable is not set
    echo.
    echo Please set TORNADO_SDK to point to your TornadoVM installation.
    echo.
    echo To set TORNADO_SDK:
    echo   1. Right-click "This PC" ^> Properties ^> Advanced system settings
    echo   2. Click "Environment Variables"
    echo   3. Add TORNADO_SDK pointing to your TornadoVM SDK directory
    echo      Example: C:\tornadovm\sdk
    echo   4. Add %%TORNADO_SDK%%\bin to PATH
    echo.
    exit /b 1
)

if not exist "%TORNADO_SDK%" (
    echo [ERROR] TORNADO_SDK is set but directory does not exist: %TORNADO_SDK%
    exit /b 1
)

REM Check backend dependencies (warnings only, non-critical)
if exist "%TORNADO_SDK%\etc\tornado.backend" (
    findstr "opencl-backend" "%TORNADO_SDK%\etc\tornado.backend" >nul 2>nul
    if !errorlevel! equ 0 (
        REM Check for OpenCL
        where clinfo >nul 2>nul
        if !errorlevel! neq 0 (
            echo [WARNING] OpenCL backend is configured but OpenCL may not be installed
            echo.
            echo For NVIDIA GPUs: Install CUDA Toolkit from https://developer.nvidia.com/cuda-downloads
            echo For Intel GPUs: Install Intel GPU drivers with OpenCL support
            echo For AMD GPUs: Install AMD GPU drivers with OpenCL support
            echo.
        )
    )

    findstr "ptx-backend" "%TORNADO_SDK%\etc\tornado.backend" >nul 2>nul
    if !errorlevel! equ 0 (
        REM Check for NVIDIA
        where nvidia-smi >nul 2>nul
        if !errorlevel! neq 0 (
            echo [WARNING] PTX backend is configured but NVIDIA driver is not detected
            echo.
            echo Install CUDA Toolkit from: https://developer.nvidia.com/cuda-downloads
            echo.
        )
    )

    findstr "spirv-backend" "%TORNADO_SDK%\etc\tornado.backend" >nul 2>nul
    if !errorlevel! equ 0 (
        REM Check for Level Zero (Intel)
        if not exist "%SystemRoot%\System32\ze_loader.dll" (
            echo [WARNING] SPIR-V backend is configured but Level Zero may not be installed
            echo.
            echo For Intel GPUs: Install Intel GPU drivers with Level Zero support
            echo Visit: https://github.com/intel/compute-runtime/releases
            echo.
        )
    )
)

REM Execute the Python launcher
set SCRIPT_DIR=%~dp0
set TORNADO_LAUNCHER=%SCRIPT_DIR%tornado.py

if not exist "%TORNADO_LAUNCHER%" (
    echo [ERROR] TornadoVM launcher not found at: %TORNADO_LAUNCHER%
    exit /b 1
)

REM Launch Python script with all arguments
python "%TORNADO_LAUNCHER%" %*
