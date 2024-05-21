Installation & Configuration
##################################

Pre-requisites
****************

These need to be installed before executing either automatic or manual TornadoVM installation:

* GCC >= 9.0 or LLVM/clang (Linux)
* Xcode >= 15 (macOS)
* Visual Studio Community 2022 (Windows)
* Python >= 3.0 (all OSes)
* At least one of following drivers:
      * OpenCL drivers: GPUs and CPUs >= 2.1, FPGAs >= 1.0
      * NVIDIA drivers and CUDA Toolkit 10.0+
      * Intel drivers and Level-Zero >= 1.2

For macOS users: the OpenCL support for your Apple model can be confirmed `here <https://support.apple.com/en-gb/HT202823>`_.

Supported Platforms
***********************

The following table includes the platforms that TornadoVM can be executed.

+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| OS                        | OpenCL Backend                                            | PTX Backend     | SPIR-V Backend       |
+===========================+===========================================================+=================+======================+
| CentOS >= 7.3             | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Fedora >= 21              | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Ubuntu >= 16.04           | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Pop!_OS >= 22.04          | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| OpenSuse Leap 15.4        | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0  | CUDA 10.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| macOS Mojave 10.14.6      | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| macOS Catalina 10.15.3    | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| macOS Big Sur 11.5.1      | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| macOS Sonoma 14.3         | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Apple M1                  | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Windows 10/11             | OpenCL for GPUs and CPUs >= 2.1, FPGAs not tested         | CUDA 12.0+      | Level-Zero >= 1.2    |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+

**Note:** The SPIR-V backend of TornadoVM is only supported for Linux OS.
Besides, the SPIR-V backend with Level Zero runs on Intel HD Graphics (integrated GPUs), and Intel ARC GPUs.

.. _installation:

Installation
*****************

TornadoVM can be built with three compiler backends and is able to generate OpenCL, PTX and SPIR-V code.
There are two ways to install TornadoVM:

A) Automatic Installation
===========================

The ``tornadovm-installer`` script provided in this repository will compile/download OpenJDK, CMake, Maven and it will build TornadoVM.
This installation script has been tested on Linux, macOS and Windows.
Additionally, this installation type will automatically trigger all dependencies, therefore it is recommended if users only need to invoke TornadoVM as a library.

.. code-block:: bash

    $ ./bin/tornadovm-installer
      usage: tornadovm-installer [-h] [--version] [--jdk JDK] [--backend BACKEND] [--listJDKs] [--javaHome JAVAHOME]

      TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

      optional arguments:
       -h, --help           show this help message and exit
       --version            Print version of TornadoVM
       --jdk JDK            Select one of the supported JDKs. Use --listJDKs option to see all supported ones.
       --backend BACKEND    Select the backend to install: { opencl, ptx, spirv }
       --listJDKs           List all JDK supported versions
       --javaHome JAVAHOME  Use a JDK from a user directory


**Note:** Select the desired backend with the ``--backend`` option:
  * ``opencl``: Enables the OpenCL backend (it requires OpenCL drivers)
  * ``ptx``: Enables the PTX backend (it requires NVIDIA CUDA drivers)
  * ``spirv``: Enables the SPIRV backend (it requires Intel Level Zero drivers)


For example, to build TornadoVM with GraalVM and JDK 21:

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


B) Manual Installation
========================

TornadoVM can be executed with the following configurations:

**Note**: For simplicity you can use `SDKMAN <https://sdkman.io/>`_ for managing multiple JDK versions.

Linux
~~~~~~~~

- TornadoVM with GraalVM for Linux and macOS (JDK 21): see the installation guide here: :ref:`installation_graalvm`.
- TornadoVM with JDK21 (e.g. OpenJDK 21, Red Hat Mandrel, Amazon Corretto): see the installation guide: :ref:`installation_jdk17plus`.

Windows
~~~~~~~~~~

To run TornadoVM on **Windows 10/11**, install TornadoVM with GraalVM. More information here: :ref:`installation_windows`.


ARM Mali GPUs and Linux
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To run TornadoVM on ARM Mali, install TornadoVM with GraalVM and JDK 21. More information here: :ref:`installation_mali`.


Compilation with Maven
~~~~~~~~~~~~~~~~~~~~~~~~
This installation type requires users to manually install all the dependencies, therefore it is recommended for developing TornadoVM.
At least one backend must be specified at build time to the ``make`` command:

.. code-block:: bash

  ## Choose the desired backend
  $ make BACKENDS=opencl,ptx,spirv


.. _installation_graalvm:

