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
   ``-Dtornado.backend=N`` / ``-Dtornado.device=N``                  Sets the default backend/device index used when none is explicitly selected (default: 0 for both).
   ``-Dtornado.{opencl,ptx,cuda,spirv,metal}.priority=X``            Sets backend priority; higher wins when multiple backends can run a task (default: OpenCL=10, SPIR-V=11, PTX=0, CUDA=0, Metal=0).
   ``-Dtornado.reuse.device.buffers=false``                          Disables reusing device buffers across executions of the same task-graph (default: true).
   ``-Dtornado.deallocate.buffers=false``                            Disables freeing device resources when the execution plan closes (default: true).
   ``-Dtornado.scheduler.block=true``                                Partitions the iteration space into blocks (one per visible CPU core when running on CPUs) (default: false).
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
   ``-Dtornado.fpga.conf.file=FILE``                 Path to the FPGA configuration file (default: none).
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
   ``-Dtornado.enable.fastMathOptimizations=true``                   Enables more aggressive fast-math optimizations (default: true).
   ``-Dtornado.experimental.partial.unroll=true``                    Enables loop partial unrolling (default: false). Use ``-Dtornado.unroll.factor=FACTOR`` (default: 4).
   ``-Dtornado.enable.nativeFunctions=true``                         Enables native math functions (default: true).
   ================================================================  ===================================================================================================

PTX Backend Specific (CU_JIT Flags)
-------------------------------------

These are sub-flags passed as the value of ``-Dtornado.ptx.compiler.flags="..."`` (space-separated ``FLAG VALUE`` pairs), not standalone ``-D`` properties. They configure the PTX backend's JIT compilation, not the separate CUDA C backend below.

**Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``CU_JIT_OPTIMIZATION_LEVEL``                                     Level of optimizations to apply to generated code (0 - 4), with 4 being the highest level of optimizations (default: 4).
   ``CU_JIT_MAX_REGISTERS``                                          Max number of registers that a thread may use (default: none).
   ``CU_JIT_TARGET``                                                 Target microarchitecture (default: none). Note that the available target microarchitecture depends on the CUDA version.
                                                                     Currently CUDA 13.0 supports the following: 30, 32, 35, 37, 50, 52, 53, 60, 61, 62, 70, 72, 75, 80, 86, 87, 89, 90, 100,
                                                                     103, 110, 120, 121. Older version of CUDA might supports less microarchitecture, for example, CUDA 12.0 supports up to 90.
   ``CU_JIT_CACHE_MODE``                                             Specifies whether to enable caching explicitly (-dlcm). 0, compile with no -dlcm flag specified.
                                                                     1, compile with L1 cache disabled (use only L2 cache). 2, compile with L1 cache enabled (use both L1 and L2 cache)
                                                                     (default: none).
   ``CU_JIT_GENERATE_DEBUG_INFO``                                    Specifies whether to create debug information in output (-g) (0: false) (default: none).
   ``CU_JIT_LOG_VERBOSE``                                            Generate verbose log messages (0: false) (default: none).
   ``CU_JIT_GENERATE_LINE_INFO``                                     Generate line number information (-lineinfo) (0: false) (default: none).
   ================================================================  ==================================================================================================================

CUDA C Backend Specific
---------------------------

TornadoVM provides two separate NVIDIA backends: **PTX** (emits PTX assembly directly) and **CUDA C** (emits CUDA C, compiled to PTX via NVRTC, built on its own with :code:`make BACKEND=cuda`). The flags below apply to the CUDA C backend specifically.

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``-Dtornado.cuda.compiler.flags=FLAGS``                           Passes additional flags to NVRTC when compiling the generated CUDA C source (default: none).
   ``-Dtornado.cuda.host.pinning=false``                             Disables pinning host memory for faster host↔device transfers (default: true).
   ================================================================  ==================================================================================================================

.. note::

   The CUDA C backend's code cache is controlled by properties that still carry the ``opencl`` prefix — inherited unchanged from the OpenCL backend's code cache implementation, and not (yet) renamed for CUDA. They apply to **both** backends: ``-Dtornado.opencl.codecache.enable=true``, ``-Dtornado.opencl.codecache.dump=true``, ``-Dtornado.opencl.source.dump=true``, ``-Dtornado.opencl.codecache.dir=PATH`` (default: ``/var/opencl-codecache``), ``-Dtornado.opencl.source.dir=PATH`` (default: ``/var/opencl-compiler``), ``-Dtornado.opencl.log.dir=PATH`` (default: ``/var/opencl-logs``).

Level Zero (SPIR-V Specific)
----------------------------

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``-Dtornado.spirv.levelzero.alignment=64``                        Sets memory alignment (in bytes) for Level Zero buffers (default: 64).
   ``-Dtornado.spirv.loadstore=false``                               Optimizes loads/stores, using fewer virtual registers (experimental - default: true).
   ``-Dtornado.spirv.levelzero.memoryAlloc.shared=false``            Enables shared memory buffers for the Level Zero backend (default: false).
   ``-Dtornado.spirv.levelzero.extended.memory=false``               Disables the extended memory allocation mode for the Level Zero backend (default: true).
   ``-Dtornado.spirv.runtimes=opencl,levelzero``                     Sets the SPIR-V dispatch runtime(s); the first in the list is the default (default: ``opencl,levelzero``).
   ``-Dtornado.spirv.version=1.2``                                   Sets the minimum SPIR-V version to target (default: 1.2).
   ================================================================  ==================================================================================================================

Metal Specific
--------------

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``-Dtornado.metal.fastmath=true``                                 Compiles Metal kernels with fast/relaxed math (Metal's analogue of OpenCL's ``-cl-fast-relaxed-math``); trades some FP precision for speed (default: false).
   ``-Dtornado.metal.threadgroupHint=true``                          Emits a ``max_total_threads_per_threadgroup`` attribute when the local work-group size is statically known, to help the Metal compiler tune occupancy (default: false).
   ``-Dtornado.metal.profiling.enable=false``                        Disables Metal profiling (default: true).
   ``-Dtornado.metal.compiler.flags=FLAGS``                          Passes additional flags to the Metal compiler (default: none).
   ================================================================  ==================================================================================================================

Notes
-----

All Java flags (those beginning with ``-Dtornado.``) are defined in the ``TornadoOptions.java`` file.

TornadoVM CLI flags (those beginning with ``--``) are mapped to Java flags by the Python interface for ease of use.
For example, ``--printKernel`` maps internally to ``-Dtornado.printKernel=true``.

