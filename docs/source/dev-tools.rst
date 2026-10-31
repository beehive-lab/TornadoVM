.. _dev-tools:

Developer Tools
================

Beyond the compiler and runtime, the TornadoVM ecosystem includes a small set of tools that help you write, debug and profile TornadoVM applications. All of them are open-source and maintained by the `beehive-lab <https://github.com/beehive-lab>`_ organization. An up-to-date overview is also available at `tornadovm.org/tooling <https://www.tornadovm.org/tooling>`_.

.. _profiler:

TornadoVM Profiler (built-in)
------------------------------

The TornadoVM profiler is shipped with TornadoVM itself and can be enabled either from the command line (via a flag from the ``tornado`` command), or via an ``ExecutionPlan`` in the source code.

1. Enable the Profiler from the Command Line
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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
               "BACKEND": "OPENCL",
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
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The profiler can query low-level system management API implementations to obtain the power usage of an executed TornadoVM task.
More specifically, TornadoVM is integrated with the `NVIDIA NVML API <https://docs.nvidia.com/deploy/nvml-api/index.html>`__ to support power usage for NVIDIA GPUs that operate with the ``OpenCL`` or ``CUDA`` backend (:ref:`NVIDIA NVML Configuration <nvidia_nvml_configuration>`).

.. _nvidia_nvml_configuration:

A) NVIDIA NVML Configuration for the OpenCL and CUDA backends
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

TornadoVM can leverage the NVIDIA NVML (NVIDIA Management Library) to monitor and manage power metrics for supported NVIDIA GPUs. The NVML library allows TornadoVM to access the power consumption metric, which enhances its profiling capabilities.
To enable NVML support in TornadoVM, ensure the following:

i) The libnvidia-ml.so (Linux) or nvml.dll (Windows) library is accessible in your system's library path (default location: ``${CUDA_TOOLKIT_ROOT_DIR}/lib/x64``).
ii) The nvml.h header file is accessible (default location: ``${CUDA_INCLUDE_DIRS}``).

The power metric is determined by reading energy counters at two timestamps: i) before the execution of a task, and ii) immediately after the execution. If the compute task is lightweight the energy counters may show no change, resulting in a reported power consumption close to zero. In such cases, the TornadoVM profiler will display "n/a".

Note that when dispatching occurs through the OpenCL runtime, power metrics are not supported, and the TornadoVM profiler will again report "n/a".

4. Explanation of all values
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  *COPY_IN_TIME*: OpenCL timers for copy in (host to device)
-  *COPY_OUT_TIME*: OpenCL timers for copy out (device to host)
-  *DISPATCH_TIME*: time spent for dispatching a submitted OpenCL
   command
-  *TOTAL_KERNEL_TIME*: It is the sum of all OpenCL kernel timers. For example, if a task-graph contains 2 tasks, this timer reports the sum of execution of the two kernels.
-  *TOTAL_BYTE_CODE_GENERATION*: time spent in the Tornado bytecode generation.
-  *TOTAL_TASK_GRAPH_TIME*: Total execution time. It contains all timers.
-  *TOTAL_GRAAL_COMPILE_TIME*: Total compilation with Graal (from Java. to OpenCL C / CUDA C)
-  *TOTAL_DRIVER_COMPILE_TIME*: Total compilation with the driver (once the OpenCL C / CUDA C code is generated, the time that the driver takes to generate the final binary).
-  *TOTAL_CODE_GENERATION_TIME*: Total code generation time. This value
   represents the elapsed time from the last Graal compilation phase in
   the LIR to the target backend code (e.g., OpenCL or CUDA).

Then, for each task within a task-graph, there are usually three timers, one device identifier and two data transfer metrics:

-  *BACKEND*: TornadoVM backend selected for the method execution on the
   target device. It could be either ``CUDA`` or ``OpenCL``.
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
-  *POWER_USAGE_mW*: power consumed to execute a given task, reported in milliwatts. This metric is collected using low-level APIs (e.g., NVIDIA NVML).
-  *TASK_KERNEL_TIME*: kernel execution for the given task (Java
   method).
-  *TASK_CODE_GENERATION_TIME*: time that takes the code generation from
   the LIR to the target backend code (e.g., OpenCL).

When the task-graph is executed multiple times (through an execution plan), timers related to compilation will not appear in the Json time-report.
This is because the generated binary is cached and there is no compilation after the second iteration.

A) Print timers at the end of the execution
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The options ``--enableProfiler silent`` print a full report only when
the method ``ts.getProfileLog()`` is called.

B) Save profiler into a file
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Use the option ``--dumpProfiler <FILENAME>`` to store the profiler
output in a JSON file.

C) Parsing Json files
^^^^^^^^^^^^^^^^^^^^^^^

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


TornadoVMPulse (profiling dashboard)
--------------------------------------

`TornadoVMPulse <https://github.com/beehive-lab/TornadoVMPulse>`_ is a Streamlit dashboard for the **profiler** output described above (JSON/log/CSV): kernel time distributions, memory and power usage, copy-in/out metrics, and a sunburst view per task-graph.

.. code-block:: bash

   git clone https://github.com/beehive-lab/TornadoVMPulse.git
   cd TornadoVMPulse
   pip install -r requirements.txt
   streamlit run src/app.py

Then upload the profiler log file (e.g. produced via ``--dumpProfiler profiler-app.json``, described above) through the web UI.

TornadoInsight (IntelliJ plugin)
----------------------------------

`TornadoInsight <https://github.com/beehive-lab/tornado-insight>`_ is an IntelliJ IDEA plugin that catches TornadoVM-unsupported Java constructs (unsupported data types, exceptions, recursion, native calls, ``assert`` statements, ...) as you type, and lets you dynamically run and debug a single TornadoVM task straight from the IDE, without hand-writing a ``TaskGraph`` and main method.

* Install from the `JetBrains Marketplace <https://plugins.jetbrains.com/plugin/23309-tornadoinsight>`_, or build it yourself:

  .. code-block:: bash

     git clone --recurse-submodules https://github.com/beehive-lab/tornado-insight.git
     cd tornado-insight
     ./gradlew clean build
     # Install the generated zip from build/distributions via
     # IntelliJ > Help > Find Action > "Install plugin from disk"

* Requires TornadoVM >= 1.0 and JDK >= 21. Configure the TornadoVM installation directory and JDK path under *Settings > TornadoInsight*.

TornadoViz (bytecode visualizer)
------------------------------------

`TornadoViz <https://github.com/beehive-lab/tornadoviz>`_ visualizes TornadoVM **bytecode execution logs**: task-graph dependencies, memory operations (allocations, transfers, deallocations) and object lifecycles, plus a searchable view of the raw bytecode operations. Use it to spot bytecode-level bottlenecks and unexpected data-copy or object-retention patterns.

.. code-block:: bash

   git clone https://github.com/beehive-lab/tornadoviz.git
   cd tornadoviz
   pip install -r requirements.txt
   streamlit run tornado-visualizer-fixed.py

Feed it the bytecode log produced by running with ``--printBytecodes``.

Third-Party Profilers
-------------------------

* **NVIDIA Nsight Systems** — timeline and kernel-level profiling for TornadoVM applications running on the CUDA backend.
* **Intel VTune Profiler** — hotspot detection and low-level metrics for the OpenCL backend.
