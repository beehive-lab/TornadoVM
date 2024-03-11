.. _examples:

Running Examples and Benchmarks
##################################


Running TornadoVM Programs
------------------------------

TornadoVM includes a tool for launching applications from the command-line:

.. code-block:: bash 

  $ tornado --help
  usage: tornado [-h] [--version] [-version] [--debug] [--threadInfo] [--igv] [--igvLowTier] [--printKernel] [--printBytecodes] [--enableProfiler ENABLE_PROFILER] [--dumpProfiler DUMP_PROFILER] [--printJavaFlags] [--devices] [--ea]
               [--module-path MODULE_PATH] [--classpath CLASSPATH] [--jvm JVM_OPTIONS] [-m MODULE_APPLICATION] [-jar JAR_FILE] [--params APPLICATION_PARAMETERS]
               [application]

  Tool for running TornadoVM Applications. This tool sets all Java options for enabling TornadoVM.

  positional arguments:
    application

  optional arguments:
    -h, --help            show this help message and exit
    --version             Print version of TornadoVM
    -version              Print JVM Version
    --debug               Enable debug mode
    --threadInfo          Print thread deploy information per task on the accelerator
    --igv                 Debug Compilation Graphs using Ideal Graph Visualizer (IGV)
    --igvLowTier          Debug Low Tier Compilation Graphs using Ideal Graph Visualizer (IGV)
    --printKernel, -pk    Print generated kernel (OpenCL, PTX or SPIR-V)
    --printBytecodes, -pc
                          Print the generated TornadoVM bytecodes
    --enableProfiler ENABLE_PROFILER
                          Enable the profiler {silent|console}
    --dumpProfiler DUMP_PROFILER  
                          Dump the profiler to a file
    --printJavaFlags      Print all the Java flags to enable the execution with TornadoVM
    --devices             Print information about the accelerators available
    --ea, -ea             Enable assertions
    --module-path MODULE_PATH
                          Module path option for the JVM
    --classpath CLASSPATH, -cp CLASSPATH, --cp CLASSPATH
                          Set class-path
    --jvm JVM_OPTIONS, -J JVM_OPTIONS
                          Pass Java options to the JVM. Use without spaces: e.g., --jvm="-Xms10g" or -J"-Xms10g"
    -m MODULE_APPLICATION
                          Application using Java modules
    -jar JAR_FILE         Main Java application in a JAR File
    --params APPLICATION_PARAMETERS
                        Command-line parameters for the host-application. Example: --params="param1 param2..."


Some examples: 


.. code-block:: bash 

  $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D


Use the following command to identify the ``ids`` of the devices that are being discovered by TornadoVM:

.. code-block:: bash 

  $ tornado --devices


Every device discovered by TornadoVM is associated with a pair of ``id`` numbers that correspond to the type of driver and the specific device, as follows:


.. code-block:: bash 

  Tornado device=<driverNumber>:<deviceNumber>


Example output:


.. code-block:: bash 

  Number of Tornado drivers: 2
  Total number of PTX devices  : 1
  Tornado device=0:0
    PTX -- GeForce GTX 1650
      Global Memory Size: 3.8 GB
      Local Memory Size: 48.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [1024, 1024, 64]
      Device OpenCL C version: N/A

  Total number of OpenCL devices  : 4
  Tornado device=1:0
    NVIDIA CUDA -- GeForce GTX 1650
      Global Memory Size: 3.8 GB
      Local Memory Size: 48.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [1024, 1024, 64]
      Device OpenCL C version: OpenCL C 1.2

  Tornado device=1:1
    Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
      Global Memory Size: 24.8 GB
      Local Memory Size: 64.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [256, 256, 256]
      Device OpenCL C version: OpenCL C 2.0

  Tornado device=1:2
	  Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
		Global Memory Size: 31.0 GB
		Local Memory Size: 32.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [8192, 8192, 8192]
		Device OpenCL C version: OpenCL C 1.2

  Tornado device=1:3
	  AMD Accelerated Parallel Processing -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
		Global Memory Size: 31.0 GB
		Local Memory Size: 32.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [1024, 1024, 1024]
		Device OpenCL C version: OpenCL C 1.2


**The output might vary depending on which backends you have included in the build process. To run TornadoVM, you should see at least one device.**

To run on a specific device use the following option:

.. code-block:: bash 

  -D<g>.<t>.device=<driverNumber>:<deviceNumber>

Where ``g`` is the *TaskGraph name* and ``t`` is the *task name*.

For example running on ``driver:device`` ``1:1`` (Intel HD Graphics in our example) will look like this:

.. code-block:: bash 
  
  $ tornado --jvm="-Ds0.t0.device=1:1" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D


The command above will run the MatrixMultiplication1D example on the integrated GPU (Intel HD Graphics).


Benchmarking
--------------------------------------------

Running all benchmarks with default values
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


.. code-block:: bash 

  $ tornado-benchmarks.py
  Running TornadoVM Benchmarks
  [INFO] This process takes between 30-60 minutes
  List of benchmarks: 
         *saxpy
         *addImage
         *stencil
         *convolvearray
         *convolveimage
         *blackscholes
         *montecarlo
         *blurFilter
         *renderTrack
         *euler
         *nbody
         *sgemm
         *dgemm
         *mandelbrot
         *dft
  [INFO] TornadoVM options: -Xms24G -Xmx24G -server 
  ... 


Running a specific benchmark
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash 

  $ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner --params="sgemm"


Unittests
-----------------------------------

To run all unittests in Tornado:

.. code-block:: bash 

  $ make tests


To run an individual unittest:

.. code-block:: bash 

  $  tornado-test uk.ac.manchester.tornado.unittests.TestHello


Also, it can be executed in verbose mode:


.. code-block:: bash 

  $ tornado-test --verbose uk.ac.manchester.tornado.unittests.TestHello



To test just a method of a unittest class:


.. code-block:: bash 

  $ tornado-test --verbose uk.ac.manchester.tornado.unittests.TestHello#testHello


To see the OpenCL/PTX generated kernel for a unittest:


.. code-block:: bash 

  $ tornado-test --verbose -pk uk.ac.manchester.tornado.unittests.TestHello#testHello



To execute in debug mode:


.. code-block:: bash 

  $ tornado-test --verbose --debug uk.ac.manchester.tornado.unittests.TestHello#testHello
  task info: s0.t0
	  platform          : NVIDIA CUDA
	  device            : GeForce GTX 1050 CL_DEVICE_TYPE_GPU (available)
	  dims              : 1
	  global work offset: [0]
	  global work size  : [8]
	  local  work size  : [8]
