.. _flags:

TornadoVM Flags
===============

TornadoVM provides runtime and compiler flags to enable experimental features, tuning, and profiling. These flags fall into two categories:

1. **JVM Flags** (passed with the `-D` prefix via the `--jvm` option)
2. **TornadoVM CLI Flags** (passed directly to the `tornado` Python wrapper)

.. note::
   In the examples below, ``s0`` refers to a task graph and ``t0`` to a specific task within that graph.

Example Usage
-------------

.. code-block:: bash

   $ tornado --jvm "-Dtornado.fullDebug=true" -m tornado.examples/uk.ac.manchester.examples.compute.Montecarlo 1024

Debugging and Logging
---------------------

**CLI Flags**

.. table::
   :align: left

   =======================  ============================================================================
   Flag                     Description
   =======================  ============================================================================
   ``--fullDebug``          Enables full debug mode (maps to ``-Dtornado.fullDebug=true``).
   ``--debug``              Enables basic debug output such as compilation status and device info.
   ``--printKernel``        Prints generated OpenCL/PTX/SPIR-V kernels.
   ``--threadInfo``         Displays the number of threads used.
   ``--devices``            Lists available hardware devices.
   =======================  ============================================================================

**JVM Flags**

.. table::
   :align: left

   ================================================  ============================================================================
   Flag                                              Description
   ================================================  ============================================================================
   ``-Dtornado.fullDebug=true``                      Enables full debug output including bytecode and runtime internals.
   ``-Dtornado.fpgaDumpLog=true``                    Dumps FPGA HLS compilation logs.
   ``-Dtornado.printKernel=true``                    Prints generated OpenCL/PTX/SPIR-V kernels.
   ``-Dtornado.print.kernel.dir=FILENAME``           Saves generated kernels to the specified file.
   ``-Dtornado.threadInfo=true``                     Displays the number of threads used.
   ``-Dtornado.print.bytecodes=true``                Prints TornadoVM Internal Bytecodes to stdout.
   ``-Dtornado.dump.bytecodes.dir=FILENAME``         Dumps TornadoVM Internal Bytecodes to the specified file.
   ================================================  ============================================================================

Profiling
---------

**CLI Flags**

.. table::
   :align: left

   ==============================  =============================================================================
   Flag                            Description
   ==============================  =============================================================================
   ``--enableProfiler console``    Prints profiling metrics as JSON to stdout.
   ``--enableProfiler silent``     Collects profiling metrics internally (see TornadoVM Profiler API).
   ``--dumpProfiler FILENAME``     Saves profiling output to the specified file.
   ==============================  =============================================================================

**JVM Flags**

.. table::
   :align: left

   ================================================  ============================================================
   Flag                                              Description
   ================================================  ============================================================
   ``-Dtornado.profiler=true``                       Enables profiling and prints metrics as JSON to sdout.
   ``-Dtornado.log.profiler=true``                   Collects profiling metrics internally for logging.
   ``-Dtornado.profiler.dump.dir=FILENAME``          Saves profiling output to the specified file.
   ================================================  ============================================================

Performance & Scheduling
------------------------

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==============================================================================
   Flag                                                              Description
   ================================================================  ==============================================================================
   ``-Dtornado.ns.time=true``                                        Uses nanoseconds for timing instead of milliseconds (default: true).
   ``-Ds0.t0.global.workgroup.size=X,Y,Z``                           Sets custom global workgroup size.
   ``-Ds0.t0.local.workgroup.size=X,Y,Z``                            Sets custom local workgroup size.
   ``-Dtornado.concurrent.devices=true``                             Enables concurrent execution across devices (default: false).
   ``-Dtornado.{ptx,opencl}.priority=X``                             Sets driver priority (default: PTX=1, OpenCL=0).
   ================================================================  ==============================================================================

Precompiled and FPGA Options
----------------------------

**JVM Flags**

.. table::
   :align: left

   ================================================  ============================================================
   Flag                                              Description
   ================================================  ============================================================
   ``-Dtornado.precompiled.binary=PATH``             Path to precompiled kernel or FPGA bitstream.
   ``-Dtornado.fpga.conf.file=FILE``                 Path to the FPGA configuration file.
   ================================================  ============================================================

Optimizations
-------------

**JVM Flags**

.. table::
   :align: left

   ================================================================  ===================================================================================================
   Flag                                                              Description
   ================================================================  ===================================================================================================
   ``-Dtornado.enable.fma=true``                                     Enables fused multiply-add (default: true). May cause issues on some platforms.
   ``-Dtornado.enable.mathOptimizations=true``                       Enables math simplifications (e.g., ``1/sqrt(x)`` → ``rsqrt``) (default: true).
   ``-Dtornado.experimental.partial.unroll=true``                    Enables loop partial unrolling (default: false). Use ``-Dtornado.partial.unroll.factor=FACTOR``.
   ``-Dtornado.enable.nativeFunctions=true``                         Enables native math functions (default: false).
   ================================================================  ===================================================================================================

Level Zero (SPIR-V Specific)
----------------------------

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``-Dtornado.spirv.levelzero.alignment=64``                        Sets memory alignment (in bytes) for Level Zero buffers (default: 64).
   ``-Dtornado.spirv.levelzero.thread.dispatcher=true``              Uses Level Zero’s thread block suggestion (default: true).
   ``-Dtornado.spirv.loadstore=false``                               Optimizes Loads/Stores and simplifies the generated SPIR-V binary (experimental - default: false).
   ``-Dtornado.spirv.levelzero.memoryAlloc.shared=false``            Enables shared memory buffers (default: false).
   ================================================================  ==================================================================================================================

Notes
-----

All Java flags (those beginning with ``-Dtornado.``) are defined in the ``TornadoOptions.java`` file.

TornadoVM CLI flags (those beginning with ``--``) are mapped to Java flags by the Python interface for ease of use.
For example, ``--printKernel`` maps internally to ``-Dtornado.printKernel=true``.

