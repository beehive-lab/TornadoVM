Installation & Configuration
=============================

Pre-requisites
###############

* Maven Version >= 3.6.3
* CMake >= 3.6
* At least one of following drivers:
      * OpenCL drivers: GPUs and CPUs >= 2.1, FPGAs >= 1.0
      * NVIDIA drivers and CUDA SDK 10.0+
      * Intel drivers and Level-Zero >= 1.2
* GCC or clang/LLVM (GCC >= 9.0)
* Python (>= 3.0)

For Mac OS X users: the OpenCL support for your Apple model can be confirmed `here <https://support.apple.com/en-gb/HT202823>`_.

Supported Platforms
###############

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
| Mac OS X Mojave 10.14.6   | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Mac OS X Catalina 10.15.3 | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Mac OS X Big Sur 11.5.1   | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Apple M1                  | OpenCL for GPUs and CPUs >= 1.2                           | Not supported   | Not supported        |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+
| Windows 10/11             | OpenCL for GPUs and CPUs >= 2.1, FPGAs not tested         | CUDA 10.0+      | Not supported/tested |
+---------------------------+-----------------------------------------------------------+-----------------+----------------------+

Note: The SPIR-V backend of TornadoVM is only supported for Linux OS.
Besides, the SPIR-V backend with Level Zero runs on Intel HD Graphics (integrated GPUs), and Intel ARC GPUs. 

.. _installation:

Installation
###############

TornadoVM can be built with three compiler backends and is able to generate OpenCL, PTX and SPIR-V code. 
There are two ways to install TornadoVM: 

A) Automatic Installation
--------------------------

The ``tornadoVMInstaller.sh`` script provided in this repository will compile/download OpenJDK, ``cmake`` and it will build TornadoVM.
This installation script has been tested on Linux and OSx.
Additionally, this installation type will automatically trigger all dependencies, therefore it is recommended if users only need to invoke TornadoVM as a library.

.. code-block:: bash
   
    $ ./scripts/tornadovm-installer 
      usage: tornadovm-installer [-h] [--version] [--jdk JDK] [--backend BACKEND] [--listJDKs] [--javaHome JAVAHOME]

      TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

      optional arguments:
       -h, --help           show this help message and exit
       --version            Print version of TornadoVM
       --jdk JDK            Select one of the supported JDKs. Use --listJDKs option to see all supported ones.
       --backend BACKEND    Select the backend to install: { opencl, ptx, spirv }
       --listJDKs           List all JDK supported versions
       --javaHome JAVAHOME  Use a JDK from a user directory


**NOTE** Select the desired backend with the ``--backend`` option:
  * ``opencl``: Enables the OpenCL backend (it requires OpenCL drivers)
  * ``ptx``: Enables the PTX backend (it requires NVIDIA CUDA drivers)
  * ``spirv``: Enables the SPIRV backend (it requires Intel Level Zero drivers)


For example, to build TornadoVM with GraalVM and JDK 17:

.. code-block:: bash 

  ## Install with Graal for JDK 17 using PTX, OpenCL and SPIRV backends
  ./scripts/tornadovm-installer --jdk graalvm-jdk-17  --backend opencl,ptx,spirv


To build TornadoVM with Red Hat Mandrel JDK 17 with OpenCL and PTX backends:

.. code-block:: bash 

  ./scripts/tornadovmInstaller.sh --jdk mandrel-jdk-17 --backend opencl,ptx


After the installation, the scripts create a directory with the TornadoVM SDK. The directory also includes a source file with all variables needed to start using TornadoVM. 
After the script finished the installation, set the ``env`` variables needed by using:

.. code-block:: bash  

  $ source source.sh


B) Manual Installation
--------------------------

TornadoVM can be executed with the following configurations:

Linux
~~~~~~~~

- TornadoVM with GraalVM for Linux and OSx (JDK 17 and JDK 21): see the installation guide here: :ref:`installation_graalvm`.
- TornadoVM with JDK17+ (e.g. OpenJDK [17-21], Red Hat Mandrel, Amazon Corretto): see the installation guide: :ref:`installation_jdk17plus`.

Windows
~~~~~~~~~~

To run TornadoVM on **Windows 10/11 OS**, install TornadoVM with GraalVM. More information here: :ref:`installation_windows`.


