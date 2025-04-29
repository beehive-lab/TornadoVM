Installation & Configuration
#############################

Pre-requisites
****************

These need to be installed before executing either automatic or manual TornadoVM installation:

* GCC >= 10.0 or LLVM/clang (Linux)
* Xcode >= 15 (macOS only)
* Visual Studio Community 2022 (Windows 11 recommended)
* Python >= 3.6 (all OSes)
* At least one of following drivers:
      * OpenCL drivers: GPUs and CPUs >= 2.1, FPGAs >= 1.0
      * NVIDIA drivers and CUDA Toolkit 10.0+
      * Intel compute-runtime and/or GPU drivers (OpenCL), and Level-Zero >= 1.2

For Intel-based MacOS users: the OpenCL support for your Apple model can be confirmed `here <https://support.apple.com/en-gb/HT202823>`_.

Supported Platforms
*******************

The following table includes the platforms that TornadoVM can be executed.

+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| OS                        | OpenCL Backend                                            | PTX Backend     | SPIR-V Backend       |
+===========================+===========================================================+=================+======================+
| CentOS >= 7.3             | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Fedora >= 39              | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Ubuntu >= 20.04           | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Pop!_OS >= 22.04          | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| OpenSuse Leap 15.4        | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Apple M1/M2/M3            | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Windows 10/11             | OpenCL for GPUs and CPUs >= 2.1, FPGAs not tested         | CUDA 12.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Windows WSL               | OpenCL for GPUs and CPUs >= 2.1, FPGAs not tested         | CUDA 12.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+

**Note:** The SPIR-V backend of TornadoVM is supported for Linux and Windows systems.
The SPIR-V backend can be dispatched through two different runtimes:

- Via Level Zero: it runs on Intel HD Graphics (integrated GPUs), and Intel ARC GPUs.
- Via OpenCL: it runs on Linux and Windows on any device with OpenCL >= 2.1 support (CPUs, GPUs).

.. _installation:

Installation
************

TornadoVM can be built with three compiler backends and is able to generate OpenCL, PTX and SPIR-V code.

Installation Script
===================

The ``tornadovm-installer`` script provided in this repository will compile/download ``OpenJDK``, ``CMake``, ``Maven`` and it will build the TornadoVM project.
This installation script has been tested on Linux, macOS and Windows.
Additionally, this installation type will automatically trigger all dependencies, therefore it is recommended if users only need to invoke TornadoVM as a library.

.. code-block:: bash

    $ ./bin/tornadovm-installer
      usage: tornadovm-installer [-h] [--version] [--jdk JDK] [--backend BACKEND] [--listJDKs] [--javaHome JAVAHOME]
                           [--polyglot]

      TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

      options:
        -h, --help           show this help message and exit
        --version            Print version of TornadoVM
        --jdk JDK            Select one of the supported JDKs. Use --listJDKs option to see all supported ones.
        --backend BACKEND    Select the backend to install: { opencl, ptx, spirv }
        --listJDKs           List all JDK supported versions
        --javaHome JAVAHOME  Use a JDK from a user directory
        --polyglot           To enable interoperability with Truffle Programming Languages.


**Note:** Select the desired backend with the ``--backend`` option:
  * ``opencl``: Enables the OpenCL backend (it requires OpenCL drivers and OpenCL SDK installed)
  * ``ptx``: Enables the PTX backend (it requires NVIDIA Driver and the CUDA SDK)
  * ``spirv``: Enables the SPIRV backend (it requires Intel Level Zero drivers)


For example, to build TornadoVM with GraalVM (JDK21) for all backends:

.. code-block:: bash

  ## Install with Graal for JDK 21 using PTX, OpenCL and SPIRV backends
  ./bin/tornadovm-installer --jdk graal-jdk-21  --backend opencl,ptx,spirv


Another example: to build TornadoVM with OpenJDK 21 for the OpenCL and PTX backends:

.. code-block:: bash

  ./bin/tornadovm-installer --jdk jdk21 --backend opencl,ptx


Windows example: to build TornadoVM with GraalVM and all supported backends (mind backslash and quotes):

.. code-block:: bash

  rem invoke the Microsoft Visual Studio Tool Terminal 
  .\bin\windowsMicrosoftStudioTools2022.cmd

  rem create and activate a virtual environment

  python -m venv .venv
  .venv\Scripts\activate.bat

  python bin\tornadovm-installer --jdk graal-jdk-21 --backend opencl,ptx,spirv


