.. _developer-guidelines:

Developer Guidelines
====================

This guide covers how to build TornadoVM from source and configure your development environment (code formatting and check styles) to contribute to the project.


.. _build-from-source:

Building from Source
---------------------

Building from source is only needed if you want to contribute to the project, run the latest ``develop`` branch, or build a custom backend combination. If you just want to use TornadoVM as a library or application, see the :ref:`Quick Install <installation>` instead.

Pre-requisites
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* GCC >= 13.0 or LLVM/clang (Linux), Xcode >= 15 (macOS), Visual Studio Community 2022 (Windows 11 recommended)
* Python >= 3.6
* At least one of the following drivers/SDKs:

  * OpenCL: GPUs and CPUs >= 2.1
  * NVIDIA driver + CUDA Toolkit 10.0+ (12.0+ on Windows) for the **PTX** backend; CUDA Toolkit 13.0+ for the **CUDA** backend
  * Intel compute-runtime and/or GPU drivers (OpenCL), and Level-Zero >= 1.2

Supported Platforms
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. csv-table::
   :header: "OS", "OpenCL Backend", "PTX Backend", "CUDA Backend", "SPIR-V Backend", "Metal Backend"
   :widths: 22, 28, 12, 12, 13, 13

   "CentOS / Fedora / Ubuntu / Pop!_OS / Suse", "OpenCL for GPUs and CPUs >= 2.1", "CUDA 10.0+", "CUDA 13.0+", "Level-Zero >= 1.2", "Not supported"
   "Apple M1/M2/M3/M4/M5", "OpenCL for GPUs and CPUs >= 1.2", "Not supported", "Not supported", "Not supported", "macOS (Apple Silicon)"
   "Windows 10/11 (native/WSL)", "OpenCL for GPUs and CPUs >= 2.1", "CUDA 12.0+", "CUDA 13.0+", "Level-Zero >= 1.2", "Not supported"

**Note:** The SPIR-V backend runs on Linux and Windows, either via Level Zero (Intel HD Graphics and Intel ARC GPUs) or via OpenCL (any device with OpenCL >= 2.1).

For Intel-based macOS users, OpenCL support can be confirmed `here <https://support.apple.com/en-gb/HT202823>`_.

Build with the ``tornadovm-installer`` script
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``tornadovm-installer`` script downloads/builds ``OpenJDK``, ``CMake`` and ``Maven``, then builds TornadoVM. It works on Linux, macOS and Windows and is the recommended way to build from source.

.. code-block:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM
   ./bin/tornadovm-installer
   # Select the backend(s) to install: 1. opencl  2. spirv  3. ptx  4. metal  5. cuda
   # (you can select more than one, comma-separated, e.g. 1, 2, 3)
   source setvars.sh        # Linux / macOS
   # setvars.cmd             # Windows

.. code-block:: bash

   $ ./bin/tornadovm-installer --help
     usage: tornadovm-installer [-h] [--jdk JDK] [--backend BACKEND] [--version] [--listJDKs] [--polyglot] [--mvn_single_threaded] [--auto-deps]

     options:
       --jdk JDK             Specify a JDK to install (e.g., 'jdk21', 'graal-jdk-21'). Use --listJDKs to see all options.
       --backend BACKEND     Select the backend(s) to install: { opencl, ptx, cuda, spirv, metal }
       --listJDKs            List supported JDKs
       --polyglot            Enable Truffle Interoperability with GraalVM
       --mvn_single_threaded  Run Maven in single-threaded mode
       --auto-deps           Automatically download any missing dependencies

.. _installation_graalvm:

To build with GraalVM instead of stock OpenJDK (needed for :ref:`truffle-languages`), pass a Graal JDK keyword:

.. code-block:: bash

   ./bin/tornadovm-installer --jdk graal-jdk-21 --backend opencl

Build with the Makefile
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you already have a JDK, Maven and CMake on your ``PATH``, you can build directly with ``make``:

.. code-block:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git
   cd TornadoVM
   make BACKEND=opencl,ptx,spirv   # build only the backends you need { opencl, ptx, cuda, spirv, metal }
   source setvars.sh

To rebuild with a different backend selection later:

.. code-block:: bash

   source setvars.sh
   make BACKEND=opencl

.. note::

   For NVIDIA GPUs, TornadoVM provides two backends: **PTX** (emits PTX assembly directly) and **CUDA** (generates CUDA C, compiled to PTX via NVRTC). Build the CUDA backend on its own with ``make BACKEND=cuda``. See :ref:`hybrid-api` for native library integration (cuBLAS, cuFFT, cuDNN) on top of the CUDA backend.

Windows builds require the Visual Studio developer tools and a Python virtual environment:

.. code-block:: bash

   .\bin\windowsMicrosoftStudioTools2022.cmd
   python -m venv .venv
   .venv\Scripts\activate.bat
   python bin\tornadovm-installer
   setvars.cmd

Verify the Installation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # list the accelerator devices reachable from TornadoVM
   tornado --devices

   # run the unit test suite
   tornado-test -V

   # run a specific example (e.g., NBody)
   tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody

See :ref:`examples` for more ways to run examples, benchmarks and individual unit tests.

Platform-Specific Notes
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. _installation_windows:

Windows 10/11
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**TornadoVM on native Windows is experimental.** Install Visual Studio Community 2022 (with the MSVC C++ x86/64 build tools workload, including the C++ ATL packages) and Python 3, then follow the "Build with the Makefile" steps above. NVIDIA users only need the CUDA Toolkit if they want the PTX or CUDA backend; the OpenCL backend needs just the NVIDIA driver.

.. _installation_windows_wsl:

Windows Subsystem for Linux (WSL)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

TornadoVM also builds inside WSL (Ubuntu). Install the NVIDIA CUDA Toolkit for WSL and/or the `Intel compute-runtime <https://github.com/intel/compute-runtime/releases>`_ following the vendor instructions, then build as on Linux:

.. code-block:: bash

   git clone https://github.com/beehive-lab/TornadoVM.git tornado
   cd tornado
   ./bin/tornadovm-installer
   source setvars.sh

.. _installation_mali:

ARM Mali GPUs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Requires JDK 21 with GraalVM and an OpenCL 2.0+ driver for Mali (tested with the `Bifrost kernel driver <https://developer.arm.com/tools-and-software/graphics-and-gaming/mali-drivers/bifrost-kernel>`_, e.g. on Mali-G71). Note that the ``cl_khr_fp64`` extension (double-precision) is not available on Bifrost GPUs, so ``double``-typed unit tests are not expected to pass there.

.. _installation_riscv:

RISC-V RVV 1.0 (experimental)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Tested on Linux Bianbu OS 2.0/2.1 (Bananapi F3, Sipeed Lichee PI 3A). The native OpenCL build needs a small patch (the ``cmake-maven`` plugin is unsupported on RISC-V), applied automatically by the `tornadovm-riscv-patch <https://github.com/beehive-lab/tornadovm-riscv-patch>`_ scripts:

.. code-block:: bash

   sudo apt-get install clinfo gcc g++
   sudo ln -s libOpenCL.so.1 libOpenCL.so
   python -m venv /path/to/venv && source /path/to/venv/bin/activate

   git clone https://github.com/beehive-lab/tornadovm-riscv-patch.git
   bash tornadovm-riscv-patch/apply-riscv-patch-opencl.sh   # OpenCL only
   # bash tornadovm-riscv-patch/apply-riscv-patch-spirv.sh  # OpenCL + SPIR-V

   source setvars.sh
   tornado --devices

Known Issues on Linux
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Ubuntu >= 16.04 needs the ``ocl-icd-opencl-dev`` package: ``sudo apt-get install ocl-icd-opencl-dev``


IDE Code Formatter
-------------------

Eclipse / NetBeans
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

  mvn eclipse:eclipse
  python scripts/eclipseSetup.py

(NetBeans needs the Eclipse Formatter Plugin.)

