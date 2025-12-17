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

import argparse
import os
import platform
import re
import shlex
import subprocess
import sys
import idea_xml_utils as ideaUtils
from pathlib import Path
import shutil
import ctypes
import ctypes.util

# ########################################################
# FLAGS FOR TORNADOVM
# ########################################################
__TORNADOVM_DEBUG__ = " -Dtornado.debug=True "
__TORNADOVM_FULLDEBUG__ = __TORNADOVM_DEBUG__ + "-Dtornado.fullDebug=True -Ddump.taskgraph=True "
__TORNADOVM_THREAD_INFO__ = " -Dtornado.threadInfo=True "
__TORNADOVM_IGV__ = " -Dgraal.Dump=*:5 -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true "
__TORNADOVM__IGV_LOW_TIER = " -Dgraal.Dump=*:1 -Dgraal.PrintGraph=Network -Dgraal.PrintBackendCFG=true -Dtornado.debug.lowtier=True "
__TORNADOVM_PRINT_KERNEL__ = " -Dtornado.printKernel=True "
__TORNADOVM_PRINT_BC__ = " -Dtornado.print.bytecodes=True "
__TORNADOVM_DUMP_PROFILER__ = " -Dtornado.profiler=True -Dtornado.log.profiler=True -Dtornado.profiler.dump.dir="
__TORNADOVM_ENABLE_PROFILER_SILENT__ = " -Dtornado.profiler=True -Dtornado.log.profiler=True "
__TORNADOVM_ENABLE_PROFILER_CONSOLE__ = " -Dtornado.profiler=True "
__TORNADOVM_ENABLE_CONCURRENT__DEVICES__ = " -Dtornado.concurrent.devices=True "
__TORNADOVM_DUMP_BYTECODES_DIR__ = " -Dtornado.print.bytecodes=True -Dtornado.dump.bytecodes.dir="

# ########################################################
# LIST OF TORNADOVM PROVIDERS: Set of Java Classes that
# will be loaded at runtime.
# ########################################################
__TORNADOVM_PROVIDERS__ = """\
-Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph \
-Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime \
-Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado \
-Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor \
-Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel """

# ########################################################
# BACKEND FILES AND MODULES
# ########################################################
__COMMON_EXPORTS__ = "/etc/exportLists/common-exports"
__OPENCL_EXPORTS__ = "/etc/exportLists/opencl-exports"
__PTX_EXPORTS__ = "/etc/exportLists/ptx-exports"
__SPIRV_EXPORTS__ = "/etc/exportLists/spirv-exports"
__TORNADOVM_ADD_MODULES__ = "--add-modules ALL-SYSTEM,tornado.runtime,tornado.annotation,tornado.drivers.common"
__PTX_MODULE__ = "tornado.drivers.ptx"
__OPENCL_MODULE__ = "tornado.drivers.opencl"

# ########################################################
# JAVA FLAGS
# ########################################################
__JAVA_GC__ = "-XX:+UseParallelGC "
__JAVA_BASE_OPTIONS__ = "-server -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI --enable-preview "
__TRUFFLE_BASE_OPTIONS__ = "--jvm --polyglot --vm.XX:+UnlockExperimentalVMOptions --vm.XX:+EnableJVMCI --enable-preview "

# We do not satisfy the Graal compiler assertions because we only support a subset of the Java specification.
# This allows us to have the GraalIR in states which normally would be illegal.
__GRAAL_ENABLE_ASSERTIONS__ = " -ea -da:org.graalvm.compiler... "


# ########################################################
# Windows Dependency Validation Functions
# ########################################################
def validate_tornado_sdk_path(sdk_path):
    """Validate TORNADO_SDK has proper Windows path format with drive letter."""
    if os.name == 'nt':
        # Check if path starts with drive letter (e.g., C:, D:)
        if not re.match(r'^[A-Za-z]:', sdk_path):
            print("[ERROR] TORNADO_SDK path is missing drive letter")
            print()
            print(f"[INFO] Current TORNADO_SDK: {sdk_path}")
            print()
            print("[CAUSE] On Windows, paths must start with a drive letter (C:, D:, etc.)")
            print("        Your path starts with a backslash instead of a drive letter.")
            print()
            print("[FIX] Update your TORNADO_SDK environment variable to include the drive letter:")
            print("      Example: C:\\Users\\YourName\\tornadovm\\sdk")
            print()
            print("      Steps to fix:")
            print("      1. Right-click 'This PC' > Properties > Advanced system settings")
            print("      2. Click 'Environment Variables'")
            print("      3. Edit TORNADO_SDK and add the drive letter (e.g., C:)")
            print()
            sys.exit(1)

def check_dll_loadable(dll_path):
    """Try to load a DLL and return True if successful."""
    if os.name != 'nt':
        return True
    try:
        # Try to load the DLL using ctypes
        ctypes.WinDLL(dll_path)
        return True
    except OSError:
        return False

def get_gpu_info():
    """Get GPU information on Windows. Returns list of GPU names or empty list."""
    if os.name != 'nt':
        return []
    try:
        result = subprocess.run(['wmic', 'path', 'win32_VideoController', 'get', 'name'],
                              capture_output=True, text=True, timeout=5,
                              creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        if result.returncode == 0:
            lines = result.stdout.strip().split('\n')
            # Skip header line and filter empty lines
            gpus = [line.strip() for line in lines[1:] if line.strip()]
            return gpus
    except Exception:
        # Silently fail if wmic is not available or permission denied
        pass
    return []

def check_nvidia_driver():
    """Check if NVIDIA driver is installed."""
    return shutil.which('nvidia-smi') is not None

def validate_opencl_backend(sdk_path):
    """Validate OpenCL backend dependencies on Windows."""
    if os.name != 'nt':
        return True

    opencl_dll = os.path.join(sdk_path, 'lib', 'tornado-opencl.dll')

    if not os.path.exists(opencl_dll):
        print(f"[WARNING] OpenCL backend configured but tornado-opencl.dll not found")
        print(f"[INFO] Expected location: {opencl_dll}")
        print()
        return False

    # Try to load the DLL
    if not check_dll_loadable(opencl_dll):
        print("[ERROR] Cannot load OpenCL JNI library")
        print()
        print(f"[INFO] Library location: {opencl_dll}")
        print()

        # Detect GPU to provide better guidance
        gpus = get_gpu_info()
        if gpus:
            print("[INFO] Detected GPU(s):")
            for gpu in gpus:
                print(f"       - {gpu}")
            print()

        # Check for NVIDIA drivers
        has_nvidia_driver = check_nvidia_driver()

        print("[CAUSE] Missing OpenCL drivers or dependencies")
        print("        The OpenCL backend requires OpenCL 2.1+ drivers for GPUs/CPUs")
        print("        or OpenCL 1.0+ for FPGAs.")
        print()

        # Check system OpenCL.dll (safe read-only operation)
        system_opencl = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'System32', 'OpenCL.dll')
        try:
            if os.path.exists(system_opencl):
                print(f"[INFO] System OpenCL.dll found at: {system_opencl}")
                print("       But tornado-opencl.dll cannot load due to missing dependencies")
            else:
                print("[INFO] System OpenCL.dll not found in Windows\\System32")
                print("       OpenCL drivers are not installed")
        except Exception:
            # Silently skip if we can't check (permission issues)
            pass
        print()

        print("[FIX] Install appropriate GPU drivers based on your hardware:")
        print()
        print("      For NVIDIA GPUs:")
        print("      - GPU driver must match or exceed CUDA Toolkit version")
        print("      - Download NVIDIA drivers (usually pre-installed on Windows)")
        print("      - Install CUDA Toolkit 10.0+ (Windows requires 12.0+)")
        print("      - Download from: https://developer.nvidia.com/cuda-downloads")
        print()
        print("      For Intel GPUs:")
        print("      - Install Intel Graphics drivers with OpenCL support")
        print("      - Download from: https://www.intel.com/content/www/us/en/download-center/home.html")
        print("      - Or install Intel Compute Runtime")
        print("      - Download from: https://github.com/intel/compute-runtime/releases")
        print()
        print("      For AMD GPUs:")
        print("      - Install AMD drivers with OpenCL 2.1+ support")
        print("      - Download from AMD website")
        print()
        print("      After installation:")
        print("      1. Restart your terminal/IDE")
        print("      2. Run tornado --devices to verify installation")
        print()
        sys.exit(1)

    return True