**Notes on Windows:**

- The installer must run in a virtual Python environment (`venv`) to automatically install and import a missing ``wget`` Python module. Otherwise, the installer fails to install and import ``wget`` and reports an error. Although the installer works fine on the second try, using a `venv` from the start is a smarter approach.

- Running the TornadoVM test suite on Windows requires using ``nmake`` which is part of Visual Studio:

  .. code-block:: bash

    tornado-test -V


After the installation, the scripts create a directory with the TornadoVM SDK. The directory also includes a source file with all variables needed to start using TornadoVM.
After the script finished the installation, set the environment variables needed.

On Linux and macOS by using:

.. code-block:: bash

  $ source setvars.sh

On Windows by using:

.. code-block:: bash

  C:> setvars.cmd


.. _installation_linux:

Installation for Linux 
=======================

Download dependencies (Red Hat-based):

.. code:: bash

   sudo dnf install gcc g++ git cmake python3


Download dependencies (Ubuntu-based):

.. code:: bash

   sudo apt-get install gcc g++ git cmake python3


Install the driver/s. Download the required driver/s from the hardware vendor as well as the required SDKs (e.g. CUDA for NVIDIA GPUs).


Once the drivers and SDK are installed, download and build TornadoVM. 
For example, using JDK 21 for all backends:

.. code:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM
   bin/tornadovm-installer --jdk jdk21 --backend opencl,ptx,spirv
   source setvars.sh


Check the installation: 

.. code:: bash

   # list the accelerator devices that are reachable from TornadoVM
   tornado --devices

   # run unit tests
   tornado-test -V

   ## run specific examples (e.g., NBody)
   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody


To recompile TornadoVM to use a different backend:

.. code:: bash

   source setvars.sh
   make BACKEND=opencl



.. _installation_appleMSeries:

Installation for MacOS M1/M2/M3
===============================


Download dependencies:

.. code:: bash

   brew install wget
   brew install maven


Download and install TornadoVM. Note that, in OSx Apple M1/M2/M3 chip, the only backend supported is OpenCL. 


.. code:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM
   bin/tornadovm-installer --jdk graal-jdk-21 --backend opencl
   source setvars.sh


Check the installation: 

.. code:: bash

   # list the accelerator devices that are reachable from TornadoVM
   tornado --devices

   # run unit tests
   tornado-test -V

   ## run specific examples (e.g., NBody)
   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody


To recompile TornadoVM after an update:

.. code:: bash

   source setvars.sh
   make 



.. _installation_windows:

Installation for Windows 10/11
==============================

**[DISCLAIMER] Please, note that, although TornadoVM can run on Windows 10/11, it is still experimental.**

1. Install prerequisites
~~~~~~~~~~~~~~~~~~~~~~~~

Maven
^^^^^^

Download Apache Maven (at least 3.9.0) from the `official site <https://maven.apache.org/download.cgi>`__, and extract it to any
location on your computer. Add Maven's ``bin`` folder to ``PATH``.

.. code:: bash

   rem Maven unpacked to %ProgramFiles%\apache-maven-3.9.1
   set PATH=%ProgramFiles%\apache-maven-3.9.1\set;%PATH%


CMake
^^^^^^

Download and install CMake from the `official site <https://cmake.org/download/>`__. Although the installer should have updated ``PATH``, check whether the executable "cmake.exe" can be found and correct "PATH" if necessary.


2. Install the GPU drivers and toolkits (e.g., NVIDIA drivers and CUDA Toolkit)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A) CUDA Driver

Most Windows systems come with the NVIDIA drivers pre-installed. You can check your installation and the latest drivers available by using
`NVIDIA GEFORCE Experience <https://www.nvidia.com/it-it/geforce/geforce-experience/download/>`__ tool.

Alternatively, all NVIDIA drivers can be found here: `NVIDIA Driver Downloads <https://www.nvidia.com/Download/index.aspx>`__.

B) OpenCL and NVIDIA PTX

If you plan to only use the OpenCL backend from TornadoVM, then you only
need the NVIDIA driver from the previous step.

If you want to also use the PTX backend, then you need to install the
NVIDIA CUDA Toolkit.

