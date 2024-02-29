.. _changelog:

TornadoVM Changelog
===================

This file summarizes the new features and major changes for each *TornadoVM* version.

TornadoVM 1.0.2
----------------
29/02/2024

Improvements 
~~~~~~~~~~~~~~~~~~

- `#323 <https://github.com/beehive-lab/TornadoVM/pull/323>`_: Set Accelerator Memory Limit per Execution Plan at the API level
- `#328 <https://github.com/beehive-lab/TornadoVM/pull/328>`_: Javadoc API to run with concurrent devices and memory limits
- `#340 <https://github.com/beehive-lab/TornadoVM/pull/340>`_: New API calls to enable ``threadInfo`` and ``printKernel`` from the Execution Plan API. 
- `#334 <https://github.com/beehive-lab/TornadoVM/pull/334>`_: Dynamically enable/disable profiler after first run

Compatibility
~~~~~~~~~~~~~~~~~~

- `#337 <https://github.com/beehive-lab/TornadoVM/pull/337>`_ : Initial support for Graal and JDK 21.0.2

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#322 <https://github.com/beehive-lab/TornadoVM/pull/322>`_: Fix duplicate thread-info debug message when the debug option is also enabled. 
- `#325 <https://github.com/beehive-lab/TornadoVM/pull/325>`_: Set/Get accesses for the ``MatrixVectorFloat4`` type fixed
- `#326 <https://github.com/beehive-lab/TornadoVM/pull/326>`_: Fix installation script for running with Python >= 3.12
- `#327 <https://github.com/beehive-lab/TornadoVM/pull/327>`_: Fix Memory Limits for all supported Panama off-heap types.
- `#329 <https://github.com/beehive-lab/TornadoVM/pull/329>`_: Fix timers for the dynamic reconfiguration policies 
- `#330 <https://github.com/beehive-lab/TornadoVM/pull/330>`_: Fix the profiler logs when silent mode is enabled
- `#332 <https://github.com/beehive-lab/TornadoVM/pull/332>`_: Fix Batch processing when having multiple task-graphs in a single execution plan. 


TornadoVM 1.0.1
----------------
30/01/2024

Improvements
~~~~~~~~~~~~~~~~~~

- `#305 <https://github.com/beehive-lab/TornadoVM/pull/305>`_: Under-demand data transfer for custom data ranges.
- `#313 <https://github.com/beehive-lab/TornadoVM/pull/313>`_: Initial support for Half-Precision (FP16) data types.
- `#311 <https://github.com/beehive-lab/TornadoVM/pull/311>`_: Enable Multi-Task Multiple Device (MTMD) model from the ``TornadoExecutionPlan`` API:
- `#315 <https://github.com/beehive-lab/TornadoVM/pull/315>`_: Math ``Ceil`` function added


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~~~

- `#294 <https://github.com/beehive-lab/TornadoVM/pull/294>`_: Separation of the OpenCL Headers from the code base.
- `#297 <https://github.com/beehive-lab/TornadoVM/pull/297>`_: Separation of the LevelZero JNI API in a separate repository.
- `#301 <https://github.com/beehive-lab/TornadoVM/pull/301>`_: Temurin configuration supported.
- `#304 <https://github.com/beehive-lab/TornadoVM/pull/304>`_: Refactor of the common phases for the JIT compiler.
- `#316 <https://github.com/beehive-lab/TornadoVM/pull/316>`_: Beehive SPIR-V Toolkit version updated.

Bug Fixes
~~~~~~~~~~~~~~~~~~

- `#298 <https://github.com/beehive-lab/TornadoVM/pull/298>`_: OpenCL Codegen fixed open-close brackets.
- `#300 <https://github.com/beehive-lab/TornadoVM/pull/300>`_: Python Dependencies fixed for AWS
- `#308 <https://github.com/beehive-lab/TornadoVM/pull/308>`_: Runtime check for Grid-Scheduler names
- `#309 <https://github.com/beehive-lab/TornadoVM/pull/309>`_: Fix check-style to support STR templates
- `#314 <https://github.com/beehive-lab/TornadoVM/pull/314>`_: emit Vector16 Capability for 16-width vectors