ARM Mali GPUs and Linux
~~~~~~~~~~~~~~~~~~~

To run TornadoVM on ARM Mali, install TornadoVM with GraalVM and JDK 11. More information here: :ref:`installation_mali`.


Compilation with Maven 
~~~~~~~~~~~~~~~~~~~
This installation type requires users to manually install all the dependencies, therefore it is recommended for developing TornadoVM.
At least one backend must be specified at build time to the ``make`` command:

.. code-block:: bash 

  ## Choose the desired backend
  $ make BACKENDS=opencl,ptx,spirv


.. _installation_graalvm:

Installation for GraalVM for JDK 21.0.0 and GraalVM for JDK 17.0.8 on Linux and OSx
-----------------------------------------------------------------------------------

1. Download GraalVM for JDK 17.0.8 or JDK 21.0.0
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

GraalVM **Community Edition** builds are available to download at:

`https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.0 <https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-21.0.0>`_.
`https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-17.0.8 <https://github.com/graalvm/graalvm-ce-builds/releases/tag/jdk-17.0.8>`_.

The examples below show how to download and extract GraalVM for JDK 21.0.0 and GraalVM for JDK 17.0.8 for Linux.

-  Example for GraalVM for JDK 17 Community 17.0.8:

.. code:: bash

   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz
   $ tar -xf graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz

-  Example for GraalVM for JDK 21 Community 21.0.0:

.. code:: bash

   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0_linux-x64_bin.tar.gz
   $ tar -xf graalvm-community-jdk-21.0.0_linux-x64_bin.tar.gz

The Java binary will be found in the `graalvm-jdk-{JDK_VERSION}-23.0.1`` directory. This directory is used as the JAVA_HOME (See step 2).

For OSX:

-  Example for GraalVM for JDK 17 Community 17.0.8:

.. code:: bash

   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz

-  Example for GraalVM for JDK 21 Community 21.0.0:

.. code:: bash
   $ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.0/graalvm-community-jdk-21.0.0.0.2_macos-x64_bin.tar.gz

then ``untar`` it to the OSX standard JDK location
``/Library/Java/JavaVirtualMachines/`` or to a folder of your choice.

2. Download TornadoVM
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

For OSX: the exports above may be added to ``~/.profile``

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

For OSX:
^^^^^^^^

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

To build with GraalVM and JDK 17 and JDK 21:

.. code:: bash

   $ make graalvm-jdk-17-plus BACKEND={ptx,opencl}

and done!!


.. _installation_jdk11plus:

TornadoVM for JDK 17+ on Linux and OSx
--------------------------------------


**DISCLAIMER:**
           
TornadoVM is based on the Graal compiler that depends on JVMCI (Java Virtual Machine Compiler Interface). Different JDKs come with different
versions of JVMCI. Therefore, the version of the Graal compiler that TornadoVM uses might not be compatible with the JVMCI version of some
JDKs. Below are listed the Java 17 JDK distributions against which TornadoVM has been tested, but compatibility is not guaranteed.

.. code:: bash

  ./scripts/tornadovm-installer --listJDKs
  jdk17            : Install TornadoVM with OpenJDK 17 (Oracle OpenJDK)
  jdk21            : Install TornadoVM with OpenJDK 21 (Oracle OpenJDK)
  graalvm-jdk-17   : Install TornadoVM with GraalVM and JDK 17 (GraalVM 23.1.0)
  graalvm-jdk-21   : Install TornadoVM with GraalVM and JDK 21 (GraalVM 23.1.0)
  corretto-jdk-17  : Install TornadoVM with Corretto JDK 17
  corretto-jdk-21  : Install TornadoVM with Corretto JDK 21
  mandrel-jdk-17   : Install TornadoVM with Mandrel 23.0.1 (JDK 17)
  mandrel-jdk-21   : Install TornadoVM with Mandrel 23.0.1 (JDK 21)
  microsoft-jdk-17 : Install TornadoVM with Microsoft JDK 17
  zulu-jdk-jdk-17  : Install TornadoVM with Azul Zulu JDK 17
  zulu-jdk-jdk-21  : Install TornadoVM with Azul Zulu JDK 21


1. Download a JDK 17+ distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- OpenJDK distributions are available to download at https://adoptium.net/. 
- Red Hat Mandrel releases are available at https://github.com/graalvm/mandrel/releases. 
- Amazon Coretto releases are available at https://aws.amazon.com/corretto/. 
- Microsoft OpenJDK releases are available at https://docs.microsoft.com/en-us/java/openjdk/download. Azul Zulu
- OpenJDK releases are available at `https://www.azul.com/downloads <https://www.azul.com/downloads/?package=jdk#download-openjdk>`__.