-  Complete CUDA Toolkit from `CUDA Toolkit
   Downloads <https://developer.nvidia.com/cuda-downloads?target_os=Windows&target_arch=x86_64>`__.

It is important to make sure that the GPU drivers are included with the CUDA Toolkit, so you may avoid downloading drivers separately.
The only thing to note is that the GPU driver you are currently using should be of the same or higher version than the one shipped with CUDA Toolkit.
Thus, if you have the driver already installed, make sure that the version required by the CUDA Toolkit is same or higher, otherwise update the GPU driver during toolkit installation.
Note, that NSight, BLAST libs and Visual Studio integration are irrelevant for TornadoVM builds, you just need the CUDA Toolkit - so you may skip installing them.


3. Install Visual Studio Community 2022 and Python (use the Windows installer for each of those)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- `Visual Studio Community 2022 <https://visualstudio.microsoft.com/vs/community/>`_. 
- `Python3 for Windows <https://www.python.org/downloads/windows/>`_.

If you have not configured Visual Studio 2022 to use C++, you may need to install it using the Visual Studio Installer. 
In this case, enable the following packages:

- MSVC C++ x86/64 build tools (latest)
- MSVC C++ x86/64 Spectre-mitigated libs (latest)
- C++ ATL for latest build tools (latest for x86/64)
- C++ ATL for latest build tools with Spectre Mitigations (x86/64)


4. Download TornadoVM
~~~~~~~~~~~~~~~~~~~~~~

Clone the latest TornadoVM source code from the GitHub `repository <https://github.com/beehive-lab/TornadoVM>`__:

.. code:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM

Hereafter, the directory with the source code will be referred as ``<TornadoVM>``.


5. Configure/Compile the TornadoVM Project 
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


The installation script downloads the following dependencies:

- Java
- Maven
- cmake

.. code:: bash

   python -m venv .venv
   .venv\Scripts\activate.bat
   .\bin\windowsMicrosoftStudioTools2022.cmd
   python bin\tornadovm-installer --jdk jdk21 --backend=opencl 
   setvars.cmd


And TornadoVM is ready to be used. If you want to recompile with a different backend: 

.. code:: bash

   python -m venv .venv
   .venv\Scripts\activate.bat
   .\bin\windowsMicrosoftStudioTools2022.cmd
   nmake /f Makefile.mak jdk21 BACKEND=opencl,ptx
   setvars.cmd


6. Check the installation
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   # list the accelerator devices that are reachable from TornadoVM
   tornado --devices

   # run unit tests
   tornado-test -V

   ## run specific examples (e.g., NBody)
   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody


.. _installation_windows_wsl:

Installation for Windows Subsystem for Linux (WSL)
===================================================


This tutorial shows how to install TornadoVM with CUDA to run on NVIDIA GPUs within WSL, and Intel GPU via the Intel compute runtime.

Install WSL using PowerShell
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
.. code:: bash

   ## By default, Windows 11 installs Ubuntu 24.04 LTS, as in Jan 2025
   wsl --install


For more details about how to configure WSL, follow the official documentation: `link <https://learn.microsoft.com/en-us/windows/wsl/install>`_


Setup CUDA in WSL
~~~~~~~~~~~~~~~~~~

If you have an NVIDIA GPU installed in your Windows 11 PC, the NVIDIA driver is also installed for WSL.
What we need to install next is the CUDA SDK. Open a terminal in WSL:

.. code:: bash

   ## Update the system
   sudo apt-get update
   sudo apt-get dist-upgrade


Install CUDA. For detailed instructions, follow the NVIDIA's guidelines: `link <https://docs.nvidia.com/cuda/wsl-user-guide/index.html>`_.


.. code:: bash

   sudo apt-key del 7fa2af80

   wget https://developer.download.nvidia.com/compute/cuda/repos/wsl-ubuntu/x86_64/cuda-wsl-ubuntu.pin
   sudo mv cuda-wsl-ubuntu.pin /etc/apt/preferences.d/cuda-repository-pin-600
   wget https://developer.download.nvidia.com/compute/cuda/12.6.3/local_installers/cuda-repo-wsl-ubuntu-12-6-local_12.6.3-1_amd64.deb
   sudo dpkg -i cuda-repo-wsl-ubuntu-12-6-local_12.6.3-1_amd64.deb
   sudo cp /var/cuda-repo-wsl-ubuntu-12-6-local/cuda-*-keyring.gpg /usr/share/keyrings/
   sudo apt-get update
   sudo apt-get -y install cuda-toolkit-12-6


