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

   $ tornado --jvm "-Dtornado.debug.full=true" -m tornado.examples/uk.ac.manchester.examples.compute.Montecarlo 1024

To list every flag with its type, its default and the value it is currently set to:

.. code-block:: bash

   $ tornado --list-flags          # supported flags
   $ tornado --list-flags --all    # also internal and experimental flags

TornadoVM CLI Flags
-------------------

The ``tornado`` wrapper maps these flags to JVM flags for ease of use. For example, ``--printKernel`` passes ``-Dtornado.print.kernel=true``.

.. table::
   :align: left

   ==============================  ============================================================================
   Flag                            Description
   ==============================  ============================================================================
   ``--debug``                     Enables basic debug output such as compilation status and device info.
   ``--fullDebug``                 Enables full debug mode (maps to ``-Dtornado.debug.full=true``).
   ``--printKernel``               Prints the generated kernels.
   ``--threadInfo``                Displays the number of threads used.
   ``--printBytecodes``            Prints the TornadoVM bytecodes.
   ``--devices``                   Lists available hardware devices.
   ``--list-flags``                Lists the JVM flags; add ``--all`` for internal ones.
   ``--enableProfiler console``    Prints profiling metrics as JSON to stdout.
   ``--enableProfiler silent``     Collects profiling metrics internally (see TornadoVM Profiler API).
   ``--dumpProfiler FILENAME``     Saves profiling output to the specified file.
   ==============================  ============================================================================

JVM Flags
---------

JVM flags are read, in order of precedence, from ``-D`` options on the command line (or set at run time with
``TornadoRuntimeProvider.setProperty``), then from ``$TORNADOVM_HOME/etc/tornado.properties``, and otherwise take
their default. Boolean flags accept ``true`` or ``false`` in any letter case; a value a flag cannot take stops the
program with a message naming the flag.

When TornadoVM starts, it warns about every ``-Dtornado.*`` flag that does not exist, suggesting the closest
existing one, and about every flag that was removed. A flag that was renamed keeps working under its former name
for one release, with a warning naming the new one.

The tables below are generated from the flag declarations in ``tornado-api``
(``uk.ac.manchester.tornado.api.options``).

.. BEGIN GENERATED OPTIONS (OptionsDocTool --rst-update; do not edit by hand)

Runtime and device selection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.backend``
     - ``0``
     - Index of the default backend, as numbered by ``tornado --devices``.
   * - ``-Dtornado.device``
     - ``0``
     - Index of the default device within the default backend.
   * - ``-Dtornado.enabled.backends``
     - *empty*
     - Comma-separated backends to load (``opencl``, ``cuda``, ``metal``); empty loads every installed backend. A backend left out is not initialised, which shortens start-up. Backend indices count only the loaded backends.
   * - ``-Dtornado.device.memory``
     - 4GB
     - Maximum device memory TornadoVM allocates per device, e.g. ``512MB``, ``8GB``.
   * - ``-Dtornado.concurrent.devices``
     - ``false``
     - Run the tasks of a task graph concurrently on several devices, in one or several backends.
   * - ``-Dtornado.ooo.execution.enable``
     - ``false``
     - Create device command queues that may run commands out of order. Formerly ``-Dtornado.ooo-execution.enable``.
   * - ``-Dtornado.recover.bailout``
     - ``false``
     - When a task fails to compile or launch, run it as sequential Java on the host instead of failing. Off by default: the fallback is silent, so a broken device path looks like a working one.
   * - ``-Dtornado.reuse.device.buffers``
     - ``true``
     - Keep device buffers between executions of a task graph instead of allocating them every time.
   * - ``-Dtornado.deallocate.buffers``
     - ``true``
     - Free every device buffer of an execution plan when the plan is closed.
   * - ``-Dtornado.virtual.device``
     - ``false``
     - Generate code for a virtual device described by ``tornado.device.desc`` instead of a real one.
   * - ``-Dtornado.device.desc``
     - ``etc/virtual-device-template.json``
     - JSON description of the virtual device used by ``tornado.virtual.device``.
   * - ``-Dtornado.tvm.max.bytecode.size``
     - ``4096``
     - Size in bytes of the buffer that holds the TornadoVM bytecodes of a task graph. Formerly ``-Dtornado.tvm.maxbytecodesize``.

