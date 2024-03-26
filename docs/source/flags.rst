.. _flags:

TornadoVM Flags
===============

There is a number of runtime flags and compiler flags to enable experimental features, as well as fine and coarse grain profiling in the context of TornadoVM.

**Note:** for the following examples ``s0`` represents an arbitrary task-graph, as well as ``t0`` represents a given task's name.

All flags needs Java prefix of ``-D``. An example of tornado using a flag is the following:

``$ tornado --jvm "-Dtornado.fullDebug=true" -m tornado.examples/uk.ac.manchester.examples.compute.Montecarlo 1024``

List of TornadoVM Flags:
------------------------

-  | ``-Dtornado.fullDebug=true``:
   | Enables full debugging log to be output in the. command line.

-  | ``--printKernel``:
   | Print the generated OpenCL/PTX kernel in the command line.

-  | ``--threadInfo``:
   | Print the information about the number of parallel threads used.

-  | ``--debug``:
   | Print minor debug information such as compilation status, generated code and device information. 

-  | ``--fullDebug``:
   | In addition to the information dumped by the basic debug, the full debug mode also dumps information about the TornadoVM bytecode, and internal runtime status. This option is mainly used for development of TornadoVM. 

-  | ``--devices``:
   | Output a list of all available devices on the current system.

-  | ``-Dtornado.ns.time=true``:
   | Converts the time to units to nanoseconds instead of milliseconds.

-  ``-Dtornado.{ptx,opencl}.priority=X``: Allows to define a driver
   priority. The drivers are sorted in descending order based on their
   priority. By default, the ``PTX driver`` has priority ``1`` and the
   ``OpenCL driver`` has priority ``0``.

-  | ``-Ds0.t0.global.workgroup.size=XXX,XXX,XXX``:
   | Allows to define global worksizes (problem sizes).

-  | ``-Ds0.t0.local.workgroup.size=XXX,XXX,``:
   | Allows to define custom local workgroup configuration and overwrite
     the default values provided by the TornadoScheduler.

-  | ``-Dtornado.profiling.enable=true``:
   | Enable profilling for OpenCL/CUDA events such as kernel times and
     data tranfers.

-  | ``-Dtornado.opencl.userelative=true``:
   | Enable usage of relative addresses which is a prerequisite for
     using DMA tranfers on Altera/Intel FPGAs. Nonetheless, this flag
     can be used for any OpenCL device.

-  ``-Dtornado.precompiled.binary=PATH``: Provides the location of the
   bistream or pre-generated OpenCL (.cl) kernel.

-  ``-Dtornado.fpga.conf.file=FILE``: Provides the absolute path of the
   FPGA configuation file.

-  ``-Dtornado.fpgaDumpLog=true``: Dumps the log information from the
   HLS compilation to the command prompt.

-  | ``-Dtornado.opencl.blocking=true``:
   | Allows to force OpenCL API blocking calls.

-  | ``--enableProfiler console``:
   | It enables profiler information such as ``COPY_IN``, ``COPY_OUT``,
     compilation time, total time, etc. This flag is disabled by
     default. TornadoVM will print by STDOUT a JSON string containing
     all profiler metrics related to the execution of each
     task-schedule.

-  ``--enableProfiler silent``: It enables profiler information such as
   ``COPY_IN``, ``COPY_OUT``, compilation time, total time, etc. This
   flag is disabled by default. The profiler information is stored
   internally and it can be queried using the `TornadoVM Profiler
   API <https://github.com/beehive-lab/TornadoVM/blob/master/tornado-api/src/main/java/uk/ac/manchester/tornado/api/profiler/ProfileInterface.java>`__.

-  | ``--dumpProfiler FILENAME``:
   | It enables profiler information such as ``COPY_IN``, ``COPY_OUT``,
     compilation time, total time, etc. This flag is disabled by
     default. TornadoVM will save the profiler information in the
     ``FILENAME`` after the execution of each task-schedule.

-  | ``-Dtornado.opencl.compiler.options=LIST_OF_OPTIONS``:
   | It allows to pass the compile options specified by the OpenCL
     ``CLBuildProgram``
     `specification <https://www.khronos.org/registry/OpenCL/sdk/1.0/docs/man/xhtml/clBuildProgram.html>`__
     to TornadoVM at runtime. By default it doesn’t enable any.
-  | ``-Dtornado.concurrent.devices=true``:
   | Allows to run a TaskGraph in multiple devices concurrently. The user
     needs explicitly to define the device for each task, otherwise all
     tasks will run on the default device. For instance,
     ``-Ds0.t0.device=0:0 -Ds0.t1.device=0:1``.


Optimizations
'''''''''''''

-  | ``-Dtornado.enable.fma=True``:
   | It enables Fused-Multiply-Add optimizations. This option is enabled
     by default. However, for some platforms, such as the Xilinx FPGA
     using SDAccel 2018.2 and OpenCL 1.0, this option must be disabled
     as it causes runtime errors. See issue on
     `Github <https://github.com/beehive-lab/TornadoVM/issues/24>`__.

-  ``-Dtornado.enable.mathOptimizations``: It enables math
   optimizations. For instance, ``1/sqrt(x)`` is transformed into
   ``rsqrt`` instruction for the corresponding backend (OpenCL, SPIRV
   and PTX). It is enabled by default.

-  ``-Dtornado.experimental.partial.unroll=True``: It enables the
   compiler to force partial unroll on counted loops with a factor of 2.
   The unroll factor can be configured with the
   ``tornado.partial.unroll.factor=FACTOR`` that the FACTOR value can
   take integer values up to 32.

-  ``-Dtornado.enable.nativeFunctions=False``: It enables the
   utilization of native mathematical functions, in case that the
   selected backend (OpenCL, PTX, SPIR-V) supports native functions. This
   option is disabled by default.

Level Zero
''''''''''

-  ``-Dtornado.spirv.levelzero.alignment=64``: Memory alignment (in
   bytes) for Level Zero buffers. It is set to 64 by default.

-  ``-Dtornado.spirv.levelzero.thread.dispatcher=True``: If it is
   enabled, it uses the Level Zero suggested thread block for the thread
   dispatcher. True by default.

-  ``-Dtornado.spirv.loadstore=False``: It optimizes Loads/Stores and
   simplifies the generated SPIR-V binary. This option is still
   experimental. It is set to ``False`` by default.

-  ``-Dtornado.spirv.levelzero.memoryAlloc.shared=False``: If it is
   enabled, then it uses shared memory buffers between the accelerator
   and the host. It is set to false by default.