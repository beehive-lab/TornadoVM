.. _profiler:

TornadoVM Profiler
==================

The TornadoVM profiler can be enabled either from the command line (via a flag from the ``tornado`` command), or via an ``ExecutionPlan`` in the source code.

1. Enable the Profiler from the Command Line
---------------------------------------------------------

To enable the TornadoVM profiler, developers can  use ``--enableProfiler <silent|console>``.

-  ``console``: It prints a JSON entry for each task-graph executed via STDOUT.
-  ``silent`` : It enables profiling information in silent mode. Use the profiler API to query the values.

Example:

.. code:: bash

   $ tornado --enableProfiler console  -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="100000"
   {
       "s0": {
           "TOTAL_BYTE_CODE_GENERATION": "10465146",
           "COPY_OUT_TIME": "24153",
           "COPY_IN_TIME": "72044",
           "TOTAL_DRIVER_COMPILE_TIME": "63298322",
           "TOTAL_DISPATCH_DATA_TRANSFERS_TIME": "0",
           "TOTAL_CODE_GENERATION_TIME": "16564279",
           "TOTAL_TASK_GRAPH_TIME": "285317518",
           "TOTAL_GRAAL_COMPILE_TIME": "109520628",
           "TOTAL_KERNEL_TIME": "47974",
           "TOTAL_COPY_OUT_SIZE_BYTES": "400024",
           "TOTAL_COPY_IN_SIZE_BYTES": "1600096",
           "s0.t0": {
               "BACKEND": "SPIRV",
               "METHOD": "VectorAddInt.vectorAdd",
               "DEVICE_ID": "0:0",
               "DEVICE": "Intel(R) UHD Graphics [0x9bc4]",
               "TASK_COMPILE_GRAAL_TIME": "109520628",
               "TASK_CODE_GENERATION_TIME": "16564279",
               "TASK_COMPILE_DRIVER_TIME": "63298322",
               "POWER_USAGE_mW": "n/a",
               "TASK_KERNEL_TIME": "47974"
           }
       }
   }

All timers are printed in nanoseconds.


2. Enable/Disable the Profiler using the TornadoExecutionPlan
----------------------------------------------------------------------

The profiler can be enabled/disabled using the `TornadoExecutionPlan` API:

.. code:: bash

    // Enable the profiler and print report in STDOUT
    executionPlan.withProfiler(ProfilerMode.CONSOLE) //
        .withDevice(device) //
        .withDefaultScheduler()
        .execute();


It is also possible to enable the profiler without live reporting in STDOUT and query the profiler after the execution:

.. code:: bash

    // Enable the profiler in silent mode
    executionPlan.withProfiler(ProfilerMode.SILENT) //
        .withDevice(device) //
        .withDefaultScheduler();

    TornadoExecutionResult executionResult = executorPlan.execute();
    TornadoProfilerResult profilerResult = executionResult.getProfilerResult();

    // Print Kernel Time
    System.out.println(profilerResult.getDeviceKernelTime() + " (ns)");

3. Configure and Enable/Disable the Power Usage of Compute Functions via the Profiler
-------------------------------------------------------------------------------------

The profiler can query low-level system management API implementations to obtain the power usage of an executed TornadoVM task.
More specifically, TornadoVM is integrated with the `NVIDIA NVML API <https://docs.nvidia.com/deploy/nvml-api/index.html>`__ to support power usage for NVIDIA GPUs that operate with the ``OpenCL`` or ``PTX`` backend (:ref:`NVIDIA NVML Configuration <nvidia_nvml_configuration>`).
Additionally, it is integrated with the `oneAPI Level Zero SYSMAN API <https://spec.oneapi.io/level-zero/latest/sysman/api.html>`__) to support power usage for Intel GPUs that operate with the ``SPIRV`` backend (:ref:`oneAPI Level Zero SYSMAN Configuration <oneapi_sysman_configuration>`).

.. _nvidia_nvml_configuration:

A) NVIDIA NVML Configuration for the OpenCL and PTX backends
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