def validate_ptx_backend(sdk_path):
    """Validate PTX backend dependencies on Windows."""
    if os.name != 'nt':
        return True

    ptx_dll = os.path.join(sdk_path, 'lib', 'tornado-ptx.dll')

    if not os.path.exists(ptx_dll):
        print(f"[WARNING] PTX backend configured but tornado-ptx.dll not found")
        print(f"[INFO] Expected location: {ptx_dll}")
        print()
        return False

    # Try to load the DLL
    if not check_dll_loadable(ptx_dll):
        print("[ERROR] Cannot load PTX JNI library")
        print()
        print(f"[INFO] Library location: {ptx_dll}")
        print()

        # Detect GPU to provide better guidance
        gpus = get_gpu_info()
        if gpus:
            print("[INFO] Detected GPU(s):")
            for gpu in gpus:
                print(f"       - {gpu}")
                if "NVIDIA" not in gpu.upper():
                    print("         (This is not an NVIDIA GPU - PTX requires NVIDIA)")
            print()

        # Check for NVIDIA drivers
        has_nvidia_driver = check_nvidia_driver()

        print("[CAUSE] Missing NVIDIA CUDA Toolkit or drivers")
        print("        The PTX backend requires:")
        print("        - NVIDIA GPU")
        print("        - NVIDIA drivers (usually pre-installed on Windows)")
        print("        - CUDA Toolkit 10.0+ (Windows requires 12.0+)")
        print("        - GPU driver must match or exceed CUDA Toolkit version")
        print()

        if not has_nvidia_driver:
            print("[FIX] Install NVIDIA CUDA Toolkit and drivers")
            print("      Download from: https://developer.nvidia.com/cuda-downloads")
            print()
            print("      Installation steps:")
            print("      1. Verify you have an NVIDIA GPU (check detected GPUs above)")
            print("      2. Download CUDA Toolkit 12.0+ for Windows")
            print("      3. Run the installer (includes NVIDIA drivers)")
            print("      4. Restart your system")
            print("      5. Verify installation: nvidia-smi")
            print("      6. Run tornado --devices again")
        else:
            print("[INFO] NVIDIA drivers detected (nvidia-smi available)")
            print()
            print("[FIX] Reinstall or update NVIDIA CUDA Toolkit")
            print("      The tornado-ptx.dll requires complete CUDA Toolkit installation")
            print()
            print("      1. Download CUDA Toolkit 12.0+ for Windows")
            print("         From: https://developer.nvidia.com/cuda-downloads")
            print("      2. Run the installer and select 'Custom' installation")
            print("      3. Ensure all CUDA components are selected")
            print("      4. Restart your system")
            print("      5. Verify with: nvidia-smi")
            print("      6. Run tornado --devices again")

        print()
        print("[NOTE] PTX backend is NVIDIA-specific")
        print("       For non-NVIDIA GPUs, use OpenCL or SPIR-V backends instead")
        print()
        sys.exit(1)

    return True

def validate_spirv_backend(sdk_path):
    """Validate SPIR-V backend dependencies on Windows."""
    if os.name != 'nt':
        return True

    # SPIR-V can run through OpenCL or Level Zero runtimes
    opencl_dll = os.path.join(sdk_path, 'lib', 'tornado-opencl.dll')
    levelzero_dll = os.path.join(sdk_path, 'lib', 'tornado-levelzero.dll')

    has_opencl = os.path.exists(opencl_dll)
    has_levelzero = os.path.exists(levelzero_dll)

    if not has_opencl and not has_levelzero:
        print(f"[WARNING] SPIR-V backend configured but no compatible runtime found")
        print(f"[INFO] SPIR-V requires either:")
        print(f"       - tornado-opencl.dll at: {opencl_dll}")
        print(f"       - tornado-levelzero.dll at: {levelzero_dll}")
        print()
        return False

    # Try to load at least one of the DLLs
    opencl_loadable = has_opencl and check_dll_loadable(opencl_dll)
    levelzero_loadable = has_levelzero and check_dll_loadable(levelzero_dll)

    if not opencl_loadable and not levelzero_loadable:
        print("[ERROR] Cannot load SPIR-V runtime libraries")
        print()
        if has_opencl:
            print(f"[INFO] Found but failed to load: {opencl_dll}")
        if has_levelzero:
            print(f"[INFO] Found but failed to load: {levelzero_dll}")
        print()

        # Detect GPU to provide better guidance
        gpus = get_gpu_info()
        if gpus:
            print("[INFO] Detected GPU(s):")
            for gpu in gpus:
                print(f"       - {gpu}")
            print()

        print("[CAUSE] Missing Level Zero loader or Intel Compute Runtime")
        print("        The SPIR-V backend requires:")
        print("        - Intel Level Zero 1.2+ (recommended for Intel GPUs)")
        print("        - Intel Compute Runtime (for OpenCL support)")
        print("        - Supports Intel HD Graphics (integrated) and Intel ARC GPUs")
        print()

        # Check if ze_loader.dll exists in System32
        system_root = os.environ.get('SystemRoot', 'C:\\Windows')
        ze_loader = os.path.join(system_root, 'System32', 'ze_loader.dll')

        try:
            if os.path.exists(ze_loader):
                print(f"[INFO] Level Zero loader found at: {ze_loader}")
                print("       But additional dependencies may be missing")
            else:
                print("[INFO] Level Zero loader (ze_loader.dll) not found in System32")
                print("       Level Zero runtime is not installed")
        except Exception:
            pass
        print()

        # Check system OpenCL.dll
        system_opencl = os.path.join(system_root, 'System32', 'OpenCL.dll')
        try:
            if os.path.exists(system_opencl):
                print(f"[INFO] System OpenCL.dll found at: {system_opencl}")
            else:
                print("[INFO] System OpenCL.dll not found")
                print("       Intel Compute Runtime is likely not installed")
        except Exception:
            pass
        print()

        print("[FIX] Install Intel GPU drivers and runtime:")
        print()
        print("      Option 1: Install Intel Graphics drivers (recommended)")
        print("      1. Download Intel Graphics drivers from:")
        print("         https://www.intel.com/content/www/us/en/download-center/home.html")
        print("      2. Run the installer")
        print("      3. Restart your system")
        print("      4. Run tornado --devices again")
        print()
        print("      Option 2: Install Intel Compute Runtime")
        print("      1. Download from: https://github.com/intel/compute-runtime/releases")
        print("      2. Install both Level Zero (1.2+) and OpenCL packages")
        print("      3. Restart your system")
        print("      4. Run tornado --devices again")
        print()
        print("[NOTE] SPIR-V backend works best with Intel GPUs")
        print("       For NVIDIA GPUs, use PTX backend instead")
        print("       For AMD GPUs, use OpenCL backend instead")
        print()
        sys.exit(1)

    return True