Update the ``~/.bashrc file``:

.. code:: bash

   export C_INCLUDE_PATH=/usr/local/cuda/include
   export CPLUS_INCLUDE_PATH=/usr/local/cuda/include
   export LD_LIBRARY_PATH=/usr/local/cuda/lib64
   export PATH=/usr/local/cuda/bin/:$PATH


Login again or type ``bash``.


Now you can install TornadoVM.


Install Intel Compute Runtime for OpenCL and Level Zero for WSL
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


Go to `https://github.com/intel/compute-runtime/releases/ <https://github.com/intel/compute-runtime/releases/>`_ and download the latest release.
In this tutorial, the latest version is ``24.48.31907.7`` (`link <https://github.com/intel/compute-runtime/releases/tag/24.48.31907.7>`_).


.. code:: bash

   mkdir -p ~/bin/neo
   cd ~/bin/neo
   wget https://github.com/intel/intel-graphics-compiler/releases/download/v2.2.3/intel-igc-core-2_2.2.3+18220_amd64.deb
   wget https://github.com/intel/intel-graphics-compiler/releases/download/v2.2.3/intel-igc-opencl-2_2.2.3+18220_amd64.deb
   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/intel-level-zero-gpu-dbgsym_1.6.31907.7_amd64.ddeb
   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/intel-level-zero-gpu_1.6.31907.7_amd64.deb
   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/intel-opencl-icd-dbgsym_24.48.31907.7_amd64.ddeb
   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/intel-opencl-icd_24.48.31907.7_amd64.deb
   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/libigdgmm12_22.5.4_amd64.deb


Verify CheckSums:

.. code:: bash

   wget https://github.com/intel/compute-runtime/releases/download/24.48.31907.7/ww48.sum
   sha256sum -c ww48.sum


Install packages:

.. code:: bash

   sudo dpkg -i *.deb


Update soft link for OpenCL:


.. code:: bash

   sudo ln -s /usr/lib/x86_64-linux-gnu/libOpenCL.so.1 /usr/lib/x86_64-linux-gnu/libOpenCL.so



We are ready to install TornadoVM.


Install TornadoVM for WSL
~~~~~~~~~~~~~~~~~~~~~~~~~


Install a new Python's environment:

.. code:: bash

   sudo apt install python3-venv
   ## Setup a new environment for Python modules
   python3 -m venv ~/bin/venv
   source ~/bin/venv/bin/activate


Clone and build TornadoVM:


.. code:: bash

   cd ~/
   git clone https://github.com/beehive-lab/TornadoVM.git tornado
   cd tornado

  ## Install OpenCL only
   ./bin/tornadovm-installer --jdk jdk21 --backend=opencl

   ## Install OpenCL and PTX
   ./bin/tornadovm-installer --jdk jdk21 --backend=opencl,ptx

   ## Install All backends:
   ./bin/tornadovm-installer --jdk jdk21 --backend=opencl,ptx,spirv


Finally enable environment:

.. code:: bash

   source ~/bin/venv/bin/activate
   source setvars.sh

Run tests:

.. code:: bash

   make tests




.. _installation_mali:

Installation for ARM Mali GPUs
==============================

1. Installation
~~~~~~~~~~~~~~~~

The installation of TornadoVM to run on ARM Mali GPUs requires JDK21 with GraalVM.

The OpenCL driver for Mali GPUs on Linux that has been tested is:

-  OpenCL C 2.0 ``v1.r9p0-01rel0.37c12a13c46b4c2d9d736e0d5ace2e5e``:
   `link <https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel>`__

2. Testing on ARM MALI GPUs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We have tested TornadoVM on the following ARM Mali GPUs:

-  Mali-G71, which implements the Bifrost architecture:
   `link <https://developer.arm.com/ip-products/graphics-and-multimedia/mali-gpus/mali-g71-gpu>`__

Some of the unittests in TornadoVM run with ``double`` data types.
To enable double support, TornadoVM includes the following extension in the generated OpenCL code:

.. code:: c

   cl_khr_fp64

However, this extension is not available on Bifrost GPUs.

The rest of the unittests should pass.


Running Examples
================

TornadoVM uses modules:

To run examples:

.. code:: bash

   $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D 1024

To run benchmarks:

