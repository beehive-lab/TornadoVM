.. _multi-device:

Multi-Device Execution
==========================================

TornadoVM supports multi-device execution for task-graphs that contain multiple tasks without data dependencies between them.
This feature allows users to better utilize the available hardware and potentially improve the overall execution time of their applications.

TornadoVM executes on multiple devices in two modes:

1) Sequential:
    It will launch each independent task on a different accelerator *sequentially* (i.e., one after the other). This mode is mainly used for debugging.
2) Concurrent:
    It will launch each independent task on a different accelerator *concurrently*. In this mode, a separate Java thread per accelerator is spawned.

Prerequisites
----------------------------------------------

Before using TornadoVM's multi-device execution, make sure that you have one of the supported backends (e.g., OpenCL, PTX, and SPIRV) with at least 2 available devices.

Ensuring that multiple devices are available
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By running the following command you can obtain the list of the available devices that will be required later on in this tutorial, as well as their unique Tornado device ids.

.. code:: bash

   $ tornado --devices

    Number of Tornado drivers: 1
    Driver: OpenCL
      Total number of OpenCL devices  : 3
      Tornado device=0:0  (DEFAULT)
        OPENCL --  [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
            Global Memory Size: 7.8 GB
            Local Memory Size: 48.0 KB
            Workgroup Dimensions: 3
            Total Number of Block Threads: [1024]
            Max WorkGroup Configuration: [1024, 1024, 64]
            Device OpenCL C version: OpenCL C 1.2

      Tornado device=0:1
        OPENCL --  [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
            Global Memory Size: 62.5 GB
            Local Memory Size: 32.0 KB
            Workgroup Dimensions: 3
            Total Number of Block Threads: [8192]
            Max WorkGroup Configuration: [8192, 8192, 8192]
            Device OpenCL C version: OpenCL C 3.0

      Tornado device=0:2
        OPENCL --  [Intel(R) FPGA Emulation Platform for OpenCL(TM)] -- Intel(R) FPGA Emulation Device
            Global Memory Size: 62.5 GB
            Local Memory Size: 256.0 KB
            Workgroup Dimensions: 3
            Total Number of Block Threads: [67108864]
            Max WorkGroup Configuration: [67108864, 67108864, 67108864]
            Device OpenCL C version: OpenCL C 1.2

.. _sequential_execution:

Sequential Execution on Multiple Devices
----------------------------------------------

In the following example we are going to use the `blur filter <https://github.com/beehive-lab/TornadoVM/blob/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/compute/BlurFilter.java>`__
application that operates on three different color channels (e.g., red, blue, and green). Each colour channel is processed by a separate TornadoVM task, resulting in a TaskGraph (named `blur`) composed of three tasks (named `red`, `green` and `blue`).
Also, from the devices shown above we going to use devices 0:0 (NVIDIA GeForce RTX 3070) and 0:1 (13th Gen Intel(R) Core(TM) i7-13700).

.. code:: bash

    $ tornado --threadInfo  \
            --jvm=" -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:0" \
            -m  tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter

TornadoVM requires the user to explicitly define on which device each task will run; otherwise, TornadoVM will use its default device for the selected backend.
In the given example, we have specified the device assignments for each task of the TaskGraph blur.

* *-Dblur.red.device=0:0* This specifies that the red task of the BlurFilter will run on device 0:0 (NVIDIA GeForce RTX 3070).
* *-Dblur.green.device=0:1* This specifies that the green task of the BlurFilter will run on device 0:1 (Intel Core i7-13700).
* *-Dblur.blue.device=0:0* This specifies that the blue task of the BlurFilter will also run on device 0:0 (NVIDIA GeForce RTX 3070).

The expected output after execution is:

.. code:: bash

        Task info: blur.red
            Backend           : OPENCL
            Device            : NVIDIA GeForce RTX 3070 CL_DEVICE_TYPE_GPU (available)
            Dims              : 2
            Global work offset: [0, 0]
            Global work size  : [4000, 6000]
            Local  work size  : [32, 30, 1]
            Number of workgroups  : [125, 200]

        Task info: blur.blue
            Backend           : OPENCL
            Device            : NVIDIA GeForce RTX 3070 CL_DEVICE_TYPE_GPU (available)
            Dims              : 2
            Global work offset: [0, 0]
            Global work size  : [4000, 6000]
            Local  work size  : [32, 30, 1]
            Number of workgroups  : [125, 200]

        Task info: blur.green
            Backend           : OPENCL
            Device            : 13th Gen Intel(R) Core(TM) i7-13700 CL_DEVICE_TYPE_CPU (available)
            Dims              : 2
            Global work offset: [0, 0]
            Global work size  : [24, 1]
            Local  work size  : null
            Number of workgroups  : [0, 0]

.. _concurrent_execution:

Concurrent Execution on Multiple Devices
----------------------------------------------

In the previous example, although the tasks did not share dependencies, they still ran sequentially.
Thus, one device has been idle, while the tasks were executed one after the other.
To improve performance and run tasks concurrently on multiple devices, use the ``--enableConcurrentDevices`` flag:

.. code:: bash

    $ tornado --threadInfo \
        --enableConcurrentDevices \
        --jvm=" -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:0" \
        -m  tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter

By adding the --enableConcurrentDevices flag, one instance of the TornadoVM Interpreter per device will be spawned through a Java thread-pool, allowing all devices to run concurrently.

The expected output after execution is:

.. code:: bash

        Task info: blur.red
        Backend           : OPENCL
        Device            : NVIDIA GeForce RTX 3070 CL_DEVICE_TYPE_GPU (available)
        Dims              : 2
        Global work offset: [0, 0]
        Global work size  : [4000, 6000]
        Local  work size  : [32, 30, 1]
        Number of workgroups  : [125, 200]

    Task info: blur.green
        Backend           : OPENCL
        Device            : 13th Gen Intel(R) Core(TM) i7-13700 CL_DEVICE_TYPE_CPU (available)
        Dims              : 2
        Global work offset: [0, 0]
        Global work size  : [24, 1]
        Local  work size  : null
        Number of workgroups  : [0, 0]

    Task info: blur.blue
        Backend           : OPENCL
        Device            : NVIDIA GeForce RTX 3070 CL_DEVICE_TYPE_GPU (available)
        Dims              : 2
        Global work offset: [0, 0]
        Global work size  : [4000, 6000]
        Local  work size  : [32, 30, 1]
        Number of workgroups  : [125, 200]



How to debug
----------------------------------------------

Previously, we enabled debug information solely to display the thread and device configuration for each task.
TornadoVM can dump additional information to help developers to trace where the code is executed.

To access this information, you need to include the ``--printBytecodes`` flag in the above example from Section (:ref:`concurrent_execution`). By adding this flag, the following output will be displayed in conjunction with the thread information:

.. code:: bash

    $ tornado --threadInfo \
        --printBytecodes \
        --enableConcurrentDevices \
        --jvm=" -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:0" \
        -m  tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter

The expected output after execution is:

.. code:: bash

    Interpreter instance running bytecodes for:   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 Running in thread:  pool-1-thread-1
    bc:  ALLOC [I@2ffe106e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
    bc:  ALLOC [I@705e1b5b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
    bc:  ALLOC [F@63f945a3 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
    bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x2ffe106e] [I@2ffe106e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
    bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x63f945a3] [F@63f945a3 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
    bc:  LAUNCH  task blur.red - compute on  [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070, size=0, offset=0 [event list=0]
    bc:  ALLOC [I@738395e4 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
    bc:  ALLOC [I@1d78beeb on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
    bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x738395e4] [I@738395e4 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
    bc:  LAUNCH  task blur.blue - compute on  [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070, size=0, offset=0 [event list=2]
    bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x705e1b5b] [I@705e1b5b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=3]
    bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x1d78beeb] [I@1d78beeb on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=5]
    bc:  DEALLOC [0x2ffe106e] [I@2ffe106e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
    bc:  DEALLOC [0x705e1b5b] [I@705e1b5b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
    bc:  DEALLOC [0x63f945a3] [F@63f945a3 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
    bc:  DEALLOC [0x738395e4] [I@738395e4 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
    bc:  DEALLOC [0x1d78beeb] [I@1d78beeb on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
    bc:  BARRIER  event-list 17
    bc:  END

    Interpreter instance running bytecodes for:   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 Running in thread:  pool-1-thread-2
    bc:  ALLOC [I@41ac3343 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
    bc:  ALLOC [I@16c36388 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
    bc:  ALLOC [F@63f945a3 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
    bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x41ac3343] [I@41ac3343 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=-1]
    bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x63f945a3] [F@63f945a3 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=-1]
    bc:  LAUNCH  task blur.green - compute on  [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700, size=0, offset=0 [event list=1]
    bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x16c36388] [I@16c36388 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=4]
    bc:  DEALLOC [0x41ac3343] [I@41ac3343 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
    bc:  DEALLOC [0x16c36388] [I@16c36388 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
    bc:  DEALLOC [0x63f945a3] [F@63f945a3 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
    bc:  BARRIER  event-list 17
    bc:  END

Let's take a closer look at the first line: Interpreter instance running bytecodes for: [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 Running in thread: pool-1-thread-1.

This line reveals details about the TornadoVM interpreter's operation. We observe that we have two separate instances of the TornadoVM interpreter, each running independently within distinct Java threads. One instance operates within pool-1-thread-1, while the other operates in pool-1-thread-2.
In the sequential execution scenario showcased earlier in this tutorial (:ref:`sequential_execution`), we would expect all instances of the TornadoVM interpreter to run from the main Java thread.

This distinction is essential as it helps us understand how TornadoVM's bytecodes are executed in parallel, while also efficiently utilizing available hardware resources, such as the NVIDIA GeForce RTX 3070 GPU and the 13th Gen Intel(R) Core(TM) i7-13700 CPU (based on the earlier debug output).

By comprehending these details, developers gain valuable information on how TornadoVM efficiently harnesses multi-threading capabilities.
The feature of running multiple tasks on multiple devices results in enhanced performance and overall system efficiency.

Not Supported
----------------------------------------------

* Tasks that share data dependencies can run only on a single device.
* Batch processing can run only on a single device.
* Dynamic reconfiguration only explores single device execution.