def validate_windows_dependencies(sdk_path):
    """Run all Windows-specific dependency checks."""
    if os.name != 'nt':
        return

    # Validate TORNADO_SDK path format
    validate_tornado_sdk_path(sdk_path)

    # Check if tornado.backend file exists and validate backends
    backend_file = os.path.join(sdk_path, 'etc', 'tornado.backend')
    if os.path.exists(backend_file):
        try:
            with open(backend_file, 'r', encoding='utf-8') as f:
                content = f.read()

                # Validate each configured backend
                if 'opencl-backend' in content:
                    validate_opencl_backend(sdk_path)

                if 'ptx-backend' in content:
                    validate_ptx_backend(sdk_path)

                if 'spirv-backend' in content:
                    validate_spirv_backend(sdk_path)

        except (OSError, PermissionError, IOError):
            # Silently skip if we can't read the backend file
            # This avoids errors due to permission issues
            pass
        except Exception:
            # Catch any other unexpected errors
            pass

# ########################################################
# TornadoVM Runner Tool
# ########################################################
class TornadoVMRunnerTool():

    def __init__(self):
        # Prioritize TORNADOVM_HOME (used by SDKMAN) over TORNADO_SDK
        # This ensures that when SDKMAN switches versions, we use the new version
        if "TORNADOVM_HOME" in os.environ:
            self.sdk = os.environ["TORNADOVM_HOME"]
            print(f"[INFO] Using TORNADOVM_HOME as TORNADO_SDK: {self.sdk}")
        elif "TORNADO_SDK" in os.environ:
            self.sdk = os.environ["TORNADO_SDK"]
        else:
            print("Please ensure the TORNADO_SDK or TORNADOVM_HOME environment variable is set correctly")
            sys.exit(0)

        # Validate Windows-specific dependencies (path format, DLL dependencies, etc.)
        validate_windows_dependencies(self.sdk)

        # Automatically expand tornado-argfile.template if tornado-argfile doesn't exist
        self.ensureArgfileExists()

        try:
            self.java_home = os.environ["JAVA_HOME"]
            # Strip any surrounding quotes that may have been included in the environment variable
            self.java_home = self.java_home.strip('"').strip("'")
            if (platform.platform().startswith("MING")):
                self.java_home = self.java_home.replace("\\", "/")
        except:
            print("Please ensure the JAVA_HOME environment variable is set correctly")
            sys.exit(0)

        env_vars = {
            "GRAALPY_HOME": "graalpy",
            "GRAALJS_HOME": "js",
            "GRAALNODEJS_HOME": "node",
            "TRUFFLERUBY_HOME": "truffleruby"
        }

        self.setTruffleVars(env_vars)

        self.commands = {
            "java": os.path.join(self.java_home, "bin", "java"),
            "python": self.graalpy,
            "js": self.js,
            "node": self.node,
            "ruby": self.truffleruby
        }

        self.isTruffleCommand = args.truffle_language != None

        if (self.isTruffleCommand):
            if (args.truffle_language in self.commands) and (self.commands[args.truffle_language] != None):
                self.cmd = self.commands[args.truffle_language]
            else:
                print(
                    "Support for " + args.truffle_language + " not provided yet. Please run --truffle with one of the following options: python|ruby|js|node, and ensure that the (GRAALPY_HOME, TRUFFLERUBY_HOME, GRAALJS_HOME, GRAALNODEJS_HOME) environment variables are set correctly.")
                sys.exit(0)
        else:
            self.cmd = self.commands["java"]

        self.java_version, self.isGraalVM = self.getJavaVersion()
        self.checkCompatibilityWithTornadoVM()
        self.platform = sys.platform
        self.listOfBackends = self.getInstalledBackends(False)

        # Check OpenCL drivers on Windows if OpenCL backend is installed
        if (os.name == 'nt' and 'opencl-backend' in self.listOfBackends):
            self.checkOpenCLDriversWindows()

        # Check Visual C++ Runtime on Windows
        if (os.name == 'nt'):
            self.checkVCRuntimeWindows()

        # Check C++ standard library compatibility on Linux
        if (self.platform.startswith('linux')):
            self.checkLinuxLibstdcxxCompatibility()

        # Check macOS compatibility
        if (self.platform == 'darwin'):
            self.checkMacOSCompatibility()

    def setTruffleVars(self, env_vars):
        for var, attr in env_vars.items():
            if var in os.environ:
                setattr(self, attr, os.environ[var] + f"/bin/{attr} ")
            else:
                setattr(self, attr, None)

    def getJavaVersion(self):
        try:
            if os.name == 'nt':
                # Use list format to avoid issues with paths containing spaces
                versionCommand = subprocess.Popen([self.commands["java"], "-version"],
                                                  stdout=subprocess.PIPE,
                                                  stderr=subprocess.PIPE)
            else:
                versionCommand = subprocess.Popen(shlex.split(self.commands["java"] + " -version"), stdout=subprocess.PIPE,
                                                  stderr=subprocess.PIPE)
            stdout, stderr = versionCommand.communicate()
        except FileNotFoundError:
            print("[ERROR] Cannot find Java.")
            print(f"[ERROR] Please ensure JAVA_HOME is set correctly and that Java is accessible.")
            print(f"[ERROR] Current JAVA_HOME: {self.java_home}")
            print(f"[ERROR] Looking for Java at: {self.commands['java']}")
            if os.name == 'nt':
                print("[ERROR] On Windows, ensure %JAVA_HOME%\\bin is in your PATH.")
            sys.exit(1)

        # Try to match version format: version "21.x.x" or version "1.8.x"
        matchJVMVersion = re.search(r'version\s+"?(\d+)(?:\.(\d+))?', str(stderr))
        matchGraal = re.search(r"GraalVM", str(stderr))
        graalEnabled = False
        if (matchGraal != None):
            graalEnabled = True

        if (matchJVMVersion != None):
            major_version = int(matchJVMVersion.group(1))
            # For older Java versions (1.8, 1.11), the real version is in the second group
            if major_version == 1 and matchJVMVersion.group(2):
                version = int(matchJVMVersion.group(2))
            else:
                version = major_version
            return version, graalEnabled
        else:
            print("[ERROR] JDK Version not found")
            print(f"[DEBUG] Java version output was: {stderr.decode('utf-8', errors='ignore')}")
            sys.exit(0)

    def checkCompatibilityWithTornadoVM(self):
        if (self.java_version != 21):
            print("TornadoVM supports only JDK version 21")
            sys.exit(0)

    def checkOpenCLDriversWindows(self):
        """Check if OpenCL drivers are installed on Windows"""
        openCLInstalled = False

        # Check for OpenCL.dll in System32
        system32_opencl = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'System32', 'OpenCL.dll')
        syswow64_opencl = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'SysWOW64', 'OpenCL.dll')

        if os.path.exists(system32_opencl) or os.path.exists(syswow64_opencl):
            openCLInstalled = True

        # Also check registry for OpenCL vendors
        if not openCLInstalled:
            try:
                result = subprocess.run(
                    ['powershell', '-Command',
                     "Get-ItemProperty -Path 'HKLM:\\SOFTWARE\\Khronos\\OpenCL\\Vendors' -ErrorAction SilentlyContinue"],
                    capture_output=True,
                    text=True,
                    timeout=5
                )
                if result.returncode == 0 and result.stdout.strip():
                    openCLInstalled = True
            except:
                pass

        if not openCLInstalled:
            print("\n" + "="*80)
            print("WARNING: OpenCL drivers not detected on your Windows system!")
            print("="*80)
            self.printOpenCLGuidanceWindows()

    def getWindowsGPUInfo(self):
        """Get GPU information on Windows"""
        try:
            result = subprocess.run(
                ['powershell', '-Command',
                 "Get-WmiObject Win32_VideoController | Select-Object Name, DriverVersion | Format-List"],
                capture_output=True,
                text=True,
                timeout=5
            )
            if result.returncode == 0:
                return result.stdout.strip()
        except:
            pass
        return None

    def printOpenCLGuidanceWindows(self):
        """Print guidance for installing OpenCL drivers on Windows"""
        print("\nTornadoVM requires OpenCL drivers to execute on GPUs.")
        print("\nCHECK YOUR GPU:")

        gpuInfo = self.getWindowsGPUInfo()
        if gpuInfo:
            print("-" * 80)
            print(gpuInfo)
            print("-" * 80)

        print("\nHOW TO INSTALL OPENCL DRIVERS:\n")

        print("For Intel GPUs:")
        print("  1. Intel Graphics Driver (may include OpenCL):")
        print("     https://www.intel.com/content/www/us/en/download-center/home.html")
        print("\n  2. Intel OpenCL Runtime (Recommended):")
        print("     https://www.intel.com/content/www/us/en/developer/articles/tool/opencl-drivers.html")
        print("\n  3. Intel oneAPI Base Toolkit (For Development):")
        print("     https://www.intel.com/content/www/us/en/developer/tools/oneapi/base-toolkit-download.html")

        print("\nFor NVIDIA GPUs:")
        print("  - Download latest NVIDIA drivers (includes OpenCL):")
        print("    https://www.nvidia.com/Download/index.aspx")

        print("\nFor AMD GPUs:")
        print("  - Download AMD Adrenalin drivers (includes OpenCL):")
        print("    https://www.amd.com/en/support")

        print("\nVERIFY INSTALLATION:")
        print("  After installing drivers, check for OpenCL.dll:")
        print("    dir C:\\Windows\\System32\\OpenCL.dll")

        print("\n" + "="*80)
        print("TornadoVM will continue, but OpenCL execution may fail without drivers.")
        print("="*80 + "\n")

    def checkVCRuntimeWindows(self):
        """Check if required Visual C++ Runtime is installed on Windows"""
        vcRuntimeOK = False

        # Method 1: Check for MSVCR140.dll (VC++ 2015 - required by tornado-opencl.dll)
        system32_msvcr = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'System32', 'MSVCR140.dll')
        syswow64_msvcr = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'SysWOW64', 'MSVCR140.dll')

        if os.path.exists(system32_msvcr) or os.path.exists(syswow64_msvcr):
            vcRuntimeOK = True

        # Method 2: Check registry for VC++ 2015 Redistributable
        if not vcRuntimeOK:
            try:
                result = subprocess.run(
                    ['reg', 'query', 'HKLM\\SOFTWARE\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x64', '/v', 'Version'],
                    capture_output=True,
                    text=True,
                    timeout=5
                )
                if result.returncode == 0:
                    vcRuntimeOK = True
            except:
                pass

        # If MSVCR140.dll not found, check for newer runtime (VC++ 2017+)
        # This helps determine which runtime is installed
        hasModernRuntime = False
        vcruntime140 = os.path.join(os.environ.get('SystemRoot', 'C:\\Windows'), 'System32', 'VCRUNTIME140.dll')
        if os.path.exists(vcruntime140):
            hasModernRuntime = True

        if not vcRuntimeOK:
            print("\n" + "="*80)
            print("WARNING: Visual C++ 2015 Runtime (MSVCR140.dll) not detected!")
            print("="*80)
            self.printVCRuntimeGuidanceWindows(hasModernRuntime)

    def printVCRuntimeGuidanceWindows(self, hasModernRuntime):
        """Print guidance for installing Visual C++ Runtime on Windows"""
        print("\nTornadoVM native libraries (tornado-opencl.dll) require Visual C++ 2015 Runtime.")

        if hasModernRuntime:
            print("\nYou have Visual C++ 2017+ Runtime installed, but TornadoVM was built with VC++ 2015.")
            print("The older MSVCR140.dll is required for compatibility.")

        print("\nSYMPTOMS:")
        print("  - 'tornado --devices' fails with ExceptionInInitializerError")
        print("  - Error 126 when loading tornado-opencl.dll")
        print("  - UnsatisfiedLinkError for native libraries")

        print("\nSOLUTION:")
        print("  Install Visual C++ 2015-2022 Redistributable (x64):")
        print("\n  Download:")
        print("    https://aka.ms/vs/17/release/vc_redist.x64.exe")
        print("\n  Or search Microsoft for:")
        print("    'Visual C++ Redistributable for Visual Studio 2015-2022'")

        print("\nAFTER INSTALLATION:")
        print("  1. Verify MSVCR140.dll exists:")
        print("     dir C:\\Windows\\System32\\MSVCR140.dll")
        print("\n  2. Test TornadoVM:")
        print("     %TORNADO_SDK%\\bin\\tornado --devices")

        print("\nALTERNATIVE SOLUTION (Advanced):")
        print("  Build TornadoVM from source with Visual Studio 2017+ to use modern runtime.")
        print("  Repository: https://github.com/beehive-lab/TornadoVM")

        print("\n" + "="*80)
        print("TornadoVM will continue, but native library loading may fail without this runtime.")
        print("="*80 + "\n")

    def checkLinuxLibstdcxxCompatibility(self):
        """Check if the system has compatible libstdc++ version for TornadoVM native libraries"""
        try:
            # Check if OpenCL backend is installed
            opencl_lib = os.path.join(self.sdk, 'lib', 'libtornado-opencl.so')
            spirv_lib = os.path.join(self.sdk, 'lib', 'libtornado-levelzero.so')
            ptx_lib = os.path.join(self.sdk, 'lib', 'libtornado-ptx.so')

            # Find at least one native library to check
            lib_to_check = None
            if os.path.exists(opencl_lib):
                lib_to_check = opencl_lib
            elif os.path.exists(spirv_lib):
                lib_to_check = spirv_lib
            elif os.path.exists(ptx_lib):
                lib_to_check = ptx_lib

            if not lib_to_check:
                # No native libraries found, skip check
                return

            # Use ldd to check dependencies
            result = subprocess.run(['ldd', lib_to_check],
                                  capture_output=True,
                                  text=True,
                                  timeout=5)

            # Check both stdout and stderr (ldd outputs errors to stderr)
            output = result.stdout + result.stderr

            # Check for "not found" or GLIBCXX version errors
            if 'not found' in output or 'GLIBCXX' in output:
                # Try to get more specific info using strings
                strings_result = subprocess.run(['strings', lib_to_check],
                                               capture_output=True,
                                               text=True,
                                               timeout=5)

                if strings_result.returncode == 0:
                    # Extract GLIBCXX versions from symbol names (e.g., "symbol@GLIBCXX_3.4.32")
                    glibcxx_versions = []
                    for line in strings_result.stdout.split('\n'):
                        if 'GLIBCXX_' in line:
                            # Extract version after @ or at start of line
                            if '@GLIBCXX_' in line:
                                version = line.split('@GLIBCXX_')[1].split()[0]
                                glibcxx_versions.append('GLIBCXX_' + version)
                            elif line.startswith('GLIBCXX_'):
                                glibcxx_versions.append(line.strip())

                    if glibcxx_versions:
                        # Filter to only numeric versions (e.g., GLIBCXX_3.4.32)
                        # Exclude non-version strings like GLIBCXX_DEBUG_MESSAGE_LENGTH
                        numeric_versions = [v for v in glibcxx_versions
                                          if v.replace('GLIBCXX_', '').replace('.', '').isdigit()]

                        if not numeric_versions:
                            return

                        # Sort by version number properly (not alphabetically)
                        def version_key(v):
                            parts = v.replace('GLIBCXX_', '').split('.')
                            return tuple(int(p) for p in parts)

                        max_required = sorted(numeric_versions, key=version_key)[-1]

                        # Check system libstdc++
                        system_libstdcxx = '/usr/lib/x86_64-linux-gnu/libstdc++.so.6'
                        if not os.path.exists(system_libstdcxx):
                            # Try alternative locations
                            system_libstdcxx = '/lib/x86_64-linux-gnu/libstdc++.so.6'

                        if os.path.exists(system_libstdcxx):
                            system_result = subprocess.run(['strings', system_libstdcxx],
                                                         capture_output=True,
                                                         text=True,
                                                         timeout=5)

                            if system_result.returncode == 0:
                                # Extract GLIBCXX versions from system library
                                system_versions = []
                                for line in system_result.stdout.split('\n'):
                                    if 'GLIBCXX_' in line:
                                        if '@GLIBCXX_' in line:
                                            version = line.split('@GLIBCXX_')[1].split()[0]
                                            system_versions.append('GLIBCXX_' + version)
                                        elif line.startswith('GLIBCXX_'):
                                            system_versions.append(line.strip())

                                if system_versions:
                                    # Filter to only numeric versions
                                    numeric_system_versions = [v for v in system_versions
                                                              if v.replace('GLIBCXX_', '').replace('.', '').isdigit()]

                                    if not numeric_system_versions:
                                        return

                                    # Get max available version using proper version comparison
                                    max_available = sorted(numeric_system_versions, key=version_key)[-1]

                                    # Compare versions using tuple comparison
                                    if version_key(max_required) > version_key(max_available):
                                        self.printLibstdcxxCompatibilityWarning(
                                            lib_to_check, max_required, max_available)
        except Exception:
            # Silently fail - this is a best-effort check
            pass

    def printLibstdcxxCompatibilityWarning(self, library, required_version, available_version):
        """Print warning about libstdc++ incompatibility"""
        print("\n" + "="*80)
        print("WARNING: C++ Standard Library Compatibility Issue Detected!")
        print("="*80)
        print(f"\nTornadoVM native library: {os.path.basename(library)}")
        print(f"Required version: {required_version}")
        print(f"Available version: {available_version}")
        print("\nCAUSE:")
        print("  The TornadoVM SDK was built on a system with a newer GCC version")
        print("  than what is available on this system.")
        print("\nSYMPTOMS:")
        print("  - 'tornado --devices' will fail with ExceptionInInitializerError")
        print("  - Error: 'GLIBCXX_X.X.XX not found'")
        print("  - UnsatisfiedLinkError for native libraries")

        # Detect distribution
        distro_info = self.getLinuxDistribution()

        print("\nSOLUTION:")
        print("\nUpgrade GCC/libstdc++ on this system")

        if 'ubuntu' in distro_info.lower() or 'debian' in distro_info.lower():
            print("  Ubuntu/Debian:")
            print("    sudo add-apt-repository ppa:ubuntu-toolchain-r/test")
            print("    sudo apt update")
            print("    sudo apt install gcc-13 g++-13 libstdc++6")
        elif 'fedora' in distro_info.lower() or 'rhel' in distro_info.lower() or 'centos' in distro_info.lower():
            print("  Fedora/RHEL/CentOS:")
            print("    sudo dnf install gcc-toolset-13")
            print("    scl enable gcc-toolset-13 bash")
        elif 'arch' in distro_info.lower():
            print("  Arch Linux:")
            print("    sudo pacman -Syu gcc")
        else:
            print("  Install the latest GCC version for your distribution")

        print("\nSYSTEM REQUIREMENTS:")
        print("  This SDK requires:")
        if 'GLIBCXX_3.4.32' in required_version:
            print("    - GCC 13+ / Ubuntu 24.04+ / Fedora 38+")
        elif 'GLIBCXX_3.4.30' in required_version:
            print("    - GCC 11+ / Ubuntu 22.04+ / Fedora 36+")
        elif 'GLIBCXX_3.4.29' in required_version:
            print("    - GCC 10+ / Ubuntu 20.04+ / Fedora 33+")

        print("\n" + "="*80)
        print("TornadoVM cannot continue with incompatible libstdc++ version.")
        print("Please follow the recommended solution to resolve this issue.")
        print("="*80 + "\n")
        sys.exit(1)

    def getLinuxDistribution(self):
        """Get Linux distribution information"""
        try:
            if os.path.exists('/etc/os-release'):
                with open('/etc/os-release', 'r') as f:
                    for line in f:
                        if line.startswith('ID='):
                            return line.split('=')[1].strip().strip('"')
        except:
            pass
        return "unknown"

    def checkMacOSCompatibility(self):
        """Check macOS version compatibility for TornadoVM native libraries"""
        try:
            # Get macOS version
            macos_version = platform.mac_ver()[0]
            if not macos_version:
                return

            major_version = int(macos_version.split('.')[0])

            # Check native libraries for deployment target
            opencl_lib = os.path.join(self.sdk, 'lib', 'libtornado-opencl.dylib')
            spirv_lib = os.path.join(self.sdk, 'lib', 'libtornado-levelzero.dylib')
            ptx_lib = os.path.join(self.sdk, 'lib', 'libtornado-ptx.dylib')

            lib_to_check = None
            if os.path.exists(opencl_lib):
                lib_to_check = opencl_lib
            elif os.path.exists(spirv_lib):
                lib_to_check = spirv_lib
            elif os.path.exists(ptx_lib):
                lib_to_check = ptx_lib

            if not lib_to_check:
                return

            # Use otool to check deployment target
            result = subprocess.run(['otool', '-l', lib_to_check],
                                  capture_output=True,
                                  text=True,
                                  timeout=5)

            if result.returncode == 0:
                output = result.stdout

                # Parse deployment target from otool output
                # Look for LC_VERSION_MIN_MACOSX or LC_BUILD_VERSION
                min_version = None
                for line in output.split('\n'):
                    if 'version' in line.lower() and any(v in line for v in ['10.', '11.', '12.', '13.', '14.', '15.']):
                        # Try to extract version number
                        import re
                        version_match = re.search(r'(\d+\.\d+)', line)
                        if version_match:
                            min_version = version_match.group(1)
                            break

                if min_version:
                    min_major = int(min_version.split('.')[0])

                    if major_version < min_major:
                        self.printMacOSCompatibilityWarning(lib_to_check, min_version, macos_version)
        except Exception:
            # Silently fail - this is a best-effort check
            pass

    def printMacOSCompatibilityWarning(self, library, required_version, current_version):
        """Print warning about macOS version incompatibility"""
        print("\n" + "="*80)
        print("WARNING: macOS Version Compatibility Issue Detected!")
        print("="*80)
        print(f"\nTornadoVM native library: {os.path.basename(library)}")
        print(f"Required minimum macOS: {required_version}")
        print(f"Current macOS version: {current_version}")
        print("\nCAUSE:")
        print("  The TornadoVM SDK was built on a newer version of macOS")
        print("  than what is currently running on this system.")
        print("\nSYMPTOMS:")
        print("  - 'tornado --devices' may fail to load native libraries")
        print("  - dyld errors about incompatible library versions")
        print("  - UnsatisfiedLinkError for native libraries")
        print("\nSOLUTION:")
        print("\nUpgrade macOS (if possible)")
        print(f"  Upgrade to macOS {required_version} or later")

        print("\nRECOMMENDED DEPLOYMENT TARGETS:")
        print("  - macOS 11.0 (Big Sur): Maximum compatibility with Apple Silicon and Intel")
        print("  - macOS 12.0 (Monterey): Modern features, good compatibility")
        print("  - macOS 13.0 (Ventura): Latest features, newer systems only")

        print("\n" + "="*80)
        print("TornadoVM cannot continue with incompatible macOS version.")
        print("Please follow the recommended solution to resolve this issue.")
        print("="*80 + "\n")
        sys.exit(1)

    def printNVMLGuidanceWindows(self):
        """Print guidance for nvml.dll dependency issue"""
        print("\nThe tornado-opencl.dll has a dependency on nvml.dll (NVIDIA Management Library).")
        print("This is required even if you don't have an NVIDIA GPU.")

        print("\nSYMPTOMS:")
        print("  - 'tornado --devices' fails with ExceptionInInitializerError")
        print("  - Error 126 when loading tornado-opencl.dll")
        print("  - UnsatisfiedLinkError for native libraries")

        print("\nWHY IS THIS NEEDED?")
        print("  The Windows OpenCL backend was compiled with a hard dependency on NVML.")
        print("  This should be an optional dependency but currently it's required.")

        print("\nSOLUTION 1: Install NVIDIA CUDA Toolkit (Includes nvml.dll)")
        print("  Even without an NVIDIA GPU, the DLL can be present.")
        print("\n  Download CUDA Toolkit:")
        print("    https://developer.nvidia.com/cuda-downloads")
        print("\n  After installation, nvml.dll will be at:")
        print("    C:\\Program Files\\NVIDIA Corporation\\NVSMI\\nvml.dll")

        print("\nSOLUTION 2: Copy nvml.dll to System32")
        print("  If you can obtain nvml.dll from another source:")
        print("    copy nvml.dll C:\\Windows\\System32\\")

        print("\nSOLUTION 3: Build TornadoVM without NVML dependency (Recommended)")
        print("  Clone and build from source without CUDA in the build environment.")
        print("  Repository: https://github.com/beehive-lab/TornadoVM")

        print("\nREPORT THIS ISSUE:")
        print("  This is a packaging bug. NVML should not be a hard dependency.")
        print("  Please report: https://github.com/beehive-lab/TornadoVM/issues")
        print("  Title: 'Windows OpenCL backend has hard dependency on nvml.dll'")

        print("\n" + "="*80)
        print("TornadoVM will continue, but OpenCL backend loading will fail without nvml.dll.")
        print("="*80 + "\n")

    def printRelease(self):
        f = open(self.sdk + "/etc/tornado.release")
        releaseVersion = f.read()
        print(releaseVersion)

    def getInstalledBackends(self, verbose=False):
        if (verbose):
            print("Backends installed: ")
        tornadoBackendFilePath = self.sdk + "/etc/tornado.backend"
        listBackends = []
        with open(tornadoBackendFilePath, 'r') as tornadoBackendFile:
            lines = tornadoBackendFile.read().splitlines()
            for line in lines:
                if "tornado.backends" in line:
                    backends = line.split("=")[1]
                    backends = backends.split(",")
                    listBackends = backends
                    if (verbose):
                        for b in backends:
                            b = b.replace("-backend", "")
                            print("\t - " + b)
        return listBackends

    def printVersion(self):
        self.printRelease()
        self.getInstalledBackends(True)

    def buildTornadoVMOptions(self, args):
        tornadoFlags = ""
        if (args.debug):
            tornadoFlags = tornadoFlags + __TORNADOVM_DEBUG__

        if (args.fullDebug):
            # Full debug also enables the light debugging option
            tornadoFlags = tornadoFlags + __TORNADOVM_FULLDEBUG__

        if (args.threadInfo):
            tornadoFlags = tornadoFlags + __TORNADOVM_THREAD_INFO__

        if (args.printKernel):
            tornadoFlags = tornadoFlags + __TORNADOVM_PRINT_KERNEL__

        if (args.igv):
            tornadoFlags = tornadoFlags + __TORNADOVM_IGV__

        if (args.igvLowTier):
            tornadoFlags = tornadoFlags + __TORNADOVM__IGV_LOW_TIER

        if (args.printBytecodes):
            tornadoFlags = tornadoFlags + __TORNADOVM_PRINT_BC__

        if (args.dump_bytecodes_dir != None):
            tornadoFlags = tornadoFlags + __TORNADOVM_DUMP_BYTECODES_DIR__ + args.dump_bytecodes_dir + " "

        if (args.enableConcurrentDevices):
            tornadoFlags = tornadoFlags + __TORNADOVM_ENABLE_CONCURRENT__DEVICES__

        if (args.enable_profiler != None):
            if (args.enable_profiler == "silent"):
                tornadoFlags = tornadoFlags + __TORNADOVM_ENABLE_PROFILER_SILENT__
            elif (args.enable_profiler == "console"):
                tornadoFlags = tornadoFlags + __TORNADOVM_ENABLE_PROFILER_CONSOLE__
            else:
                print("[ERROR] Please select --enableProfiler <silent|console>")
                sys.exit(0)

        if (args.dump_profiler != None):
            tornadoFlags = tornadoFlags + __TORNADOVM_DUMP_PROFILER__ + args.dump_profiler + " "

        tornadoFlags = tornadoFlags + "-Djava.library.path=" + self.sdk + "/lib "
        if (self.java_version == 8):
            tornadoFlags = tornadoFlags + " -Djava.ext.dirs=" + self.sdk + "/share/java/tornado "
        else:
            tornadoFlags = tornadoFlags + " --module-path ." + os.pathsep + self.sdk + "/share/java/tornado"

        if (args.module_path != None):
            tornadoFlags = tornadoFlags + ":" + args.module_path + " "
        else:
            tornadoFlags = tornadoFlags + " "

        # If the execution will take place through truffle, adapt the flags
        if (self.isTruffleCommand):
            tornadoFlags = self.truffleCompatibleFlags(tornadoFlags)

        return tornadoFlags

    def printVersion(self):
        self.printRelease()
        self.getInstalledBackends(True)

    def ensureArgfileExists(self):
        """
        Automatically expand tornado-argfile.template to tornado-argfile if it doesn't exist.
        This runs on every tornado command to ensure the argfile is always up-to-date.
        Handles cross-platform compatibility (Unix paths vs Windows paths).
        """
        template_path = os.path.join(self.sdk, "tornado-argfile.template")
        argfile_path = os.path.join(self.sdk, "tornado-argfile")

        # If argfile exists and template doesn't, nothing to do
        if os.path.exists(argfile_path) and not os.path.exists(template_path):
            return

        # If template doesn't exist, nothing to do
        if not os.path.exists(template_path):
            return

        # If argfile doesn't exist or template is newer, expand it
        if not os.path.exists(argfile_path) or \
           os.path.getmtime(template_path) > os.path.getmtime(argfile_path):
            try:
                with open(template_path, 'r') as f:
                    content = f.read()

                # Expand ${TORNADO_SDK} to actual path
                # On Windows, convert forward slashes to backslashes in the SDK path
                sdk_path = self.sdk.replace('/', '\\') if os.name == 'nt' else self.sdk
                expanded = content.replace('${TORNADO_SDK}', sdk_path)

                # Fix module path separator for current platform
                # Template uses : (Unix) but Windows needs ;
                if os.name == 'nt':
                    # Only replace : in module-path lines (not in all --add-exports)
                    lines = expanded.split('\n')
                    for i, line in enumerate(lines):
                        if line.startswith('--module-path ') or line.startswith('--upgrade-module-path '):
                            # Replace : with ; in module paths only
                            lines[i] = line.replace(':', ';')
                    expanded = '\n'.join(lines)

                with open(argfile_path, 'w') as f:
                    f.write(expanded)
            except Exception as e:
                # Silently fail - this is not critical for tornado operation
                pass

    def generateArgfile(self):
        """
        Regenerate tornado-argfile in SDK directory.
        Expands ${TORNADO_SDK} placeholders to create a ready-to-use argfile.
        Works portably across Windows, Linux, and macOS.
        """
        template_file = os.path.join(self.sdk, "tornado-argfile.template")
        output_file = os.path.join(self.sdk, "tornado-argfile")

        if not os.path.exists(template_file):
            print(f"[ERROR] Argfile is not found in TORNADO_SDK")
            print(f"[ERROR] Please open an issue in TornadoVM: https://github.com/beehive-lab/TornadoVM/issues")
            sys.exit(1)

        print(f"[INFO] Generating argfile in SDK directory: {self.sdk}")

        try:
            # Read template and expand ${TORNADO_SDK} placeholders
            with open(template_file, 'r') as f:
                content = f.read()

            # Expand ${TORNADO_SDK} to actual SDK path
            # On Windows, ensure backslashes are used
            sdk_path = self.sdk.replace('/', '\\') if os.name == 'nt' else self.sdk
            expanded = content.replace('${TORNADO_SDK}', sdk_path)

            # Fix module path separator for current platform
            # Template uses OS-specific separator, but ensure consistency
            if os.name == 'nt':
                # Only replace : with ; in module-path lines (not in all --add-exports)
                lines = expanded.split('\n')
                for i, line in enumerate(lines):
                    if line.startswith('--module-path ') or line.startswith('--upgrade-module-path '):
                        # Replace : with ; in module paths only
                        lines[i] = line.replace(':', ';')
                expanded = '\n'.join(lines)

            # Write expanded argfile
            with open(output_file, 'w') as f:
                f.write(expanded)

            print(f"[INFO] Generated argfile at: {output_file}")
            if os.name == 'nt':
                print(f"[INFO] You can now use: java @%TORNADO_SDK%\\tornado-argfile -cp <classpath> <MainClass>")
            else:
                print(f"[INFO] You can now use: java @$TORNADO_SDK/tornado-argfile -cp <classpath> <MainClass>")

        except subprocess.CalledProcessError as e:
            print(f"[ERROR] Failed to generate argfile")
            print(f"[ERROR] Command: {e.cmd}")
            print(f"[ERROR] Return code: {e.returncode}")
            if e.stdout:
                print(f"[ERROR] stdout: {e.stdout}")
            if e.stderr:
                print(f"[ERROR] stderr: {e.stderr}")
            sys.exit(1)
        except Exception as e:
            print(f"[ERROR] Unexpected error: {e}")
            sys.exit(1)

    def buildOptionalParameters(self, args):
        params = ""
        if (args.param1 != None):
            params += args.param1 + " "
        if (args.param2 != None):
            params += args.param2 + " "
        if (args.param3 != None):
            params += args.param3 + " "
        if (args.param4 != None):
            params += args.param4 + " "
        if (args.param5 != None):
            params += args.param5 + " "
        if (args.param6 != None):
            params += args.param6 + " "
        if (args.param7 != None):
            params += args.param7 + " "
        if (args.param8 != None):
            params += args.param8 + " "
        if (args.param9 != None):
            params += args.param9 + " "
        if (args.param10 != None):
            params += args.param10 + " "
        if (args.param11 != None):
            params += args.param11 + " "
        if (args.param12 != None):
            params += args.param12 + " "
        if (args.param13 != None):
            params += args.param13 + " "
        if (args.param14 != None):
            params += args.param14 + " "
        if (args.param15 != None):
            params += args.param15 + " "
        return params

    def buildJavaCommand(self, args):
        tornadoFlags = self.buildTornadoVMOptions(args)
        if (self.isTruffleCommand):
            tornadoAddModules = __TORNADOVM_ADD_MODULES__.replace("--", "--vm.-").replace(" ",
                                                                                          "=") + ",tornado.examples"
        else:
            tornadoAddModules = __TORNADOVM_ADD_MODULES__

        javaFlags = ""
        if (args.enableAssertions):
            javaFlags = javaFlags + __GRAAL_ENABLE_ASSERTIONS__

        if (self.isTruffleCommand):
            javaFlags = javaFlags + " " + __TRUFFLE_BASE_OPTIONS__
        else:
            javaFlags = javaFlags + " " + __JAVA_BASE_OPTIONS__

        javaFlags = javaFlags + tornadoFlags + __TORNADOVM_PROVIDERS__ + " "

        upgradeModulePath = "--upgrade-module-path " + self.sdk + "/share/java/graalJars "

        if (self.isGraalVM == False):
            javaFlags = javaFlags + upgradeModulePath

        javaFlags = javaFlags + __JAVA_GC__

        common = self.sdk + __COMMON_EXPORTS__
        opencl = self.sdk + __OPENCL_EXPORTS__
        ptx = self.sdk + __PTX_EXPORTS__
        spirv = self.sdk + __SPIRV_EXPORTS__

        if (self.isTruffleCommand):
            common = self.truffleCompatibleExports(common)
            opencl = self.truffleCompatibleExports(opencl)
            ptx = self.truffleCompatibleExports(ptx)
            spirv = self.truffleCompatibleExports(spirv)

        # For Truffle, exports are already expanded inline (no @ prefix needed)
        # For Java, use @ to read from file
        if (self.isTruffleCommand):
            javaFlags = javaFlags + " " + common + " "
            if ("opencl-backend" in self.listOfBackends):
                javaFlags = javaFlags + opencl + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("spirv-backend" in self.listOfBackends):
                javaFlags = javaFlags + opencl + " " + spirv + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("ptx-backend" in self.listOfBackends):
                javaFlags = javaFlags + ptx + " "
                tornadoAddModules = tornadoAddModules + "," + __PTX_MODULE__
        else:
            javaFlags = javaFlags + " @" + common + " "
            if ("opencl-backend" in self.listOfBackends):
                javaFlags = javaFlags + "@" + opencl + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("spirv-backend" in self.listOfBackends):
                javaFlags = javaFlags + "@" + opencl + " @" + spirv + " "
                tornadoAddModules = tornadoAddModules + "," + __OPENCL_MODULE__
            if ("ptx-backend" in self.listOfBackends):
                javaFlags = javaFlags + "@" + ptx + " "
                tornadoAddModules = tornadoAddModules + "," + __PTX_MODULE__

        javaFlags = javaFlags + tornadoAddModules + " "

        if (args.jvm_options != None):
            javaFlags = javaFlags + args.jvm_options + " "

        if (args.classPath != None):
            javaFlags = javaFlags + " -cp " + args.classPath
            try:
                ## Obtain existing CLASSPATH
                systemClassPath = os.environ["CLASSPATH"]
                javaFlags = javaFlags + ":" + systemClassPath + " "
            except:
                javaFlags = javaFlags + " "

        if (self.isTruffleCommand):
            executionFlags = self.truffleCompatibleFlags(javaFlags)
        else:
            executionFlags = javaFlags

        if os.name == 'nt':
            # Properly quote the command path if it contains spaces
            return '"' + self.cmd + '" ' + executionFlags
        else:
            return self.cmd + " " + executionFlags

    def truffleCompatibleExports(self, exportFile):
        data = Path(exportFile).read_text()
        # ignore the header of the file
        data = re.sub(r'(?m)^\#.*\n?', "", data)
        # make exports compatible with truffle
        data = data.replace('--add', '--vm.-add').replace(' ', '=').replace('\n', ' ')
        return data

    def truffleCompatibleFlags(self, javaFlags):
        flags = javaFlags.split()
        truffleFlags = ""
        for flag in flags:
            if (flag.startswith("--vm") or flag == "--jvm" or flag == "--polyglot"):
                truffleFlags = truffleFlags + flag + " "
            elif (flag.startswith("-D")):
                truffleFlags = truffleFlags + flag.replace("-D", "--vm.D") + " "
            elif (flag.startswith("--module")):
                truffleFlags = truffleFlags + "--vm.-module-path="
            elif (flag.startswith("--")):
                truffleFlags = truffleFlags + flag.replace("--", "--vm.-") + " "
            elif (flag.startswith("-")):
                truffleFlags = truffleFlags + flag.replace("-", "--vm.") + " "
            elif (flag != "@"):
                truffleFlags = truffleFlags + flag + " "
        return truffleFlags

    def executeCommand(self, args):
        javaFlags = self.buildJavaCommand(args)

        if (args.versionJVM):
            command = javaFlags + " -version"
            os.system(command)
            sys.exit(0)

        if (args.printFlags):
            print(javaFlags)
            sys.exit(0)

        if (args.intellijinit):
            ideaUtils.tornadovm_ide_init(os.environ['TORNADO_SDK'], self.java_home, self.listOfBackends)
            sys.exit(0)

        if (args.generate_argfile):
            self.generateArgfile()
            sys.exit(0)

        if (args.showDevices):
            command = javaFlags + "uk.ac.manchester.tornado.drivers.TornadoDeviceQuery verbose"
            os.system(command)
            sys.exit(0)

        params = ""
        if (args.application_parameters != None):
            params = args.application_parameters
        else:
            params = self.buildOptionalParameters(args)
            if (args.module_application and args.application != None):
                params = args.application + " " + params

        if (args.module_application != None):
            command = javaFlags + " -m " + str(args.module_application) + " " + params
        elif (args.jar_file != None):
            if (args.application != None):
                params = args.application + " " + params
            command = javaFlags + " -jar " + str(args.jar_file) + " " + params
        else:
            command = javaFlags + " " + str(args.application) + " " + params
        ## Execute the command
        status = os.system(command)
        sys.exit(status)