TornadoVM 1.0
----------------
05/12/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Brand-new API for allocating off-heap objects and array collections using the Panama Memory Segment API.
  - New Arrays, Matrix and Vector type objects are allocated using the Panama API.
  - Migration of existing applications to use the new Panama-based types: https://tornadovm.readthedocs.io/en/latest/offheap-types.html
- Handling of the TornadoVM's internal bytecode improved to avoid write-only copies from host to device.
- ``cospi`` and ``sinpi`` math operations supported for OpenCL, PTX and SPIR-V.
- Vector 16 data types supported for ``float``, ``double`` and ``int``.
- Support for Mesa's ``rusticl``.
- Device default ordering improved based on maximum thread size.
- Move all the installation and configuration scripts from Bash to Python.
- The installation process has been improved for Linux and OSx with M1/M2 chips.
- Documentation improved.
- Add profiling information for the testing scripts.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

- Integration with the Graal 23.1.0 JIT Compiler.
- Integration with OpenJDK 21.
- Integration with Truffle Languages (Python, Ruby and Javascript) using Graal 23.1.0.
- TornadoVM API Refactored.
- Backport bug-fixes for branch using OpenJDK 17: ``master-jdk17``


Bug fixes:
~~~~~~~~~~~~~~~~~

- Multiple SPIR-V Devices fixed.
- Runtime Exception when no SPIR-V devices are present.
- Issue with the kernel context API when invoking multiple kernels fixed.
- MTMD mode is fixed when running multiple backends on the same device.
- ``long`` type as a constant parameter for a kernel fixed.
- FPGA Compilation and Execution fixed for AWS and Xilinx devices.
- Batch processing fixed for different data types of the same size.



TornadoVM 0.15.2
----------------
26/07/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Initial Support for Multi-Tasks on Multiple Devices (MTMD): This mode enables the execution of multiple independent tasks on more than one hardware accelerators. Documentation in link: https://tornadovm.readthedocs.io/en/latest/multi-device.html
- Support for trigonometric ``radian``, ``cospi`` and ``sinpi`` functions for the OpenCL/PTX and SPIR-V backends.
- Clean-up Java modules not being used and TornadoVM core classes refactored.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