Installation for GraalVM for JDK 21.0.1 on Linux and macOS
================================================================

1. Download GraalVM JDK 21.0.1
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

GraalVM **Community Edition** builds are available to download at:

`https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.1 <https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.1>`_.

The examples below show how to download and extract GraalVM for JDK 21.0.0


-  Example for GraalVM for JDK 21 Community 21.0.1:

.. code:: bash

   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.1/graalvm-community-jdk-21.0.1_linux-x64_bin.tar.gz
   $ tar -xf graalvm-community-jdk-21.0.1_linux-x64_bin.tar.gz

with SDKMAN:

.. code:: bash

  $ sdk install java 21-graalce
  $ sdk use java 21-graalce


The Java binary will be found in the `graalvm-jdk-{JDK_VERSION}-23.1.0` directory. This directory is used as the JAVA_HOME (See step 2).

**Note** if installed with SDKMAN there is no need to manually set your JAVA_HOME.

For macOS:

-  Example for GraalVM for JDK 21 Community 21.0.1:

.. code:: bash

   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.1/graalvm-community-jdk-21.0.1_macos-x64_bin.tar.gz

then ``untar`` it to the macOS standard JDK location
``/Library/Java/JavaVirtualMachines/`` or to a folder of your choice.

1. Download TornadoVM
~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

    $ cd ..
    $ git clone https://github.com/beehive-lab/TornadoVM tornadovm
    $ cd tornadovm

Create/edit your configuration file:

.. code:: bash

   $ vim etc/sources.env

The first time you need to create the ``etc/sources.env`` file and add
the following code in it **(after updating the paths to your correct
ones)**:

.. code:: bash

   #!/bin/bash
   export JAVA_HOME=<path to GraalVM jdk> ## This path is produced in Step 1
   export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
   export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
   export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)

This file should be loaded once after opening the command prompt for the
setup of the required paths:

.. code:: bash

   $ source ./etc/sources.env

For macOS: the exports above may be added to ``~/.profile``

3. Install CMAKE (if cmake < 3.6)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For Linux:
^^^^^^^^^^

.. code::bash

   $ cmake -version

**If the version of cmake is > 3.6 then skip the rest of this step and
go to Step 4.** Otherwise try to install cmake.

For simplicity it might be easier to install cmake in your home
directory. \* Redhat Enterprise Linux / CentOS use cmake v2.8 \* We
require a newer version so that OpenCL is configured properly.

.. code:: bash

   $ cd ~/Downloads
   $ wget https://cmake.org/files/v3.10/cmake-3.10.1-Linux-x86_64.tar.gz
   $ cd ~/opt
   $ tar -tvf ~/Downloads/cmake-3.10.1-Linux-x86_64.tar.gz
   $ mv cmake-3.10.1-Linux-x86_64 cmake-3.10.1
   $ export PATH=$HOME/opt/cmake-3.10.1/bin/:$PATH
   $ cmake -version
   cmake version 3.10.1

Then export ``CMAKE_ROOT`` variable to the cmake installation. You can
add it to the ``./etc/sources.env`` file.

.. code:: bash

   $ export CMAKE_ROOT=/opt/cmake-3.10.1

For macOS:
^^^^^^^^^^^^

Install cmake:

.. code:: bash

   $ brew install cmake

then

.. code:: bash

   export CMAKE_ROOT=/usr/local

which can be added to ``~/.profile``

4. Compile TornadoVM with GraalVM
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   $ cd ~/tornadovm
   $ . etc/sources.env

To build with GraalVM and JDK 21:

.. code:: bash

   $ make graal-jdk-21 BACKEND={ptx,opencl}

and done!!


.. _installation_jdk17plus:

TornadoVM for JDK 21 on Linux and macOS
==============================================


**DISCLAIMER:**

TornadoVM is based on the Graal compiler that depends on JVMCI (Java Virtual Machine Compiler Interface). Different JDKs come with different
versions of JVMCI. Therefore, the version of the Graal compiler that TornadoVM uses might not be compatible with the JVMCI version of some
JDKs. Below are listed the Java 21 JDK distributions against which TornadoVM has been tested, but compatibility is not guaranteed.

.. code:: bash

  ./bin/tornadovm-installer --listJDKs
  jdk21            : Install TornadoVM with OpenJDK 21 (Oracle OpenJDK)
  graal-jdk-21     : Install TornadoVM with GraalVM and JDK 21 (GraalVM 23.1.0)
  mandrel-jdk-21   : Install TornadoVM with Mandrel and JDK 21 (GraalVM 23.1.0)
  corretto-jdk-21  : Install TornadoVM with Corretto JDK 21
  zulu-jdk-jdk-21  : Install TornadoVM with Azul Zulu JDK 21
  temurin-jdk-21   : Install TornadoVM with Eclipse Temurin JDK 21