.. code:: bash

   $ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dft


Run tests:

.. code:: bash

   tornado-test -V 


To run individual tests:

.. code:: bash

   tornado --jvm "-Dtornado.unittests.verbose=True -Xmx6g"  -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner uk.ac.manchester.tornado.unittests.arrays.TestArrays

.. _installation_riscv:

Installation for RISC-V RVV 1.0 on Linux
========================================

The RISC-V port is experimental, but users can try it on real RISC-V hardware. 
The following instructions have been tested on Linux Bianbu OS 2.0 and 2.1 on a Bananapi F3 SBC and Sipeed Lichee PI 3A.

The installation requires a patch that disables the `cmake-maven` plugin for the native OpenCL part due to unsupported port for RISC-V. 

We have pushed a script that automatically applies the patch and builds TornadoVM to run on RISC-V. 


First, install the dependencies:

.. code:: bash

   sudo apt-get install clinfo gcc g++
   sudo ln -s libOpenCL.so.1 libOpenCL.so


Configure a new Python environment:

.. code:: bash

   python -m venv /path/to/venv


Activate the new Python environment:

.. code:: bash

   source /path/to/venv/bin/activate 


OpenCL backend only
~~~~~~~~~~~~~~~~~~~

Then, download the script to apply the patch for the OpenCL backend:


.. code:: bash

   cd tornadovm 
   git clone https://github.com/beehive-lab/tornadovm-riscv-patch.git
   
   ## Build for OpenCL only
   bash tornadovm-riscv-patch/apply-riscv-patch-opencl.sh 


SPIR-V + OpenCL backends
~~~~~~~~~~~~~~~~~~~~~~~~

If you want to enable both OpenCL and SPIR-V backends, use the following patch:

.. code:: bash

   bash tornadovm-riscv-patch/apply-riscv-patch-spirv.sh 
   

Run TornadoVM for RISC-V
~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   source setvars.sh
   tornado --devices 

   Number of Tornado drivers: 1
   Driver: OpenCL
      Total number of OpenCL devices  : 1
      Tornado device=0:0  (DEFAULT)
        OPENCL --  [ComputeAorta] -- RefSi G1 RV64    << RISC-V CPU 
                Global Memory Size: 2.0 GB
                Local Memory Size: 256.0 KB
                Workgroup Dimensions: 3
                Total Number of Block Threads: [1024]
                Max WorkGroup Configuration: [1024, 1024, 1024]
                Device OpenCL C version: OpenCL C 1.2 Clang 19.1.5


Known issues on Linux
=======================

- For Ubuntu >= 16.04, install the package ``ocl-icd-opencl-dev``

Then the following package should be installed:

.. code:: bash

   $ apt-get install ocl-icd-opencl-dev



IDE Code Formatter
==================

Using Eclipse and Netbeans
~~~~~~~~~~~~~~~~~~~~~~~~~~

The code formatter in Eclipse is automatically applied after generating the setting files.

.. code-block:: bash

  $ mvn eclipse:eclipse
  $ python scripts/eclipseSetup.py


For Netbeans, the Eclipse Formatter Plugin is needed.

Using IntelliJ
~~~~~~~~~~~~~~


Install plugins:

- Eclipse Code Formatter
- Save Actions

Then :

1. Open File > Settings > Eclipse Code Formatter.
2. Check the ``Use the Eclipse code`` formatter radio button.
3. Set the Eclipse Java Formatter config file to the XML file stored in ``/scripts/templates/eclise-settings/Tornado.xml``.
4. Set the Java formatter profile in Tornado.


TornadoVM Maven Projects
========================


To use the TornadoVM API in your projects, you can checkout our maven repository as follows:


.. code-block:: xml

   <repositories>
     <repository>
       <id>universityOfManchester-graal</id>
       <url>https://raw.githubusercontent.com/beehive-lab/tornado/maven-tornadovm</url>
     </repository>
   </repositories>

   <dependencies>
      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-api</artifactId>
         <version>1.1.0</version>
      </dependency>

      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-matrices</artifactId>
         <version>1.1.0</version>
      </dependency>
   </dependencies>



Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled.

Versions available
==================

* 1.1.0
* 1.0.10
* 1.0.9
* 1.0.7
* 1.0.6
* 1.0.5
* 1.0.4
* 1.0.3
* 1.0.2
* 1.0.1
* 1.0