def parseArguments():
    """ Parse command line arguments """
    parser = argparse.ArgumentParser(
        description="""Tool for running TornadoVM Applications. This tool sets all Java options for enabling TornadoVM.""")
    parser.add_argument('--version', action="store_true", dest="version", default=False,
                        help="Print version of TornadoVM")
    parser.add_argument('-version', action="store_true", dest="versionJVM", default=False, help="Print JVM Version")
    parser.add_argument('--debug', action="store_true", dest="debug", default=False, help="Enable debug mode")
    parser.add_argument('--fullDebug', action="store_true", dest="fullDebug", default=False,
                        help="Enable the Full Debug mode. This mode is more verbose compared to --debug only")
    parser.add_argument('--threadInfo', action="store_true", dest="threadInfo", default=False,
                        help="Print thread deploy information per task on the accelerator")
    parser.add_argument('--igv', action="store_true", dest="igv", default=False,
                        help="Debug Compilation Graphs using Ideal Graph Visualizer (IGV)")
    parser.add_argument('--igvLowTier', action="store_true", dest="igvLowTier", default=False,
                        help="Debug Low Tier Compilation Graphs using Ideal Graph Visualizer (IGV)")
    parser.add_argument('--printKernel', '-pk', action="store_true", dest="printKernel", default=False,
                        help="Print generated kernel (OpenCL, PTX or SPIR-V)")
    parser.add_argument('--printBytecodes', '-pbc', action="store_true", dest="printBytecodes", default=False,
                        help="Print the generated TornadoVM bytecodes from the Task-Graphs")
    parser.add_argument('--enableProfiler', action="store", dest="enable_profiler", default=None,
                        help="Enable the profiler {silent|console}")
    parser.add_argument('--dumpProfiler', action="store", dest="dump_profiler", default=None,
                        help="Dump the profiler to a file")
    parser.add_argument('--printJavaFlags', action="store_true", dest="printFlags", default=False,
                        help="Print all the Java flags to enable the execution with TornadoVM")
    parser.add_argument('--devices', action="store_true", dest="showDevices", default=False,
                        help="Print information about the  accelerators available")
    parser.add_argument('--enableConcurrentDevices', action="store_true", dest="enableConcurrentDevices", default=False,
                        help="Enable concurrent execution on multiple devices by multiple threads")
    parser.add_argument('--ea', '-ea', action="store_true", dest="enableAssertions", default=False,
                        help="Enable assertions")
    parser.add_argument('--module-path', action="store", dest="module_path", default=None,
                        help="Module path option for the JVM")
    parser.add_argument('--classpath', "-cp", "--cp", action="store", dest="classPath", default=None,
                        help="Set class-path")
    parser.add_argument('--jvm', '-J', action="store", dest="jvm_options", default=None,
                        help="Pass Java options to the JVM. Use without spaces: e.g., --jvm=\"-Xms10g\" or -J\"-Xms10g\"")
    parser.add_argument('-m', action="store", dest="module_application", default=None,
                        help="Application using Java modules")
    parser.add_argument('-jar', action="store", dest="jar_file", default=None,
                        help="Main Java application in a JAR File")
    parser.add_argument('--params', action="store", dest="application_parameters", default=None,
                        help="Command-line parameters for the host-application. Example: --params=\"param1 param2...\"")
    parser.add_argument("application", nargs="?")
    parser.add_argument("--truffle", action="store", dest="truffle_language", default=None,
                        help="Enable Truffle languages through TornadoVM. Example: --truffle python|r|js")
    parser.add_argument("--intellijinit", action="store_true", dest="intellijinit", default=False,
                        help="Generate internal xml files for IntelliJ IDE")
    parser.add_argument('--dumpBC', action="store", dest="dump_bytecodes_dir", default=None,
                        help="Dump the TornadoVM bytecodes to a directory")
    parser.add_argument('--generate-argfile', action="store_true", dest="generate_argfile", default=False,
                        help="Generate tornado-argfile template and expanded argfile in current directory")
    parser.add_argument("param1", nargs="?")
    parser.add_argument("param2", nargs="?")
    parser.add_argument("param3", nargs="?")
    parser.add_argument("param4", nargs="?")
    parser.add_argument("param5", nargs="?")
    parser.add_argument("param6", nargs="?")
    parser.add_argument("param7", nargs="?")
    parser.add_argument("param8", nargs="?")
    parser.add_argument("param9", nargs="?")
    parser.add_argument("param10", nargs="?")
    parser.add_argument("param11", nargs="?")
    parser.add_argument("param12", nargs="?")
    parser.add_argument("param13", nargs="?")
    parser.add_argument("param14", nargs="?")
    parser.add_argument("param15", nargs="?")

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit(0)

    args = parser.parse_args()
    return args


if __name__ == "__main__":

    args = parseArguments()

    tornadoVMRunner = TornadoVMRunnerTool()
    if (args.version):
        tornadoVMRunner.printVersion()
        sys.exit(0)

    tornadoVMRunner.executeCommand(args)