IntelliJ (quick setup)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Install the **Eclipse Code Formatter** and **Save Actions** plugins, then under *File > Settings > Eclipse Code Formatter*, enable "Use the Eclipse code formatter" and point it at ``/scripts/templates/eclipse-settings/Tornadovm_eclipse_formatter.xml``.

For the full IntelliJ setup (Checkstyle-IDEA, EditorConfig, and more), see the detailed steps below.


IntelliJ Configurations
-----------------------


1. Enable Eclipse Code Formatter for IntelliJ
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   - Install the Eclipse Code Formatter plugin from the JetBrains plugin repository:

     `Eclipse Code Formatter Plugin <https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter>`_

   - After installation, navigate to **File > Settings > Adapter for Eclipse Code Formatter**.
   - Select the option **“Use the Eclipse code formatter”**.
   - Load the TornadoVM code formatter from this path ``scripts/templates/eclipse-settings/Tornadovm_eclipse_formatter.xml`` using the selector **Eclipse Formatter Config** > **Eclipse workspace/project folder**.
   - Click **Apply** to save the settings.

2. Enable IntelliJ Code Formatter
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   - Go to Menu Settings → Editor → Code Style.
   - Import the code style scheme by following these steps:
     - Click on the cog icon in the top right corner of the Code Style settings ("Schema" field).
     - Choose "Import Schema"
     - Import the file located at: ``scripts/templates/intellij-settings/Tornadovm_intellij_formatter.xml``
     - Click **Apply**.

3. Checkstyle-IDEA Plugin
~~~~~~~~~~~~~~~~~~~~~~~~~
This plugin provides both real-time and on-demand scanning of Java files with Checkstyle from within IDEA.

   - Install the Checkstyle-IDEA plugin by going to **File > Settings** (Windows/Linux) or **IntelliJ IDEA > Preferences…** (macOS).
   - Select **Plugins**, press **Browse Repository**, and find the plugin `CheckStyle-IDEA <https://plugins.jetbrains.com/plugin/1065-checkstyle-idea>`_
   - Restart the IDE to complete the installation.
   - Click **File > Settings > Tools > Checkstyle**.
   - Set the **Scan Scope** to "Only Java sources (including tests)" to run Checkstyle for test source codes as well.
   - Click the plus sign under **Configuration File**.
   - Enter a description (e.g., "TornadoVM Checkstyle").
   - Select **Use a local Checkstyle file**.
   - Use the Checkstyle configuration file found at ``tornado-assembly/src/etc/checkstyle.xml``.
   - Click **Next > Finish**.
   - Mark the newly imported check configuration as **Active** and click **Apply**.

4. EditorConfig
~~~~~~~~~~~~~~~
   We use JetBrains' EditorConfig. This allows us to import and export code style settings easily.

- Copy the EditorConfig file to the root of your project:

.. code:: bash

  cd $TORNADO_ROOT
  cp scripts/templates/intellij-settings/.editorconfig .


- In IntelliJ IDEA, navigate to Menu Settings → Editor → Code Style.
- At the bottom of the settings window, check the box "Use EditorConfig".

1. Save Actions
~~~~~~~~~~~~~~~

Install the **Save Actions** Plugin. This allows you  to define post-save actions, including code formatting.

- To enable the auto-formatter with save-actions, follow these steps:
  - Go to **Settings > Other Settings > Save Actions**.
  - Mark the following options: Activate save actions on save, Activate save actions in shortcut and Reformat file.



Pre-commit hooks
----------------


Install pre-commit hooks
~~~~~~~~~~~~~~~~~~~~~~~~

Pre-commit docs: `<https://pre-commit.com/>_`


.. code:: bash

  pip install pre-commit
  pre-commit install


Every time there is a commit in the TornadoVM repo, the pre-commit will pass some checks (including code check style and code formatter).
If all checks are correct, then the commit will be done.

To guarantee the commit, pass the check style before:

.. code:: bash

  make checkstyle     ### If there are errors regarding the code formatting, fix it at this stage.