TornadoVM can leverage the NVIDIA NVML (NVIDIA Management Library) to monitor and manage power metrics for supported NVIDIA GPUs. The NVML library allows TornadoVM to access the power consumption metric, which enhances its profiling capabilities.
To enable NVML support in TornadoVM, ensure the following:

i) The libnvidia-ml.so (Linux) or nvml.dll (Windows) library is accessible in your system's library path (default location: ``${CUDA_TOOLKIT_ROOT_DIR}/lib/x64``).
ii) The nvml.h header file is accessible (default location: ``${CUDA_INCLUDE_DIRS}``).

.. _oneapi_sysman_configuration:

B) oneAPI Level Zero SYSMAN Configuration for the SPIRV backend
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To configure TornadoVM to utilize the oneAPI Level Zero SYSMAN API, the target device must comply with the Level Zero specification.
Note that support is available for discrete Intel GPUs (e.g., Intel ARC A770), while integrated graphics are not supported.
To enable the low-level system management API, users need to export the ``ZES_ENABLE_SYSNAM`` environment variable as per the level-zero specification.

.. code:: bash

    export ZES_ENABLE_SYSMAN=1

The power metric is determined by reading energy counters at two timestamps: i) before the execution of a task, and ii) immediately after the execution. If the compute task is lightweight the energy counters may show no change, resulting in a reported power consumption close to zero. In such cases, the TornadoVM profiler will display "n/a".

Note that when, dispatching occurs through the OpenCL runtime, power metrics are not supported, and the TornadoVM profiler will again report "n/a".

4. Explanation of all values
-------------------------------

-  *COPY_IN_TIME*: OpenCL timers for copy in (host to device)
-  *COPY_OUT_TIME*: OpenCL timers for copy out (device to host)
-  *DISPATCH_TIME*: time spent for dispatching a submitted OpenCL
   command
-  *TOTAL_KERNEL_TIME*: It is the sum of all OpenCL kernel timers. For example, if a task-graph contains 2 tasks, this timer reports the sum of execution of the two kernels.
-  *TOTAL_BYTE_CODE_GENERATION*: time spent in the Tornado bytecode generation.
-  *TOTAL_TASK_GRAPH_TIME*: Total execution time. It contains all timers.
-  *TOTAL_GRAAL_COMPILE_TIME*: Total compilation with Graal (from Java. to OpenCL C / PTX)
-  *TOTAL_DRIVER_COMPILE_TIME*: Total compilation with the driver (once the OpenCL C / PTX code is generated, the time that the driver takes to generate the final binary).
-  *TOTAL_CODE_GENERATION_TIME*: Total code generation time. This value
   represents the elapsed time from the last Graal compilation phase in
   the LIR to the target backend code (e.g., OpenCL, PTX or SPIR-V).

Then, for each task within a task-graph, there are usually three timers, one device identifier and two data transfer metrics:

-  *BACKEND*: TornadoVM backend selected for the method execution on the
   target device. It could be either ``SPIRV``, ``PTX`` or ``OpenCL``.
-  *DEVICE_ID*: platform and device ID index.
-  *DEVICE*: device name as provided by the OpenCL driver.
-  *TASK_COPY_IN_SIZE_BYTES*: size in bytes of total bytes copied-in for
   a given task.
-  *TASK_COPY_OUT_SIZE_BYTES*: size in bytes of total bytes copied-out
   for a given task.
-  *TASK_COMPILE_GRAAL_TIME*: time that takes to compile a given task
   with Graal.
-  *TASK_COMPILE_DRIVER_TIME*: time that takes to compile a given task
   with the OpenCL/CUDA driver.
-  *POWER_USAGE_mW*: power consumed to execute a given task, reported in milliwatts. This metric is collected using low-level APIs (e.g., NVIDIA NVML or oneAPI Level Zero SYSMAN).
-  *TASK_KERNEL_TIME*: kernel execution for the given task (Java
   method).
-  *TASK_CODE_GENERATION_TIME*: time that takes the code generation from
   the LIR to the target backend code (e.g., SPIR-V).

