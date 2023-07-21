.. _multi-device:

Multi Device Execution
==========================================

TornadoVM enables you to execute a Taskgraph containing multiple independent tasks (tasks without data dependencies) either serially or concurrently across multiple devices.
This feature allows users to better utilize the available hardware and potentially improve the overall execution time of their applications.

Prerequisites
----------------------------------------------

Before using TornadoVM's multi-device execution, make sure that you have one of the supported backends (e.g., OpenCL, PTX, and SPIRV) enabled and that there are multiple available devices.

Ensure multiple device are available
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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


Serial Execution on Multiple Devices
----------------------------------------------

In the following example we are going to use the a blur filter application that operates on three different pixels(e.g., red, blue, and green) independently as three separate tasks.
Also, from the devices shown above we going to use devices 0:0 (NVIDIA GPU) and 0:1 (Intel CPU)

.. code:: bash

    $ tornado --threadInfo --printBytecodes  \
            --jvm=" -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:0" \
            -m  tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter

TornadoVM requires the user to explicitly define on which device each task will run; otherwise, TornadoVM will use its default device for the selected backend.
In the given example, we have specified the device assignments for each task of the TaskGraph blur.

* *-Dblur.red.device=0:0* This specifies that the red task of the BlurFilter will run on device 0:0 (NVIDIA GeForce RTX 3070).
* *-Dblur.green.device=0:1* This specifies that the green task of the BlurFilter will run on device 0:1 (Intel Core i7-13700).
* *-Dblur.blue.device=0:0* This specifies that the blue task of the BlurFilter will also run on device 0:0 (NVIDIA GeForce RTX 3070).

The expected output after execution:

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

        Interpreter instance running bytecodes for:   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 Running in thread:  AWT-EventQueue-0
        bc:  ALLOC [I@7e450a2e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
        bc:  ALLOC [I@5e56bc21 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
        bc:  ALLOC [F@4c6fb76b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
        bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x7e450a2e] [I@7e450a2e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
        bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x4c6fb76b] [F@4c6fb76b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
        bc:  LAUNCH  task blur.red - compute on  [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070, size=0, offset=0 [event list=0]
        bc:  ALLOC [I@418a2c03 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
        bc:  ALLOC [I@8104484 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0
        bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x418a2c03] [I@418a2c03 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=-1]
        bc:  LAUNCH  task blur.blue - compute on  [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070, size=0, offset=0 [event list=2]
        bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x5e56bc21] [I@5e56bc21 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=3]
        bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x8104484] [I@8104484 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070 , size=0, offset=0 [event list=5]
        bc:  DEALLOC [0x7e450a2e] [I@7e450a2e on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
        bc:  DEALLOC [0x5e56bc21] [I@5e56bc21 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
        bc:  DEALLOC [0x4c6fb76b] [F@4c6fb76b on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
        bc:  DEALLOC [0x418a2c03] [I@418a2c03 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
        bc:  DEALLOC [0x8104484] [I@8104484 on   [NVIDIA CUDA] -- NVIDIA GeForce RTX 3070
        bc:  BARRIER  event-list 17
        bc:  END


        Task info: blur.green
            Backend           : OPENCL
            Device            : 13th Gen Intel(R) Core(TM) i7-13700 CL_DEVICE_TYPE_CPU (available)
            Dims              : 2
            Global work offset: [0, 0]
            Global work size  : [24, 1]
            Local  work size  : null
            Number of workgroups  : [0, 0]

        Interpreter instance running bytecodes for:   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 Running in thread:  AWT-EventQueue-0
        bc:  ALLOC [I@17c4bb21 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
        bc:  ALLOC [I@705e1b5b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
        bc:  ALLOC [F@4c6fb76b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0
        bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x17c4bb21] [I@17c4bb21 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=-1]
        bc:  TRANSFER_HOST_TO_DEVICE_ONCE  [Object Hash Code=0x4c6fb76b] [F@4c6fb76b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=-1]
        bc:  LAUNCH  task blur.green - compute on  [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700, size=0, offset=0 [event list=1]
        bc:  TRANSFER_DEVICE_TO_HOST_ALWAYS [0x705e1b5b] [I@705e1b5b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700 , size=0, offset=0 [event list=4]
        bc:  DEALLOC [0x17c4bb21] [I@17c4bb21 on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
        bc:  DEALLOC [0x705e1b5b] [I@705e1b5b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
        bc:  DEALLOC [0x4c6fb76b] [F@4c6fb76b on   [Intel(R) OpenCL] -- 13th Gen Intel(R) Core(TM) i7-13700
        bc:  BARRIER  event-list 17
        bc:  END


Concurrent Execution on Multiple Devices
----------------------------------------------

In the previous example, although the tasks did not share dependencies, they still ran serially, with one device idle while the other executed.
To improve performance and run tasks concurrently on multiple devices, use the --enableConcurrentDevices flag:

.. code:: bash

    $ tornado --threadInfo --enableConcurrentDevices \
        --printBytecodes \
        --jvm=" -Dblur.red.device=0:0 -Dblur.green.device=0:1 -Dblur.blue.device=0:0" \
        -m  tornado.examples/uk.ac.manchester.tornado.examples.compute.BlurFilter

By adding the --enableConcurrentDevices flag, one VM per device will be spawned through a Java thread-pool, allowing both devices to run concurrently.

The expected output after execution:

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

You can validate the concurrent execution by examining the thread pool IDs in the output.
For example, in the above example, the interpreter instance for the NVIDIA GeForce RTX 3070 runs in pool-1-thread-1, while the interpreter instance for the Intel Core i7-13700 runs in pool-1-thread-2.

Limitations
----------------------------------------------