- Initial integration with ComputeAorta (part of the Codeplay's oneAPI Construction Kit for RISC-V) to run on RISC-V with Vector Instructions (OpenCL backend) in emulation mode.
- Beehive SPIR-V Toolkit dependency updated.
- Tests for prebuilt SPIR-V kernels fixed to dispatch SPIR-V binaries through the Level Zero and OpenCL runtimes.
- Deprecated ``javac.py`` script removed.


Bug fixes:
~~~~~~~~~~~~~~~~~

- TornadoVM OpenCL Runtime throws an exception when the detected hardware does not support FP64.
- Fix the installer for the older Apple with the x86 architecture using AMD GPUs.
- Installer for ARM based systems fixed.
- Installer fixed for Microsoft WSL and NVIDIA GPUs.
- OpenCL code generator fixed to avoid using the reserved OpenCL keywords from Java function parameters.
- Dump profiler option fixed.



TornadoVM 0.15.1
----------------
15/05/2023

Improvements
~~~~~~~~~~~~~~~~~~

- Introduction of a device selection heuristic based on the computing capabilities of devices. TornadoVM selects, as the default device, the fastest device based on its computing capability.
- Optimisation of removing redundant data copies for Read-Only and Write-Only buffers from between the host (CPU) and the device (GPU) based on the Tornado Data Flow Graph.
- New installation script for TornadoVM.
- Option to dump the TornadoVM bytecodes for the unit tests.
- Full debug option improved. Use ``--fullDebug``.


Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~

- Integration and compatibility with the Graal 22.3.2 JIT Compiler.
- Improved compatibility with Apple M1 and Apple M2 through the OpenCL Backend.
- GraalVM/Truffle programs integration improved. Use ``--truffle`` in the ``tornado`` script to run guest programs with Truffle.
  Example: ``tornado --truffle python myProgram.py``
  Full documentation: https://tornadovm.readthedocs.io/en/latest/truffle-languages.html

Bug fixes:
~~~~~~~~~~~~~~~~~

- Documentation that resets the device's memory: https://github.com/beehive-lab/TornadoVM/blob/master/tornado-api/src/main/java/uk/ac/manchester/tornado/api/TornadoExecutionPlan.java#L282
- Append the Java ``CLASSPATH`` to the ``cp`` option from the ``tornado`` script.
- Dependency fixed for the ``cmake-maven`` plugin fixed for ARM-64 arch.
- Fixed the automatic installation for Apple M1/M2 and ARM-64 and NVIDIA Jetson nano computing systems.
- Integration with IGV fixed. Use the  ``--igv`` option for the ``tornado`` and ``tornado-test`` scripts.



TornadoVM 0.15
----------------
27/01/2023

Improvements
~~~~~~~~~~~~~~~~~~

- New TornadoVM API:

   - API refactoring (``TaskSchedule`` has been renamed to ``TaskGraph``)

   - Introduction of the Immutable ``TaskGraphs``

   - Introduction of the TornadoVM Execution Plans: (``TornadoExecutionPlan``)

   - The documentation of migration of existing TornadoVM applications to the new API can be found here: https://tornadovm.readthedocs.io/en/latest/programming.html#migration-to-tornadovm-v0-15

- Launch a new website https://tornadovm.readthedocs.io/en/latest/ for the documentation
- Improved documentation
- Initial support for Intel ARC discrete GPUs.
- Improved TornadoVM installer for Linux
- ImprovedTornadoVM launch script with optional parameters
- Support of large buffer allocations with Intel Level Zero. Use: ``tornado.spirv.levelzero.extended.memory=True``


Bug fixes:
~~~~~~~~~~~~~~~~~

- Vector and Matrix types
- TornadoVM Floating Replacement compiler phase fixed
- Fix ``CMAKE`` for Intel ARC GPUs
- Device query tool fixed for the PTX backend
- Documentation for Windows 11 fixed


TornadoVM 0.14.1
----------------

29/09/2022

Improvements
~~~~~~~~~~~~~~~~~~~~~

-  The tornado command is replaced from a Bash to a Python script.

   -  Use ``tornado --help`` to check the new options and examples.

-  Support of native tests for the SPIR-V backend.
-  Improvement of the OpenCL and PTX tests of the internal APIs.

Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~

-  Integration and compatibility with the Graal 22.2.0 JIT Compiler.
-  Compatibility with JDK 18 and JDK 19.
-  Compatibility with Apple M1 Pro using the OpenCL backend.

Bug Fixes
~~~~~~~~~~~~~~~~~~~~~

-  CUDA PTX generated header fixed to target NVIDIA 30xx GPUs and CUDA
   11.7.
-  The signature of generated PTX kernels fixed for NVIDIA driver >= 510
   and 30XX GPUs when using the TornadoVM Kernel API.
-  Tests of virtual OpenCL devices fixed.
-  Thread deployment information for the OpenCL backend is fixed.
-  ``TornadoVMRuntimeCI`` moved to ``TornadoVMRutimeInterface``.

TornadoVM 0.14
--------------

15/06/2022

New Features
~~~~~~~~~~~~

-  New device memory management for addressing the memory allocation
   limitations of OpenCL and enabling pinned memory of device buffers.

   -  The execution of task-schedules will still automatically
      allocate/deallocate memory every time a task-schedule is executed,
      unless lock/unlock functions are invoked explicitly at the
      task-schedule level.
   -  One heap per device has been replaced with a device buffer per
      input variable.
   -  A new API call has been added for releasing memory:
      ``unlockObjectFromMemory``
   -  A new API call has been added for locking objects to the device:
      ``lockObjectInMemory`` This requires the user to release memory by
      invoking ``unlockObjectFromMemory`` at the task-schedule level.

-  Enhanced Live Task migration by supporting multi-backend execution
   (PTX <-> OpenCL <-> SPIR-V).

.. _compatibilityintegration-1:

Compatibility/Integration
~~~~~~~~~~~~~~~~~~~~~~~~~

-  Integration with the Graal 22.1.0 JIT Compiler
-  JDK 8 deprecated
-  Azul Zulu JDK supported
-  OpenCL 2.1 as a default target for the OpenCL Backend
-  Single Docker Image for Intel XPU platforms, including the SPIR-V
   backend (using the Intel Integrated Graphics), and OpenCL (using the
   Intel Integrated Graphics, Intel CPU and Intel FPGA in emulation
   mode). Image:
   https://github.com/beehive-lab/docker-tornado#intel-integrated-graphics

Improvements/Bug Fixes
~~~~~~~~~~~~~~~~~~~~~~

-  ``SIGNUM`` Math Function included for all three backends.
-  SPIR-V optimizer enabled by default (3x reduce in binary size).
-  Extended Memory Mode enabled for the SPIR-V Backend via Level Zero.
-  Phi instructions fixed for the SPIR-V Backend.
-  SPIR-V Vector Select instructions fixed.
-  Duplicated IDs for Non-Inlined SPIR-V Functions fixed.
-  Refactoring of the TornadoVM Math Library.
-  FPGA Configuration files fixed.
-  Bitwise operations for OpenCL fixed.
-  Code Generation Times and Backend information are included in the
   profiling info.


TornadoVM 0.13
--------------

21/03/2022

-  Integration with JDK 17 and Graal 21.3.0

   -  JDK 11 is the default version and the support for the JDK 8 has
      been deprecated

-  Support for extended intrinsics regarding math operations
-  Native functions are enabled by default
-  Support for 2D arrays for PTX and SPIR-V backends:

   -  https://github.com/beehive-lab/TornadoVM/commit/2ef32ca97941410672720f9dfa15f0151ae2a1a1

-  Integer Test Move operation supported:

   -  https://github.com/beehive-lab/TornadoVM/pull/177

-  Improvements in the SPIR-V Backend:

   -  Experimental SPIR-V optimizer. Binary size reduction of up to 3x

      -  https://github.com/beehive-lab/TornadoVM/commit/394ca94dcdc3cb58d15a17046e1d22c6389b55b7

   -  Fix malloc functions for Level-Zero
   -  Support for pre-built SPIR-V binary modules using the TornadoVM
      runtime for OpenCL
   -  Performance increase due to cached buffers on GPUs by default
   -  Disassembler option for SPIR-V binary modules. Use
      ``--printKernel``

-  Improved Installation:

   -  Full automatic installer script integrated

-  Documentation about the installation for Windows 11
-  Refactoring and several bug fixes

   -  https://github.com/beehive-lab/TornadoVM/commit/57694186b42ec28b16066fb549ab8fcf9bff9753
   -  Vector types fixed:

      -  https://github.com/beehive-lab/TornadoVM/pull/181/files
      -  https://github.com/beehive-lab/TornadoVM/commit/004d61d6d26945b45ebff66641b60f90f00486be

   -  Fix AtomicInteger get for OpenCL:

      -  https://github.com/beehive-lab/TornadoVM/pull/177

-  Dependencies for Math3 and Lang3 updated


TornadoVM 0.12
--------------

17/11/2021

-  New backend: initial support for SPIR-V and Intel Level Zero

   -  Level-Zero dispatcher for SPIR-V integrated
   -  SPIR-V Code generator framework for Java

-  Benchmarking framework improved to accommodate all three backends
-  Driver metrics, such as kernel time and data transfers included in
   the benchmarking framework
-  TornadoVM profiler improved:

   -  Command line options added: ``--enableProfiler <silent|console>``
      and ``--dumpProfiler <jsonFile>``
   -  Logging improve for debugging purposes. JIT Compiler, JNI calls
      and code generation

-  New math intrinsincs operations supported
-  Several bug fixes:

   -  Duplicated barriers removed. TornadoVM BARRIER bytecode fixed when
      running multi-context
   -  Copy in when having multiple reductions fixed
   -  TornadoVM profiler fixed for multiple context switching (device
      switching)

-  Pretty printer for device information


TornadoVM 0.11
--------------

29/09/2021

-  TornadoVM JIT Compiler upgrade to work with Graal 21.2.0 and JDK 8
   with JVMCI 21.2.0
-  Refactoring of the Kernel Parallel API for Heterogeneous Programming:

   -  Methods ``getLocalGroupSize(index)`` and ``getGlobalGroupSize``
      moved to public fields to keep consistency with the rest of the
      thread properties within the ``KernelContext`` class.

      -  Changeset:
         https://github.com/beehive-lab/TornadoVM/commit/e1ebd66035d0722ca90eb0121c55dbc744840a74

-  Compiler update to register the global number of threads:
   https://github.com/beehive-lab/TornadoVM/pull/133/files
-  Simplification of the TornadoVM events handler:
   https://github.com/beehive-lab/TornadoVM/pull/135/files
-  Renaming the Profiler API method from ``event.getExecutionTime`` to
   ``event.getElapsedTime``:
   https://github.com/beehive-lab/TornadoVM/pull/134
-  Deprecating ``OCLWriteNode`` and ``PTXWriteNode`` and fixing stores
   for bytes: https://github.com/beehive-lab/TornadoVM/pull/131
-  Refactoring of the FPGA IR extensions, from the high-tier to the
   low-tier of the JIT compiler

   -  Utilizing the FPGA Thread-Attributes compiler phase for the FPGA
      execution
   -  Using the ``GridScheduler`` object (if present) or use a default
      value (e.g., 64, 1, 1) for defining the FPGA OpenCL local
      workgroup

-  Several bugs fixed:

   -  Codegen for sequential kernels fixed
   -  Function parameters with non-inlined method calls fixed


TornadoVM 0.10
--------------

29/06/2021

-  TornadoVM JIT Compiler sync with Graal 21.1.0
-  Experimental support for OpenJDK 16
-  Tracing the TornadoVM thread distribution and device information with
   a new option ``--threadInfo`` instead of ``--debug``
-  Refactoring of the new API:

   -  ``TornadoVMExecutionContext`` renamed to ``KernelContext``
   -  ``GridTask`` renamed to ``GridScheduler``

-  AWS F1 AMI version upgraded to 1.10.0 and automated the generation of
   AFI image
-  Xilinx OpenCL backend expanded with:

   - a) Initial integration of Xilinx OpenCL attributes for loop
         pipelining in the TornadoVM compiler

   - b) Support for multiple compute units

