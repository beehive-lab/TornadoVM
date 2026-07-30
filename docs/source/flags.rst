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
   ``--printKernel``        Prints generated OpenCL/CUDA kernels.
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
   ``-Dtornado.printKernel=true``                    Prints generated OpenCL/CUDA kernels.
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
   ``-Dtornado.{opencl,cuda,metal}.priority=X``                      Sets backend priority; higher wins when multiple backends can run a task (default: OpenCL=10, CUDA=0, Metal=0).
   ``-Dtornado.reuse.device.buffers=false``                          Disables reusing device buffers across executions of the same task-graph (default: true).
   ``-Dtornado.deallocate.buffers=false``                            Disables freeing device resources when the execution plan closes (default: true).
   ``-Dtornado.scheduler.block=true``                                Partitions the iteration space into blocks (one per visible CPU core when running on CPUs) (default: false).
   ================================================================  ==============================================================================

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

CUDA C Backend Specific
---------------------------

TornadoVM's **CUDA C** backend targets NVIDIA GPUs: it emits CUDA C, compiled to PTX via NVRTC, and is built on its own with :code:`make BACKEND=cuda`. The flags below apply to the CUDA C backend specifically.

**JVM Flags**

.. table::
   :align: left

   ================================================================  ==================================================================================================================
   Flag                                                              Description
   ================================================================  ==================================================================================================================
   ``-Dtornado.cuda.compiler.flags=FLAGS``                           Passes additional flags to NVRTC when compiling the generated CUDA C source (default: none).
   ``-Dtornado.cuda.host.pinning=false``                             Disables pinning host memory for faster host↔device transfers (default: true).
   ``-Dtornado.cuda.codecache.enable=false``                         Disables the on-disk cache of compiled CUDA modules, so every run re-compiles kernels with NVRTC (default: true).
   ``-Dtornado.cuda.codecache.dir=PATH``                             Sets the directory used to store the on-disk CUDA module cache (default: ``/var/cuda-codecache``).
   ================================================================  ==================================================================================================================

.. note::

   The CUDA C backend's code cache is controlled by properties that still carry the ``opencl`` prefix — inherited unchanged from the OpenCL backend's code cache implementation, and not (yet) renamed for CUDA. They apply to **both** backends: ``-Dtornado.opencl.codecache.enable=true``, ``-Dtornado.opencl.codecache.dump=true``, ``-Dtornado.opencl.source.dump=true``, ``-Dtornado.opencl.codecache.dir=PATH`` (default: ``/var/opencl-codecache``), ``-Dtornado.opencl.source.dir=PATH`` (default: ``/var/opencl-compiler``), ``-Dtornado.opencl.log.dir=PATH`` (default: ``/var/opencl-logs``).

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