1. Download a JDK 21 distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- OpenJDK distributions are available to download at https://adoptium.net/.
- Red Hat Mandrel releases are available at https://github.com/graalvm/mandrel/releases.
- Amazon Coretto releases are available at https://aws.amazon.com/corretto/.
- Microsoft OpenJDK releases are available at https://docs.microsoft.com/en-us/java/openjdk/download. Azul Zulu
- OpenJDK releases are available at `https://www.azul.com/downloads <https://www.azul.com/downloads/?package=jdk#download-openjdk>`__.
- Eclipse Temurin releases are available at `https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.1%2B12 <https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.1%2B12>`__.

1.1 Manage JDKs manually
^^^^^^^^^^^^^^^^^^^^^^^^^^^
After downloading and extracting the JDK distribution, point your ``JAVA_HOME`` variable to the JDK root.

Example using Amazon Corretto:

.. code:: bash

    $ wget https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz
    $ tar xf amazon-corretto-21-x64-linux-jdk.tar.gz
    $ export JAVA_HOME=$PWD/amazon-corretto-21-x64-linux

1.2 Manage JDKs with SDKMAN
^^^^^^^^^^^^^^^^^^^^^^^^^^^
There is no need to change your ``JAVA_HOME`` as SDKMAN exports it every time you switch between distributions.

Example using Amazon Corretto:

.. code:: bash

    $ sdk install java 21-amzn
    $ sdk use java 21-amzn

A complete list of all available Java Versions for Linux 64bit can be obtained with:

.. code:: bash

    $ sdk list java

2. Download TornadoVM
~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

    $ git clone https://github.com/beehive-lab/TornadoVM tornadovm
    $ cd tornadovm

Create/edit your configuration file:

.. code:: bash

   $ vim etc/sources.env

The first time you need to create the ``etc/sources.env`` file and add
the following code in it **(after updating the paths to your correct
ones)**:

.. code:: bash

   #!/bin/bash
   export JAVA_HOME=<path to JDK21> ## This path is produced in Step 1
   export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
   export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
   export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)

This file should be loaded once after opening the command prompt for the
setup of the required paths:

.. code:: bash

   $ source ./etc/sources.env

For macOS: the exports above may be added to ``~/.profile``


3. Install CMAKE (if cmake < 3.6)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For Linux:
^^^^^^^^^^

.. code:: bash

   $ cmake -version

**If the version of cmake is > 3.6 then skip the rest of this step and
go to Step 4.** Otherwise try to install cmake.

For simplicity it might be easier to install cmake in your home
directory.

-  Redhat Enterprise Linux / CentOS use cmake v2.8
-  We require a newer version so that OpenCL is configured properly.

.. code:: bash

   $ cd ~/Downloads
   $ wget https://cmake.org/files/v3.10/cmake-3.10.1-Linux-x86_64.tar.gz
   $ cd ~/opt
   $ tar -tvf ~/Downloads/cmake-3.10.1-Linux-x86_64.tar.gz
   $ mv cmake-3.10.1-Linux-x86_64 cmake-3.10.1
   $ export PATH=$HOME/opt/cmake-3.10.1/bin/:$PATH
   $ cmake -version
   cmake version 3.10.1

Then export ``CMAKE_ROOT`` variable to the cmake installation. You can
add it to the ``./etc/sources.env`` file.

.. code:: bash

   $ export CMAKE_ROOT=/opt/cmake-3.10.1

For macOS:
^^^^^^^^^^^^^

Install cmake:

.. code:: bash

   $ brew install cmake

then

.. code:: bash

   export CMAKE_ROOT=/usr/local

which can be added to ``~/.profile``

4. Compile TornadoVM for JDK 21
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   $ cd ~/tornadovm
   $ . etc/sources.env

To build with a distribution of JDK 21

.. code:: bash

   $ make jdk21 BACKEND={ptx,opencl}

and done!!

Running with JDK 21
~~~~~~~~~~~~~~~~~~~~~~~~~~

TornadoVM uses modules:

To run examples:

.. code:: bash

   $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D --params "512"

To run benchmarks:

.. code:: bash

   $ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner --params "dft"

To run individual tests:

.. code:: bash

   tornado --jvm "-Dtornado.unittests.verbose=True -Xmx6g"  -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner --params "uk.ac.manchester.tornado.unittests.arrays.TestArrays"


.. _installation_windows:

Installing TornadoVM for Windows 10/11
======================================

**[DISCLAIMER] Please, note that, although TornadoVM can run on Windows 10/11, it is still experimental.**

1. Install prerequisites
~~~~~~~~~~~~~~~~~~~~~~~~~~

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
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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
~~~~~~~~~~~~~~~~~~~~~~~~

Clone the latest TornadoVM source code from the GitHub `repository <https://github.com/beehive-lab/TornadoVM>`__:

.. code:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM

Hereafter, the directory with the source code will be referred as ``<TornadoVM>``.


5. Configure/Compile the TornadoVM Project 
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

There are two ways to install it. Choose one of them. We highly recommend the first option, since it will install
the required software dependencies.


A) Using the automatic installation script. This script downloads the following dependencies:

- Java
- Maven
- cmake

.. code:: bash

   python -m venv .venv
   .venv\Scripts\activate.bat
   .\bin\windowsMicrosoftStudioTools2022.cmd
   python bin\tornadovm-installer --jdk jdk21 --backend=opencl 
   setvars.cmd



B) Manual installation

In this case, you need to install all dependencies (Java, maven and cmake) before building the project.
Once all dependencies are provided, execute the following commands:


.. code:: bash

   python -m venv .venv
   .venv\Scripts\activate.bat
   .\bin\windowsMicrosoftStudioTools2022.cmd
   nmake /f Makefile.mak jdk21 BACKEND=opencl,ptx
   setvars.cmd


6. Check the installation
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   # list the accelerator devices that are reachable from TornadoVM
   tornado --devices

   # run unit tests
   tornado-test -V

   ## run specific examples (e.g., NBody)
   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody



.. _installation_mali:

TornadoVM on ARM Mali GPUs
====================================

1. Installation
~~~~~~~~~~~~~~~~~

The installation of TornadoVM to run on ARM Mali GPUs requires JDK21 with GraalVM.
See the :ref:`installation_graalvm` for details about the installation.

The OpenCL driver for Mali GPUs on Linux that has been tested is:

-  OpenCL C 2.0 ``v1.r9p0-01rel0.37c12a13c46b4c2d9d736e0d5ace2e5e``:
   `link <https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel>`__

2. Testing on ARM MALI GPUs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We have tested TornadoVM on the following ARM Mali GPUs:

-  Mali-G71, which implements the Bifrost architecture:
   `link <https://developer.arm.com/ip-products/graphics-and-multimedia/mali-gpus/mali-g71-gpu>`__

Some of the unittests in TornadoVM run with ``double`` data types.
To enable double support, TornadoVM includes the following extension in the generated OpenCL code:

.. code:: c

   cl_khr_fp64

However, this extension is not available on Bifrost GPUs.

The rest of the unittests should pass.


Known issues on Linux
===============================

For Ubuntu >= 16.04, install the package ``ocl-icd-opencl-dev``

In Ubuntu >= 16.04 CMake can cause the following error:

``Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2").``

Then the following package should be installed:

.. code:: bash

   $ apt-get install ocl-icd-opencl-dev


IDE Code Formatter
====================

Using Eclipse and Netbeans
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The code formatter in Eclipse is automatically applied after generating the setting files.

.. code-block:: bash

  $ mvn eclipse:eclipse
  $ python scripts/eclipseSetup.py


For Netbeans, the Eclipse Formatter Plugin is needed.

Using IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


Install plugins:

- Eclipse Code Formatter
- Save Actions

Then :

1. Open File > Settings > Eclipse Code Formatter.
2. Check the ``Use the Eclipse code`` formatter radio button.
3. Set the Eclipse Java Formatter config file to the XML file stored in ``/scripts/templates/eclise-settings/Tornado.xml``.
4. Set the Java formatter profile in Tornado.


TornadoVM Maven Projects
================================


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
         <version>1.0.4</version>
      </dependency>

      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-matrices</artifactId>
         <version>1.0.4</version>
      </dependency>
   </dependencies>



Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled.

Versions available
========================

* 1.0.4
* 1.0.3
* 1.0.2
* 1.0.1
* 1.0
* 0.15.2
* 0.15.1
* 0.15
* 0.14.1
* 0.14
* 0.13
* 0.12
* 0.11
* 0.10
* 0.9
* 0.8
* 0.7
* 0.6
* 0.5
* 0.4
* 0.3
* 0.2
* 0.1.0