-  Logging FPGA compilation option added to dump FPGA HLS compilation to
   a file
-  TornadoVM profiler enhanced for including data transfers for the
   stack-frame and kernel dispatch time
-  Initial support for 2D Arrays added
-  Several bug fixes and stability support for the OpenCL and PTX
   backends


TornadoVM 0.9
-------------

15/04/2021

-  Expanded API for expressing kernel parallelism within Java. It can
   work with the existing loop parallelism in TornadoVM.

   -  Direct access to thread-ids, OpenCL local memory (PTX shared
      memory), and barriers

   -  ``TornadoVMContext`` added:

      See
      https://github.com/beehive-lab/TornadoVM/blob/5bcd3d6dfa2506032322c32d72b7bbd750623a95/tornado-api/src/main/java/uk/ac/manchester/tornado/api/TornadoVMContext.java

   -  Code examples:

      -  https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/tornadovmcontext

   -  Documentation:

      -  https://github.com/beehive-lab/TornadoVM/blob/master/assembly/src/docs/21_TORNADOVM_CONTEXT.md

-  Profiler integrated with Chrome debug:

   -  Use flags:
      ``-Dtornado.chrome.event.tracer.enabled=True -Dtornado.chrome.event.tracer.filename=userFile.json``
   -  See https://github.com/beehive-lab/TornadoVM/pull/41