When the task-graph is executed multiple times (through an execution plan), timers related to compilation will not appear in the Json time-report.
This is because the generated binary is cached and there is no compilation after the second iteration.

A) Print timers at the end of the execution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The options ``--enableProfiler silent`` print a full report only when
the method ``ts.getProfileLog()`` is called.

B) Save profiler into a file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Use the option ``--dumpProfiler <FILENAME>`` to store the profiler
output in a JSON file.

C) Parsing Json files
~~~~~~~~~~~~~~~~~~~~~

TornadoVM creates the ``profiler-app.json`` file with multiple entries
for the application (one per task-graph invocation).

TornadoVM’s distribution includes a set of utilities for parsing and
obtaining statistics:

.. code:: bash

   $ createJsonFile.py profiler-app.json output.json
   $ readJsonFile.py output.json

   ['readJsonFile.py', 'output.json']
   Processing file: output.json
   Num entries = 10
   Entry,0
       TOTAL_BYTE_CODE_GENERATION,6783852
       TOTAL_KERNEL_TIME,26560
       TOTAL_TASK_GRAPH_TIME,59962224
       COPY_OUT_TIME,32768
       COPY_IN_TIME,81920
       TaskName, s0.t0
       TASK_KERNEL_TIME,26560
       TASK_COMPILE_DRIVER_TIME,952126
       TASK_COMPILE_GRAAL_TIME,46868099
       TOTAL_GRAAL_COMPILE_TIME,46868099
       TOTAL_DRIVER_COMPILE_TIME,952126
       DISPATCH_TIME,31008
       EndEntry,0

   MEDIANS    ### Print median values for each timer
       TOTAL_KERNEL_TIME,25184.0
       TOTAL_TASK_GRAPH_TIME,955967.0
       s0.t0-TASK_KERNEL_TIME,25184.0
       COPY_IN_TIME,74016.0
       COPY_OUT_TIME,32816.0
       DISPATCH_TIME,31008.0


5. Code feature extraction for the OpenCL/PTX generated code
------------------------------------------------------------

To enable TornadoVM’s code feature extraction, use the following flag:
``-Dtornado.feature.extraction=True``.

Example:

.. code:: bash

   $ tornado --jvm="-Dtornado.feature.extraction=True" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody --params "1024 1"
   {
       "nBody": {
           "BACKEND" : "PTX",
           "DEVICE_ID": "0:2",
           "DEVICE": "GeForce GTX 1650",
           "Global Memory Loads":  "15",
           "Global Memory Stores":  "6",
           "Constant Memory Loads":  "0",
           "Constant Memory Stores":  "0",
           "Local Memory Loads":  "0",
           "Local Memory Stores":  "0",
           "Private Memory Loads":  "20",
           "Private Memory Stores":  "20",
           "Total Loops":  "2",
           "Parallel Loops":  "1",
           "If Statements":  "2",
           "Integer Comparison":  "2",
           "Float Comparison":  "0",
           "Switch Statements":  "0",
           "Switch Cases":  "0",
           "Vector Operations":  "0",
           "Integer & Float Operations":  "57",
           "Boolean Operations":  "9",
           "Cast Operations":  "2",
           "Float Math Functions":  "1",
           "Integer Math Functions":  "0"
       }
   }

A) Save features into a file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Use the option ``-Dtornado.feature.extraction=True``
``-Dtornado.features.dump.dir=FILENAME``. ``FILENAME`` can contain the
filename and the full path (e.g. features.json).

B) Send log over a socket
~~~~~~~~~~~~~~~~~~~~~~~~~

| TornadoVM allows redirecting profiling and feature extraction logs to
  a specific port. This feature can be enabled with the option
  ``-Dtornado.dump.to.ip=IP:PORT``.
| The following example redirects the profiler output to the localhost
  (127.0.0.1) and to a specified open port (2000):

.. code:: bash

   $ tornado --jvm="-Dtornado.profiler=True  -Dtornado.dump.to.ip=127.0.0.1:2000" -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params "100000"

To test that the socket streams the logs correctly, open a local server
in a different terminal with the following command:

.. code:: bash

   $ ncat -k -l 2000