After downloading and extracting the JDK distribution, point your ``JAVA_HOME`` variable to the JDK root.

Example:

.. code:: bash

    $ wget https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz
    $ tar xf amazon-corretto-21-x64-linux-jdk.tar.gz
    $ export JAVA_HOME=$PWD/amazon-corretto-21-x64-linux

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
   export JAVA_HOME=<path to JDK17+> ## This path is produced in Step 1
   export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
   export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
   export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)

This file should be loaded once after opening the command prompt for the
setup of the required paths:

.. code:: bash

   $ source ./etc/sources.env

For OSX: the exports above may be added to ``~/.profile``

3. Install CMAKE (if cmake < 3.6)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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

For OSX:
^^^^^^^^

Install cmake:

.. code:: bash

   $ brew install cmake

then

.. code:: bash

   export CMAKE_ROOT=/usr/local

which can be added to ``~/.profile``

4. Compile TornadoVM for JDK 17+
~~~~~~~~~~~~~~~~~~~~

.. code:: bash

   $ cd ~/tornadovm
   $ . etc/sources.env

To build with a distribution of JDK 17+

.. code:: bash

   $ make jdk-17-plus BACKEND={ptx,opencl}

and done!!

Running with JDK 17+
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

TornadoVM for Windows 10/11 using GraalVM 
---------------------------------------------------------------

**[DISCLAIMER] Please, notice that, although TornadoVM can run on Windows10 via MSys2, it is still experimental.**

1. Install prerequisites
~~~~~~~~~~~~


Maven
'''''

Download Apache Maven (at least 3.9.0) from the `official site <https://maven.apache.org/download.cgi>`__, and extract it to any
location on your computer. Below it's assumed that Maven's home is ``C:/bin/``, but you can use any other directory. 

MSys2
'''''

1. Download the `MSys2 <https://www.msys2.org/>`__ installer from the official website and run it. You may choose any installation
   directory, below it will be referred as ``<MSYS2>``.

**IMPORTANT:** the only executable you should use as a terminal is ``<MSYS2>/mingw64.exe``.

2. Update MSys2 **system** packages. Start ``<MSYS2>/mingw64.exe`` and run the following command in the terminal:

.. code:: bash

   pacman -Syu

You might need to execute it several times until you see that no updates
found.

3. Update MSys2 **default** packages. In the terminal window of
   ``<MSYS2>/mingw64.exe`` run:

.. code:: bash

   pacman -Su

You might need to execute it several times until you see that no updates
found.

4. Install necessary tools to MSys2. In the terminal window of
   ``<MSYS2>/mingw64.exe`` run:

.. code:: bash

   pacman -S \
   mingw-w64-x86_64-make           \
   mingw-w64-x86_64-cmake          \
   mingw-w64-x86_64-gcc            \
   mingw-w64-x86_64-opencl-headers \
   mingw-w64-x86_64-opencl-icd \
   python make git

5. Create default Maven repository for MSys2 user:

.. code:: bash

   cd ~
   mkdir .m2

6. Create default content for the file ``~/.m2/settings.xml``:

.. code:: bash

   cat > ~/.m2/settings.xml << EOF
   <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
       <localRepository/>
       <interactiveMode/>
       <offline/>
       <pluginGroups/>
       <servers/>
       <mirrors/>
       <proxies/>
       <profiles/>
       <activeProfiles/>
   </settings>
   EOF

7. Create file ``mvn`` in ``<MSYS2>/mingw64/bin`` with any text editor
   (e.g., `Visual Studio
   Code <https://code.visualstudio.com/download>`__) with the following
   content:

.. code:: bash

   #!/usr/bin/env bash
   C:/<path-to-your-maven-install>/bin/mvn.cmd --settings ${HOME}/.m2/settings.xml "$@"

Example:

.. code:: bash

   #!/usr/bin/env bash
   C:/bin/apache-maven-3.9.1-bin/apache-maven-3.9.1/bin/mvn.cmd --settings ${HOME}/.m2/settings.xml "$@"

You only need to change the path to your maven installation in Windows.

2. Download TornadoVM
~~~~~~~~~~~~


Clone the latest TornadoVM source code from the GitHub `repository <https://github.com/beehive-lab/TornadoVM>`__ using ``<MSYS2>/mingw64.exe``:

.. code:: bash

   cd D:/MyProjects
   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM

We will refer hereafter the directory with TornadoVM sources as
``<TornadoVM>``.

3. Download GraalVM for JDK 21 Community 21.0.0
~~~~~~~~~~~~


TornadoVM can run with JDK 17 and 21. Visit `GraalVM for JDK17 and JDK21 <https://www.graalvm.org/downloads/>`__
and download either of the following builds:

-  `Download for JDK 17 <https://download.oracle.com/graalvm/17/latest/graalvm-jdk-17_windows-x64_bin.zip>`__
-  `Download for JDK 21 <https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_windows-x64_bin.zip>`__

Extract the downloaded file to any directory.

4. Install the NVIDIA drivers and CUDA SDK
~~~~~~~~~~~~


A) CUDA Driver
              
Most Windows systems come with the NVIDIA drivers pre-installed. You can check your installation and the latest drivers available by using
`NVIDIA GEFORCE Experience <https://www.nvidia.com/it-it/geforce/geforce-experience/download/>`__ tool.

Alternatively, all NVIDIA drivers can be found here: `NVIDIA Driver Downloads <https://www.nvidia.com/Download/index.aspx>`__.

B) OpenCL and NVIDIA PTX
''''''''''''''''''''''''

If you plan to only use the OpenCL backend from TornadoVM, then you only
need the NVIDIA driver from the previous step.

If you want to also use the PTX backend, then you need to install the
NVIDIA CUDA Toolkit.

-  Complete CUDA Toolkit from `CUDA Toolkit
   Downloads <https://developer.nvidia.com/cuda-downloads?target_os=Windows&target_arch=x86_64>`__.

It is important to make sure that the GPU drivers are included with the CUDA Toolkit, so you may avoid downloading drivers separately. 
The only thing to note is that the GPU driver you are currently using should be of the same or higher version than the one shipped with CUDA Toolkit. 
Thus, if you have the driver already installed, make sure that the version required by the CUDA SDK is same or higher, otherwise update the GPU driver during toolkit installation. 
Note, that NSight, BLAST libs and Visual Studio integration are irrelevant for TornadoVM builds, you just need the CUDA SDK - so you may skip installing them.

5. Configure the TornadoVM build: setting ENV variables
~~~~~~~~~~~~


Using any text editor create file ``<TornadoVM>/etc/sources.env`` with
the following content:

.. code:: bash

   #!/bin/bash

   # UPDATE PATH TO ACTUAL LOCATION OF THE JDK OR GRAAL 
   export JAVA_HOME="C:\Users\jjfum\bin\jvms\graalvm-ce-java17-windows-amd64-22.3.2\graalvm-ce-java17-22.3.2"


   ## NEXT TWO LINES NECESSARY TO BUILD PTX (NVIDIA CUDA) BACKEND
   ## COMMENT THEM OUT OR JUST IGNORE IF YOU ARE NOT INTERESTED IN PTX BUILD
   ## OTHERWISE UPDATE 'CUDA_PATH' WITH ACTUAL VALUE (REMEMBER OF UNIX_STYLE SLASHES AND SPACES!!!)
   export CUDA_PATH="C:/Program Files/NVIDIA GPU Computing Toolkit/CUDA/v11.6"
   export PTX_LDFLAGS=-L\"$CUDA_PATH/lib/x64\"

   # LEAVE THE REST OF FILE 'AS IS'
   # DON'T ALTER!
   export PATH=$PWD/bin/bin:$PATH               ## This directory will be automatically generated during Tornado compilation
   export TORNADO_SDK=$PWD/bin/sdk              ## This directory will be automatically generated during Tornado compilation
   CMAKE_FILE=$(where cmake | head -n 1)
   export CMAKE_ROOT=${CMAKE_FILE%\\*\\*}