-  Added support for Windows 10:

   -  See
      https://github.com/beehive-lab/TornadoVM/blob/develop/assembly/src/docs/20_INSTALL_WINDOWS_WITH_GRAALVM.md

-  TornadoVM running with Windows JDK 11 supported (Linux & Windows)
-  Xilinx FPGAs workflow supported for Vitis 2020.2
-  Pre-compiled tasks for Xilinx/Intel FPGAs fixed
-  Slambench fixed when compiling for PTX and OpenCL backends
-  Several bug fixes for the runtime, JIT compiler and data management.

--------------

TornadoVM 0.8
-------------

19/11/2020

-  Added PTX backend for NVIDIA GPUs

   -  Build TornadoVM using ``make BACKEND=ptx,opencl`` to obtain the
      two supported backends.

-  TornadoVM JIT Compiler aligned with Graal 20.2.0
-  Support for other JDKs:

   -  Red Hat Mandrel 11.0.9
   -  Amazon Coretto 11.0.9
   -  GraalVM LabsJDK 11.0.8
   -  OpenJDK 11.0.8
   -  OpenJDK 12.0.2
   -  OpenJDK 13.0.2
   -  OpenJDK 14.0.2

-  Support for hybrid (CPU-GPU) parallel reductions
-  New API for generic kernel dispatch. It introduces the concept of
   ``WorkerGrid`` and ``GridTask``

   -  A ``WorkerGrid`` is an object that stores how threads are
      organized on an OpenCL device:
      ``java       WorkerGrid1D worker1D = new WorkerGrid1D(4096);``
   -  A ``GridTask`` is a map that relates a task-name with a
      worker-grid.
      ``java       GridTask gridTask = new GridTask();       gridTask.set("s0.t0", worker1D);``
   -  A TornadoVM Task-Schedule can be executed using a ``GridTask``:
      ``java     ts.execute(gridTask);``
   -  More info:
      `link <https://github.com/beehive-lab/TornadoVM/commit/6191720fd947d3102e784dade9e576ed8af11068>`__

