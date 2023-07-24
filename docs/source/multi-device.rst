.. _multi-device:

Multi Device Execution
==========================================

TornadoVM supports multi-device execution for task-graphs that contains multiple tasks without data dependencies across them.
This feature allows users to better utilize the available hardware and potentially improve the overall execution time of their applications.

TornadoVM executes on multiple devices in two modes:

1) Sequential:
    It will it will launch each independent task in a different accelerator *sequentially* (i.e, one after the other). This mode it is mainly used for debugging.
2) Concurrent:
    It will it will launch each independent task in a different accelerator *concurrently* (i.e, one Java thread per accelerator).

Prerequisites
----------------------------------------------

Before using TornadoVM's multi-device execution, make sure that you have one of the supported backends (e.g., OpenCL, PTX, and SPIRV) with at least 2 available devices.

Ensure multiple devices are available
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By running the following option you can obtain a list of the available devices, as well as their unique Tornado device ids that are required later on in this tutorial.

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


Sequential Execution on Multiple Devices
----------------------------------------------

In the following example we are going to use the a `blur filter <https://github.com/beehive-lab/TornadoVM/blob/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/compute/BlurFilter.java>`__
application that operates on three different pixels(e.g., red, blue, and green) independently as three separate tasks.
Also, from the devices shown above we going to use devices 0:0 (NVIDIA GeForce RTX 3070) and 0:1 (13th Gen Intel(R) Core(TM) i7-13700).

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

        Task info: blur.green
            Backend           : OPENCL
            Device            : 13th Gen Intel(R) Core(TM) i7-13700 CL_DEVICE_TYPE_CPU (available)
            Dims              : 2
            Global work offset: [0, 0]
            Global work size  : [24, 1]
            Local  work size  : null
            Number of workgroups  : [0, 0]


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


In the above example, the interpreter instance for the NVIDIA GeForce RTX 3070 runs in pool-1-thread-1, while the interpreter instance for the Intel Core i7-13700 runs in pool-1-thread-2.

Limitations
----------------------------------------------

* Tasks that share data dependencies can run only on a single devices.
* Batch processing can run only on a single device.
* Dynamic reconfiguration only explores single device execution.
