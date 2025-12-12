# Installing TornadoVM From Source

This document is intended for **contributors** and **advanced users** who want to:
- Modify the TornadoVM runtime, backends, or APIs.
- Develop new features, optimizations, or drivers.
- Run TornadoVM directly from a source checkout.

If you just want to *use* TornadoVM, you do **not** need to build from source — please follow the SDK installation steps in the main `README.md`.

---

## 1. Prerequisites

### 1.1 Software

- A supported OS:
  - Linux (recommended for development)
  - macOS
  - Windows 10+ (with appropriate tools)
- **JDK 21** (or GraalVM based on JDK 21)
- GCC/G++ >= 13.0
- `git`
- C toolchain / build tools (for native parts, depending on backend)

Make sure `JAVA_HOME` points to your JDK/GraalVM 21 installation.

### 1.2 SDK Portability Considerations

**IMPORTANT**: The system you build TornadoVM on determines which systems the SDK can run on.

#### Linux SDK Compatibility

The SDK's native libraries (`.so` files) are compiled against your system's `libstdc++` version, which depends on your GCC version:

| Build System | GCC Version | Required on Target System | Runs On |
|--------------|-------------|--------------------------|---------|
| Ubuntu 24.04 | GCC 13 | `GLIBCXX_3.4.32+` | Ubuntu 24.04+, Fedora 38+, or equivalent |
| Ubuntu 22.04 | GCC 11 | `GLIBCXX_3.4.30+` | Ubuntu 22.04+, Fedora 36+, or equivalent |
| Ubuntu 20.04 | GCC 9/10 | `GLIBCXX_3.4.28+` | Ubuntu 20.04+, Fedora 33+, or equivalent |

**The launcher will detect incompatibilities** and provide clear error messages with upgrade/rebuild instructions.

#### macOS SDK Compatibility

SDKs are bound to the **deployment target** (minimum macOS version):

```bash
# Build for maximum compatibility (supports macOS 11+)
export MACOSX_DEPLOYMENT_TARGET=11.0
make clean && make
```

- SDK built on macOS 14 without setting deployment target may not run on macOS 12
- Set `MACOSX_DEPLOYMENT_TARGET` to your minimum supported macOS version

#### Windows SDK Compatibility

Windows SDKs are compiled with Visual Studio and require the corresponding **Visual C++ Redistributable** on target systems:

- Visual Studio 2015-2022: Requires VC++ 2015-2022 Redistributable (`VCRUNTIME140.dll`, `MSVCP140.dll`)
- Download: [https://aka.ms/vs/17/release/vc_redist.x64.exe](https://aka.ms/vs/17/release/vc_redist.x64.exe)

**The launcher will detect missing runtime** and provide installation instructions.


## 2. Installation from source

TornadoVM can be installed automatically with the [installation script](https://tornadovm.readthedocs.io/en/latest/installation.html). For example:

- On Linux and macOS, run:

```bash
$ ./bin/tornadovm-installer --help
usage: tornadovm-installer [-h] [--jdk JDK] [--backend BACKEND] [--version] [--listJDKs] [--polyglot] [--mvn_single_threaded] [--auto-deps]

TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

options:
  -h, --help            show this help message and exit
  --jdk JDK             Specify a JDK to install by its keyword (e.g., 'jdk21', 'graal-jdk-21'). Run with --listJDKs to view all available JDK keywords.
  --backend BACKEND     Select the backend to install: { opencl, ptx, spirv }
  --version             Print version
  --listJDKs            List supported JDKs
  --polyglot            Enable Truffle Interoperability with GraalVM
  --mvn_single_threaded
                        Run Maven in single-threaded mode
  --auto-deps           Automatic download and use any missing dependencies
```

**NOTE** Select the desired backend:

* `opencl`: Enables the OpenCL backend (requires OpenCL drivers)
* `ptx`: Enables the PTX backend (requires NVIDIA CUDA drivers)
* `spirv`: Enables the SPIRV backend (requires Intel Level Zero drivers)

Example of installation:

```bash
# Install the OpenCL backend with OpenJDK 21
$ ./bin/tornadovm-installer --jdk jdk21 --backend opencl

# It is also possible to combine different backends:
$ ./bin/tornadovm-installer --jdk jdk21 --backend opencl,spirv,ptx
```

- On Windows, run:
```bash
python -m venv .venv
.venv\Scripts\activate.bat
.\bin\windowsMicrosoftStudioTools2022.cmd
python bin\tornadovm-installer --help
usage: tornadovm-installer [-h] [--jdk JDK] [--backend BACKEND] [--version] [--listJDKs] [--polyglot] [--mvn_single_threaded] [--auto-deps]

TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

options:
  -h, --help            show this help message and exit
  --jdk JDK             Specify a JDK to install by its keyword (e.g., 'jdk21', 'graal-jdk-21'). Run with --listJDKs to view all available JDK keywords.
  --backend BACKEND     Select the backend to install: { opencl, ptx, spirv }
  --version             Print version
  --listJDKs            List supported JDKs
  --polyglot            Enable Truffle Interoperability with GraalVM
  --mvn_single_threaded
                        Run Maven in single-threaded mode
  --auto-deps           Automatic download and use any missing dependencies
```

Example of installation:

```bash
# Install the OpenCL backend with OpenJDK 21
$ python bin\tornadovm-installer --jdk jdk21 --backend opencl

# It is also possible to combine different backends:
$ python bin\tornadovm-installer --jdk jdk21 --backend opencl,spirv,ptx
```

More information are available in the [documentation page](https://tornadovm.readthedocs.io/en/latest/installation.html#b-manual-installation).

## 3. Run TornadoVM tests & Programs

You are ready to run the TornadoVM unit-tests, examples and benchmarks. Follow the [instructions](https://tornadovm.readthedocs.io/en/latest/simple-start.html#running-examples-and-benchmarks).

---

Happy hacking on TornadoVM!