-  TornadoVM profiler improved

   -  Profiler metrics added
   -  Code features per task-graph

-  Lazy device initialisation moved to early initialisation of PTX and
   OpenCL devices
-  Initial support for Atomics (OpenCL backend)

   -  `Link to
      examples <https://github.com/beehive-lab/TornadoVM/blob/master/unittests/src/main/java/uk/ac/manchester/tornado/unittests/atomics/TestAtomics.java>`__

-  Task Schedules with 11-14 parameters supported
-  Documentation improved
-  Bug fixes for code generation, numeric promotion, basic block
   traversal, Xilinx FPGA compilation.

--------------

TornadoVM 0.7
-------------

22/06/2020

-  Support for ARM Mali GPUs.
-  Support parallel reductions on FPGAs
-  Agnostic FPGA vendor compilation via configuration files (Intel & Xilinx)
-  Support for AWS on Xilinx FPGAs
-  Recompilation for different input data sizes supported
-  New TornadoVM API calls:

   a) Update references for re-compilation:
      ``taskSchedule.updateReferences(oldRef, newRef);``
   b) Use the default OpenCL scheduler:
      ``taskSchedule.useDefaultThreadScheduler(true);``

-  Use of JMH for benchmarking
-  Support for Fused Multiply-Add (FMA) instructions
-  Easy-selection of different devices for unit-tests
   ``tornado-test.py -V --debug -J"-Dtornado.unittests.device=0:1"``
-  Bailout mechanism improved from parallel to sequential
-  Improve thread scheduling
-  Support for private memory allocation
-  Assertion mode included
-  Documentation improved
-  Several bug fixes