There are only 2 places you should adjust:

1. ``JAVA_HOME`` path that points to your Graal installation
2. ``CUDA_PATH`` pointing to your NVIDIA GPU Computing Toolkit (CUDA) -
   this one is necessary only for builds with PTX backend.

6. Compile TornadoVM
~~~~~~~~~~~~

Start ``<MSYS2>/mingw64.exe`` terminal, navigate to the ``<TornadoVM>``
directory, and build TornadoVM as follows:

.. code:: bash

   cd D:/MyProjects/TornadoVM
   source etc/sources.env
   make graal-jdk-11-plus BACKEND=ptx,opencl

The ``BACKEND`` parameter has to be a comma-separated list of ``ptx`` and ``opencl`` options. You may build ``ptx`` only when NVIDIA GPU
Computing Toolkit (CUDA) is installed.

7. Check the installation
~~~~~~~~~~~~

Don't close ``<MSYS2>/mingw64.exe`` after the build. Run the following command to see that TornadoVM is working:

.. code:: bash

   tornado --devices

You should see a list of OpenCL and/or CUDA devices available on your system.

Now try to run a simple test. To run examples with Graal JDK 11, TornadoVM uses modules:

.. code:: bash

   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D --params="512"

To run individual tests:

.. code:: bash

   tornado --jvm="-Dtornado.unittests.verbose=True -Xmx6g"  -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner --params="uk.ac.manchester.tornado.unittests.arrays.TestArrays"

To run all unit-tests:

.. code:: bash

   make tests


.. _installation_mali:

TornadoVM on ARM Mali GPUs
---------------------------------------

Installation
^^^^^^^^^^^^

The installation of TornadoVM to run on ARM Mali GPUs requires JDK11 with GraalVM. 
See the :ref:`installation_graalvm` for details about the installation.

The OpenCL driver for Mali GPUs on Linux that has been tested is:

-  OpenCL C 2.0 ``v1.r9p0-01rel0.37c12a13c46b4c2d9d736e0d5ace2e5e``:
   `link <https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel>`__

Testing on ARM MALI GPUs
^^^^^^^

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
---------------------------

For Ubuntu >= 16.04, install the package ``ocl-icd-opencl-dev``
'''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''

In Ubuntu >= 16.04 CMake can cause the following error: 

``Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2").``

Then the following package should be installed:

.. code:: bash

   $ apt-get install ocl-icd-opencl-dev



Known issues on Windows
---------------------------

1. If you already have MSys2 installed and heavily customized you may
   experience issues with build or tests. We are suggesting to start
   with fresh MSys2 installation in this case and follow the
   instructions above. Most notably, make sure that you have no
   ``mingw-w64-x86_64-python`` installed - it prevents Python scripts
   that execute tests from running. Also, make sure that you have
   updated all GCC / Make / CMake packages mentioned.
2. If you see no output from ``tornado --devices`` this may be either of
   2 reasons: - OpenCL / CUDA is misconfigured. Download any third-party
   tool for OpenCL / CUDA capabilities viewing and check that you can
   see your devices there. Sometimes order of installation of different
   OpenCL drivers matters - Intel OpenCL SDK may shadow NVIDIA OpenCL
   and alike. - You build native code of the library using wrong
   compiler, most probably you ran ``<MSYS2>/msys2.exe`` terminal
   instead of ``<MSYS2>/mingw64.exe`` . Please re-try with correct
   terminal (and therefore GCC) version.
3. If you see JVM crashes or ``UnsatisfiedLinkError`` or some
   ``Error initializing DLL`` during ``tornado --devices`` execution
   than it's definitely due to wrong GCC (and hence terminal) version
   used during build.



IDE Code Formatter
------------------------------------

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
1. Open File > Settings > Eclipse Code Formatter
2. Check the ``Use the Eclipse code`` formatter radio button
3. Set the Eclipse Java Formatter config file to the XML file stored in ``/scripts/templates/eclise-settings/Tornado.xml``.
4. Set the Java formatter profile in Tornado


TornadoVM Maven Projects
------------------------------------


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
         <version>0.15.2</version>
      </dependency>

      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-matrices</artifactId>
         <version>0.15.2</version>
      </dependency>
   </dependencies>



Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled.

Versions available
------------------------------------

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