Debugging and logging
~~~~~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.debug``
     - ``false``
     - Print basic debug information.
   * - ``-Dtornado.debug.full``
     - ``false``
     - Print detailed debug information; more verbose than ``tornado.debug``. Formerly ``-Dtornado.fullDebug``.
   * - ``-Dtornado.thread.info``
     - ``false``
     - Print the thread configuration (global and local work sizes) of every kernel launch. Formerly ``-Dtornado.threadInfo``.
   * - ``-Dtornado.print.kernel``
     - ``false``
     - Print the source of every generated kernel (OpenCL C, CUDA C or Metal Shading Language). Formerly ``-Dtornado.printKernel``.
   * - ``-Dtornado.print.kernel.dir``
     - *empty*
     - File the generated kernels are appended to; empty prints them on standard output.
   * - ``-Dtornado.print.bytecodes``
     - ``false``
     - Print the TornadoVM bytecodes of every task graph as it runs.
   * - ``-Dtornado.dump.bytecodes.dir``
     - *empty*
     - File the TornadoVM bytecodes are written to; empty does not write them.

Profiling
~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.profiler``
     - ``false``
     - Enable the TornadoVM profiler and print its report on standard output after each execution.
   * - ``-Dtornado.log.profiler``
     - ``false``
     - Keep the profiler report instead of printing it, so it can be read with the execution result or written to ``tornado.profiler.dump.dir``.
   * - ``-Dtornado.profiler.dump.dir``
     - *empty*
     - File the profiler report is written to, as JSON.
   * - ``-Dtornado.ns.time``
     - ``true``
     - Report profiler times in nanoseconds; ``false`` reports them in milliseconds.
   * - ``-Dtornado.feature.extraction``
     - ``false``
     - Extract the static code features of every compiled kernel, as JSON.
   * - ``-Dtornado.features.dump.dir``
     - *empty*
     - File the extracted kernel features are written to; empty prints them on standard output.
   * - ``-Dtornado.chrome.event.tracer.enabled``
     - ``false``
     - Record runtime events in the Chrome trace format (open in ``chrome://tracing`` or Perfetto).
   * - ``-Dtornado.chrome.event.tracer.filename``
     - ``chrome.json``
     - File the Chrome trace is written to.

Thread scheduling
~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.scheduler.block``
     - ``false``
     - Split the iteration space into blocks, one per visible CPU core, when running on CPUs.
   * - ``-D<id>.device``
     - unset
     - Device a task graph or task runs on, as ``<backend>:<device>``, e.g. ``-Ds0.t0.device=1:0``.
   * - ``-D<id>.global.workgroup.size``
     - unset
     - Global work size of a task, comma-separated per dimension, e.g. ``-Ds0.t0.global.workgroup.size=1024,1024``.
   * - ``-D<id>.local.workgroup.size``
     - unset
     - Local work size of a task, comma-separated per dimension, e.g. ``-Ds0.t0.local.workgroup.size=16,16``.
   * - ``-D<id>.coarseness``
     - unset
     - Thread coarsening factors of a task, comma-separated per dimension.
   * - ``-D<id>.opencl.gpu.block.x`` or ``-Dtornado.opencl.gpu.block.x``
     - ``256``
     - OpenCL work-group size of one-dimensional kernels on GPUs.
   * - ``-D<id>.opencl.gpu.block2d.x`` or ``-Dtornado.opencl.gpu.block2d.x``
     - ``4``
     - OpenCL work-group size in x of two-dimensional kernels on GPUs.
   * - ``-D<id>.opencl.gpu.block2d.y`` or ``-Dtornado.opencl.gpu.block2d.y``
     - ``4``
     - OpenCL work-group size in y of two-dimensional kernels on GPUs.
   * - ``-D<id>.metal.threads.per.threadgroup.x`` or ``-Dtornado.metal.threads.per.threadgroup.x``
     - ``1``
     - Metal threads per threadgroup in x.
   * - ``-D<id>.metal.threads.per.threadgroup.y`` or ``-Dtornado.metal.threads.per.threadgroup.y``
     - ``1``
     - Metal threads per threadgroup in y.
   * - ``-D<id>.metal.threads.per.threadgroup.z`` or ``-Dtornado.metal.threads.per.threadgroup.z``
     - ``1``
     - Metal threads per threadgroup in z.

OpenCL backend
~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.opencl.compiler.flags``
     - ``-cl-mad-enable -cl-fast-relaxed-math -w``
     - OpenCL build options for every kernel. A plan's ``withCompilerFlags(...)`` is added to them: an option given there replaces a default of the same name.
   * - ``-Dtornado.opencl.priority``
     - ``10``
     - Priority of the OpenCL backend when choosing the default backend; the highest priority comes first.
   * - ``-Dtornado.opencl.profiling.enable``
     - ``true``
     - Create OpenCL command queues with profiling enabled, which the TornadoVM profiler needs for kernel times.
   * - ``-Dtornado.opencl.codecache.enable``
     - ``false``
     - Write the binary of every compiled kernel to ``tornado.opencl.codecache.dir`` (OpenCL and CUDA backends; not on Apple platforms).
   * - ``-Dtornado.opencl.codecache.dump``
     - ``false``
     - Same as ``tornado.opencl.codecache.enable``: write the binary of every compiled kernel to ``tornado.opencl.codecache.dir``.
   * - ``-Dtornado.opencl.codecache.dir``
     - ``/var/opencl-codecache``
     - Directory, under ``$TORNADOVM_HOME``, the kernel binaries are written to.
   * - ``-Dtornado.opencl.source.dump``
     - ``false``
     - Write the source of every generated kernel to ``tornado.opencl.source.dir`` (OpenCL and CUDA backends).
   * - ``-Dtornado.opencl.source.dir``
     - ``/var/opencl-compiler``
     - Directory, under ``$TORNADOVM_HOME``, the generated kernel sources are written to.
   * - ``-Dtornado.opencl.log.dir``
     - ``/var/opencl-logs``
     - Directory, under ``$TORNADOVM_HOME``, the kernel compiler logs are written to.

CUDA backend
~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.cuda.compile.profile``
     - ``default``
     - Named NVRTC option bundle: ``default`` (none), ``fast`` (``--use_fast_math --extra-device-vectorization``), ``debug`` (``-lineinfo``, so Nsight can attribute samples to the generated CUDA C) or ``repro`` (``--fmad=false``).
   * - ``-Dtornado.cuda.compiler.flags``
     - *empty*
     - Options passed to NVRTC, added to those of ``tornado.cuda.compile.profile`` and a plan's ``withCompilerFlags(...)``; an option of the same name replaces the profile's (NVRTC rejects an option given twice).
   * - ``-Dtornado.cuda.priority``
     - ``0``
     - Priority of the CUDA backend when choosing the default backend; the highest priority comes first.
   * - ``-Dtornado.cuda.codecache.enable``
     - ``true``
     - Keep compiled CUDA modules on disk and reuse them in later runs, so a kernel goes through NVRTC once per source, device, options and toolkit.
   * - ``-Dtornado.cuda.codecache.dir``
     - ``/var/cuda-codecache``
     - Directory, under ``$TORNADOVM_HOME``, of the CUDA module cache.
   * - ``-Dtornado.cuda.sched``
     - ``yield``
     - How a host thread waits for the device: ``yield``, ``spin``, ``blocking`` (sleeps until signalled) or ``auto``.
   * - ``-Dtornado.cuda.compute.streams``
     - ``4``
     - CUDA streams per execution plan that independent kernels are spread over, so they can run concurrently.
   * - ``-Dtornado.cuda.host.pinning``
     - ``true``
     - Pin the host memory of buffers transferred on every execution; ``false`` uses pageable transfers.
   * - ``-Dtornado.cuda.host.pinning.all``
     - ``false``
     - Pin the host memory of every device buffer, not only of those transferred on every execution.
   * - ``-Dtornado.cuda.payload.alignment``
     - ``32``
     - Byte boundary the first element of a native array is aligned to in device memory. 32 keeps warp-wide accesses at four 32-byte L2 sectors instead of five. Formerly ``-Dtornado.cuda.payloadAlignment``.
   * - ``-Dtornado.cuda.nvcc``
     - found from the CUDA toolkit
     - Path of the ``nvcc`` used to compile CUDA Tile kernels.
   * - ``-Dtornado.cuda.devrt.path``
     - searched under $CUDA_PATH, $CUDA_HOME, $CUDA_ROOT and /usr/local/cuda*
     - Path of the CUDA device runtime (``libcudadevrt.a``), linked into kernels that launch kernels (``KernelContext.launch``).
   * - ``-Dtornado.cuda.dp.pending.launch.count``
     - driver default (2048)
     - Maximum device-side kernel launches outstanding at once (``CU_LIMIT_DEV_RUNTIME_PENDING_LAUNCH_COUNT``); a launch beyond it fails. A plan's ``withCUDAPendingLaunchCount(n)`` takes precedence. Formerly ``-Dtornado.cuda.dp.pendingLaunchCount``.
   * - ``-Dtornado.staged.transfers``
     - ``false``
     - Experimental: upload large one-shot transfers through a ring of pinned staging buffers, overlapping the host copy with the DMA.

Metal backend
~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.metal.compiler.flags``
     - *empty*
     - Options passed to the Metal compiler.
   * - ``-Dtornado.metal.priority``
     - ``0``
     - Priority of the Metal backend when choosing the default backend; the highest priority comes first.
   * - ``-Dtornado.metal.profiling.enable``
     - ``true``
     - Record Metal command-buffer timestamps, which the TornadoVM profiler needs for kernel times.
   * - ``-Dtornado.metal.fast.math``
     - ``false``
     - Compile Metal kernels with fast, relaxed math (``MTLMathModeFast``), trading some floating-point precision for speed. Formerly ``-Dtornado.metal.fastmath``.
   * - ``-Dtornado.metal.codecache.enable``
     - ``false``
     - Load a kernel's Metal library from ``tornado.metal.codecache.dir`` when it is there instead of compiling it, and write the compiled ones there.
   * - ``-Dtornado.metal.codecache.dump``
     - ``false``
     - Write the Metal library of every compiled kernel to ``tornado.metal.codecache.dir``, without loading from it.
   * - ``-Dtornado.metal.codecache.dir``
     - ``/var/metal-codecache``
     - Directory, under ``$TORNADOVM_HOME``, of the Metal library cache.
   * - ``-Dtornado.metal.source.dump``
     - ``false``
     - Write the generated Metal Shading Language sources to ``tornado.metal.source.dir``.
   * - ``-Dtornado.metal.source.dir``
     - ``/var/metal-compiler``
     - Directory, under ``$TORNADOVM_HOME``, the generated Metal sources are written to.
   * - ``-Dtornado.metal.log.dir``
     - ``/var/metal-logs``
     - Directory, under ``$TORNADOVM_HOME``, the Metal compiler logs are written to.

TornadoVM compiler (TC)
~~~~~~~~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.enable.fma``
     - ``true``
     - Fuse multiplies and adds into ``fma``. ``false`` keeps the two roundings of the host, also in NVRTC (``--fmad=false``). For one execution plan only, use ``TornadoExecutionPlan.withStrictFloatingPoint()``.
   * - ``-Dtornado.enable.math.optimizations``
     - ``true``
     - Rewrite math calls into faster equivalents, such as ``1/sqrt(x)`` into ``rsqrt(x)``. Formerly ``-Dtornado.enable.mathOptimizations``.
   * - ``-Dtornado.experimental.partial.unroll``
     - ``false``
     - Experimental: partially unroll loops.
   * - ``-Dtornado.tc.dump``
     - ``none``
     - Compiler stages whose IR is written to ``tornado.tc.dump.dir``: ``none``, ``all`` or a comma-separated list.
   * - ``-Dtornado.tc.dump.dir``
     - ``tc-dumps``
     - Directory of the compiler IR dumps and reports. Formerly ``-Dtornado.tc.dumpDir``.
   * - ``-Dtornado.tc.verify``
     - ``end``
     - When the compiler verifies its IR: ``end`` of the pipeline or after ``each`` stage.
   * - ``-Dtornado.tc.only``
     - *empty*
     - Dump and report only the functions whose name contains this text; empty selects every function.
   * - ``-Dtornado.tc.report``
     - *empty*
     - ``html`` writes an HTML report of every compilation to ``tornado.tc.dump.dir``.
   * - ``-Dtornado.tc.verbose``
     - ``false``
     - Print one line per compiled kernel.
   * - ``-Dtornado.tc.stats``
     - ``false``
     - Print a summary of the compilations at exit.
   * - ``-Dtornado.tc.compile.times``
     - ``false``
     - With the profiler on, print the compiler's and the driver's compilation time of every task. Formerly ``-Dtornado.tc.compileTimes``.

Native libraries
~~~~~~~~~~~~~~~~

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.mlx.async``
     - ``false``
     - Commit MLX library tasks without waiting for the GPU; library calls only encode GPU work.
   * - ``-Dtornado.mlx.metallib``
     - the mlx.metallib installed next to libmlx
     - Path of MLX's compiled kernel library (``mlx.metallib``).
   * - ``-Dtornado.cudnn.deterministic``
     - ``false``
     - Choose only deterministic cuDNN algorithms.

Internal and experimental options
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For TornadoVM developers. They may change or disappear without a deprecation period.

.. list-table::
   :header-rows: 1
   :widths: 35 15 50

   * - Flag
     - Default
     - Description
   * - ``-Dtornado.load.api.implementation``
     - ``uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph``
     - Class implementing the task-graph API.
   * - ``-Dtornado.load.runtime.implementation``
     - ``uk.ac.manchester.tornado.runtime.TornadoCoreRuntime``
     - Class implementing the TornadoVM runtime.
   * - ``-Dtornado.load.tornado.implementation``
     - ``uk.ac.manchester.tornado.runtime.common.Tornado``
     - Class implementing the TornadoVM settings access.
   * - ``-Dtornado.fuzz.gen.dir``
     - temporary directory
     - Directory the TornadoVM fuzzer writes its generated kernel classes to. Formerly ``-Dtornado.fuzz.genDir``.
   * - ``-Dtornado.cuda.graph.async.launch``
     - ``false``
     - Launch a CUDA graph that copies nothing back to the host without waiting for it. Formerly ``-Dtornado.cuda.graph.asyncLaunch``.
   * - ``-Dtornado.staged.chunk.size``
     - ``16777216``
     - Size in bytes of each pinned staging buffer of ``tornado.staged.transfers``.
   * - ``-Dtornado.staged.ring.depth``
     - ``4``
     - Number of pinned staging buffers of ``tornado.staged.transfers``.
   * - ``-Dtornado.staged.min.size``
     - ``16777216``
     - Smallest transfer in bytes that ``tornado.staged.transfers`` stages.
   * - ``-Dtornado.staged.fill.threads``
     - half the cores, at most 8
     - Threads that fill a staging buffer of ``tornado.staged.transfers``.
   * - ``-Dtornado.metal.dispatch.batch``
     - ``true``
     - Encode kernels into shared command buffers committed in batches, instead of committing and waiting for every kernel.
   * - ``-Dtornado.metal.dispatch.batch.size``
     - ``2``
     - Kernels per command buffer before it is committed, with ``tornado.metal.dispatch.batch``. Formerly ``-Dtornado.metal.dispatch.batchSize``.
   * - ``-Dtornado.metal.dispatch.spin.wait``
     - ``true``
     - Wait for a command buffer by polling the shared event it signals, instead of ``waitUntilCompleted``. Formerly ``-Dtornado.metal.dispatch.spinWait``.
   * - ``-Dtornado.tc.memoize``
     - ``true``
     - Reuse a compilation for a task with the same method and facts as an earlier one.
   * - ``-Dtornado.tc.varying.scalars``
     - ``3``
     - A scalar argument that took this many different values across the tasks of one kernel is compiled as a runtime value from then on; 0 specialises every value. Formerly ``-Dtornado.tc.varyingScalars``.
   * - ``-Dtornado.tc.dynamic.shapes``
     - ``false``
     - Compile every task as with ``withDynamicShapes()``: a kernel that never reads an array length is shared by tasks over arrays of any size. Formerly ``-Dtornado.tc.dynamicShapes``.
   * - ``-Dtornado.tc.native.half``
     - ``true``
     - Compute half-precision arithmetic in half; ``false`` computes it through float. Formerly ``-Dtornado.tc.nativeHalf``.
   * - ``-Dtornado.tc.signed.overflow.undefined``
     - ``false``
     - Emit int and long arithmetic as plain C, where signed overflow is undefined: faster where the driver compiler exploits it, wrong where Java code relies on wrapping. Formerly ``-Dtornado.tc.signedOverflowUndefined``.
   * - ``-Dtornado.nccl.spin.wait.micros``
     - ``1000``
     - Microseconds an NCCL plan-group thread spins before it blocks while waiting for work. Formerly ``-Dtornado.nccl.spinWaitMicros``.

Removed options
~~~~~~~~~~~~~~~

Setting one of these prints a warning and has no effect.

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Flag
     - Reason
   * - ``-Dtornado.check.parameters``
     - TornadoVM always behaves as with its default (true).
   * - ``-Dtornado.circularevents``
     - TornadoVM always behaves as with its default (true).
   * - ``-Dtornado.clean.atomics.space``
     - TornadoVM always behaves as with its default (false).
   * - ``-Dtornado.debug.executionTime``
     - It had no effect. Use tornado.profiler.
   * - ``-Dtornado.dump.to.ip``
     - Sending profiler logs over a socket was removed. Use tornado.profiler.dump.dir.
   * - ``-Dtornado.enable.fastMathOptimizations``
     - It had no effect. Use tornado.enable.math.optimizations.
   * - ``-Dtornado.enable.ip.logging``
     - Sending profiler logs over a socket was removed.
   * - ``-Dtornado.enable.nativeFunctions``
     - It had no effect.
   * - ``-Dtornado.enable.streamOut.blocking``
     - TornadoVM always behaves as with its default (true).
   * - ``-Dtornado.eventpool.maxwaitevents``
     - TornadoVM always behaves as with its default (64).
   * - ``-Dtornado.eventpool.size``
     - TornadoVM always behaves as with its default (1024).
   * - ``-Dtornado.events.dump``
     - TornadoVM always behaves as with its default (false).
   * - ``-Dtornado.events.log``
     - It had no effect.
   * - ``-Dtornado.experimental.reduce``
     - TornadoVM always behaves as with its default (true).
   * - ``-Dtornado.kernels.parallelise``
     - It had no effect.
   * - ``-Dtornado.logger.buildlir``
     - It had no effect.
   * - ``-Dtornado.logger.codegen``
     - It had no effect.
   * - ``-Dtornado.max.events``
     - TornadoVM always behaves as with its default (32768).
   * - ``-Dtornado.metal.threadgroupHint``
     - It had no effect. Use <task>.metal.threads.per.threadgroup.{x,y,z}.
   * - ``-Dtornado.nccl.benchmark.cudaGraph``
     - Pass --cuda-graph to the NCCL benchmark instead.
   * - ``-Dtornado.opencl.print``
     - It had no effect. Use tornado.print.kernel.
   * - ``-Dtornado.opencl.syncflush``
     - TornadoVM always behaves as with its default (false).
   * - ``-Dtornado.panama.objectHeader``
     - The array header size is derived from the JVM's compressed-pointer setting.
   * - ``-Dtornado.platform``
     - It had no effect. Use tornado.backend.
   * - ``-Dtornado.unroll.factor``
     - It had no effect.
   * - ``-Dtornado.vectors.enable``
     - It had no effect.
   * - ``-Dtornado.vm.deps``
     - TornadoVM always behaves as with its default (false).
   * - ``-Dtornado.vmflush``
     - TornadoVM always behaves as with its default (true).
.. END GENERATED OPTIONS

Adding a Flag
-------------

For TornadoVM developers:

1. Declare the flag as a constant of the catalog class of its area in ``uk.ac.manchester.tornado.api.options``
   (``RuntimeFlags``, ``DebugFlags``, ``ProfilerFlags``, ``TaskFlags``, ``OpenCLFlags``, ``CudaFlags``,
   ``MetalFlags``, ``TcFlags`` or ``LibraryFlags``), with its key, default, description and category. Mark
   developer-only flags with ``.internal()``.
2. Read it with ``Flags.NAME.get()`` (or ``find()`` for a flag without a default). Never call
   ``System.getProperty("tornado.…")``: a unit test rejects it.
3. Regenerate this page with ``make flags-doc``.
4. To rename a flag, change its key and add the former one with ``.deprecatedKey(...)``. To remove one, delete it
   and add it to ``RemovedFlags`` with the reason.