TornadoVM 0.6
-------------

21/02/2020

-  TornadoVM compatible with GraalVM 19.3.0 using JDK 8 and JDK 11
-  TornadoVM compiler update for using Graal 19.3.0 compiler API
-  Support for dynamic languages on top of Truffle

   -  `examples <https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot>`__

-  Support for multiple tasks per task-schedule on FPGAs (Intel and
   Xilinx)
-  Support for OSX Mojave and Catalina
-  Task-schedule name handling for FPGAs improved
-  Exception handling improved
-  Reductions for ``long`` type supported
-  Bug fixes for ternary conditions, reductions and code generator
-  Documentation improved


TornadoVM 0.5
-------------

16/12/2019

-  Initial support for Xilinx FPGAs
-  TornadoVM API classes are now ``Serializable``
-  Initial support for local memory for reductions
-  JVMCI built with local annotation patch removed. Now TornadoVM
   requires unmodified JDK8 with JVMCI support
-  Support of multiple reductions within the same ``task-schedules``
-  Emulation mode on Intel FPGAs is fixed
-  Fix reductions on Intel Integrated Graphics
-  TornadoVM driver OpenCL initialization and OpenCL code cache improved
-  Refactoring of the FPGA execution modes (full JIT and emulation modes
   improved).


TornadoVM 0.4
-------------

14/10/2019

-  Profiler supported

   -  Use ``-Dtornado.profiler=True`` to enable profiler
   -  Use ``-Dtornado.profiler=True -Dtornado.profiler.save=True`` to
      dump the profiler logs

-  Feature extraction added

   -  Use ``-Dtornado.feature.extraction=True`` to enable code
      extraction features

-  Mac OSx support
-  Automatic reductions composition (map-reduce) within the same
   task-schedule
-  Bug related to a memory leak when running on GPUs solved
-  Bug fixes and stability improvements



TornadoVM 0.3
-------------

22/07/2019

-  New Matrix 2D and Matrix 3D classes with type specializations.
-  New API-call ``TaskSchedule#batch`` for batch processing. It allows
   programmers to run with more data than the maximum capacity of the
   accelerator by creating batches of executions.
-  FPGA full automatic compilation pipeline.
-  FPGA options simplified:

   -  ``-Dtornado.precompiled.binary=<binary>`` for loading the
      bitstream.
   -  ``-Dtornado.opencl.userelative=True`` for using relative
      addresses.
   -  ``-Dtornado.opencl.codecache.loadbin=True`` *removed*.

-  Reductions support enhanced and fully automated on GPUs and CPUs.
-  Initial support for reductions on FPGAs.
-  Initial API for profiling tasks integrated.


TornadoVM 0.2
-------------

25/02/2019

-  Rename to TornadoVM
-  Device selection for better performance (CPU, multi-core, GPU, FPGA)
   via an API for Dynamic Reconfiguration

   -  Added methods ``executeWithProfiler`` and
      ``executeWithProfilerSequential`` with an input policy.
   -  Policies: ``Policy.PERFORMANCE``, ``Policy.END_2_END``, and
      ``Policy.LATENCY`` implemented.

-  Basic heuristic for predicting the highest performing target device
   with Dynamic Reconfiguration
-  Initial FPGA integration for Altera FPGAs:

   -  Full JIT compilation mode
   -  Ahead of time compilation mode
   -  Emulation/debug mode

-  FPGA JIT compiler specializations
-  Added support for Java reductions:

   -  Compiler specializations for CPU and GPU reductions

-  Performance and stability fixes


Tornado 0.1.0
-------------

07/09/2018

-  Initial Implementation of the Tornado compiler
-  Initial GPU/CPU code generation for OpenCL
-  Initial support in the runtime to execute OpenCL programs generated
   by the Tornado JIT compiler
-  Initial Tornado-API release (``@Parallel`` Java annotation and ``TaskSchedule`` API)
-  Multi-GPU enabled through multiple tasks-schedules
