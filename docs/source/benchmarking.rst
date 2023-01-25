.. _benchmarking:

Benchmarking TornadoVM
======================

Benchmarks
----------------------

Currently the benchmark runner script can execute the following benchmarks:

.. code:: bash

   *saxpy
   *addImage
   *stencil
   *convolvearray
   *convolveimage
   *blackscholes
   *montecarlo
   *blurFilter
   *euler
   *renderTrack 
   *nbody
   *sgemm
   *dgemm
   *mandelbrot 
   *dft

For each benchmark, a Java version exists in order to obtain timing measurements. 
All performance and time measurements are obtained through a number of iterations (e.g. 130). 
Also, each benchmark can be tested for various array sizes ranging from ``256`` to ``16777216``.

How to run
---------------------

Go to the directory ``<tornadovm path>/bin/sdk/bin``. 
Then, the run options can be found with the following command:

.. code:: bash

   usage: tornado-benchmarks.py [-h] [--validate] [--default] [--medium]
                                [--iterations ITERATIONS] [--full]
                                [--skipSequential] [--skipParallel]
                                [--skipDevices SKIP_DEVICES] [--verbose]
                                [--printBenchmarks]

   Tool to execute benchmarks in TornadoVM. With no options, it runs all
   benchmarks with the default size

   optional arguments:
     -h, --help            show this help message and exit
     --validate            Enable result validation
     --default             Run default benchmark configuration
     --medium              Run benchmarks with medium sizes
     --iterations ITERATIONS
                           Set the number of iterations
     --full                Run for all sizes in all devices. Including big data
                           sizes
     --skipSequential      Skip java version
     --skipParallel        Skip parallel version
     --skipDevices SKIP_DEVICES
                           Skip devices. Provide a list of devices (e.g., 0,1)
     --verbose, -V         Enable verbose
     --printBenchmarks     Print the list of available benchmarks
     --jmh                 Run with JMH

Example
~~~~~~~

Example of running all benchmark for all devices available in your
system with the default data size.

.. code:: bash

   $ tornado-benchmarks.py
   Running TornadoVM Benchmarks
   [INFO] This process takes between 30-60 minutes
   [INFO] TornadoVM options: -Xms24G -Xmx24G -server
   bm=saxpy-101-16777216, id=java-reference      , average=7.604811e+06, median=7.521843e+06, firstIteration=1.179550e+07, best=7.355636e+06
   bm=saxpy-101-16777216, device=0:0  , average=1.852340e+07, median=1.708197e+07, firstIteration=2.788138e+07, best=1.612269e+07, speedupAvg=0.4106, speedupMedian=0.4403, speedupFirstIteration=0.4231, CV=10.5305%, deviceName=NVIDIA CUDA -- GeForce GTX 1050
   bm=saxpy-101-16777216, device=0:1  , average=4.503467e+07, median=4.482944e+07, firstIteration=6.696712e+07, best=4.236860e+07, speedupAvg=0.1689, speedupMedian=0.1678, speedupFirstIteration=0.1761, CV=4.7203%, deviceName=Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=saxpy-101-16777216, device=0:2  , average=2.212386e+07, median=2.129296e+07, firstIteration=3.493844e+07, best=1.975243e+07, speedupAvg=0.3437, speedupMedian=0.3533, speedupFirstIteration=0.3376, CV=7.5316%, deviceName=AMD Accelerated Parallel Processing -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=saxpy-101-16777216, device=0:3  , average=1.835022e+07, median=1.830117e+07, firstIteration=2.965289e+07, best=1.760201e+07, speedupAvg=0.4144, speedupMedian=0.4110, speedupFirstIteration=0.3978, CV=3.2015%, deviceName=Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
   bm=add-image-101-2048-2048, id=java-reference      , average=6.076920e+07, median=5.912435e+07, firstIteration=9.159228e+07, best=5.539140e+07
   bm=add-image-101-2048-2048, device=0:0  , average=2.587469e+07, median=2.560709e+07, firstIteration=6.173938e+07, best=2.399116e+07, speedupAvg=2.3486, speedupMedian=2.3089, speedupFirstIteration=1.4835, CV=5.1914%, deviceName=NVIDIA CUDA -- GeForce GTX 1050
   bm=add-image-101-2048-2048, device=0:1  , average=3.250553e+07, median=3.089569e+07, firstIteration=8.700214e+07, best=2.691534e+07, speedupAvg=1.8695, speedupMedian=1.9137, speedupFirstIteration=1.0528, CV=11.3154%, deviceName=Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=add-image-101-2048-2048, device=0:2  , average=3.061671e+07, median=3.037699e+07, firstIteration=7.024932e+07, best=2.742994e+07, speedupAvg=1.9848, speedupMedian=1.9464, speedupFirstIteration=1.3038, CV=4.3990%, deviceName=AMD Accelerated Parallel Processing -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=add-image-101-2048-2048, device=0:3  , average=2.564357e+07, median=2.512443e+07, firstIteration=6.052658e+07, best=2.316377e+07, speedupAvg=2.3698, speedupMedian=2.3533, speedupFirstIteration=1.5133, CV=4.9465%, deviceName=Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
   bm=stencil-101-1048576, id=java-reference      , average=1.841053e+05, median=1.885090e+05, firstIteration=4.734246e+06, best=1.636910e+05
   bm=stencil-101-1048576, device=0:0  , average=1.862818e+05, median=1.863900e+05, firstIteration=8.547734e+06, best=1.672090e+05, speedupAvg=0.9883, speedupMedian=1.0114, speedupFirstIteration=0.5539, CV=13.9480%, deviceName=NVIDIA CUDA -- GeForce GTX 1050
   bm=stencil-101-1048576, device=0:1  , average=1.323170e+05, median=1.272060e+05, firstIteration=7.506147e+06, best=1.057020e+05, speedupAvg=1.3914, speedupMedian=1.4819, speedupFirstIteration=0.6307, CV=12.2388%, deviceName=Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=stencil-101-1048576, device=0:2  , average=1.238349e+05, median=1.095310e+05, firstIteration=4.092201e+06, best=8.586900e+04, speedupAvg=1.4867, speedupMedian=1.7211, speedupFirstIteration=1.1569, CV=47.6368%, deviceName=AMD Accelerated Parallel Processing -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   bm=stencil-101-1048576, device=0:3  , average=2.464191e+05, median=2.296330e+05, firstIteration=4.807327e+06, best=2.218090e+05, speedupAvg=0.7471, speedupMedian=0.8209, speedupFirstIteration=0.9848, CV=12.3793%, deviceName=Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
   bm=convolve-array-100-2048-2048-5, id=java-reference      , average=2.612301e+08, median=2.609304e+08, firstIteration=4.006838e+08, best=2.544892e+08
   bm=convolve-array-100-2048-2048-5, device=0:0  , average=8.143104e+06, median=8.214443e+06, firstIteration=1.811648e+07, best=7.609697e+06, speedupAvg=32.0799, speedupMedian=31.7648, speedupFirstIteration=22.1171, CV=4.6348%, deviceName=NVIDIA CUDA -- GeForce GTX 1050
   bm=convolve-array-100-2048-2048-5, device=0:1  , average=9.842007e+07, median=9.631152e+07, firstIteration=1.018732e+08, best=9.032237e+07, speedupAvg=2.6542, speedupMedian=2.7092, speedupFirstIteration=3.9332, CV=9.3753%, deviceName=Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
   ...

Using JMH
-------------------

The ``tornado-benchmarks.py`` script is configured to use JMH.

.. code:: bash

   $ tornado-benchmarks.py --jmh    

The script runs all benchmarks using JMH. This process takes ~3.5h.

Additionally, each benchmark has a JMH configuration. Users can execute
any benchmark from the list as follows:

.. code:: bash

   $ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.<benchmark>.JMH<BENCHMARK>

This process takes ~10mins per benchmark.

For example:

.. code:: bash

   $ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.dft.JMHDFT
   # JMH version: 1.23
   ...
   Benchmark          Mode  Cnt   Score   Error  Units
   JMHDFT.dftJava     avgt    5  19.736 ± 1.589   s/op
   JMHDFT.dftTornado  avgt    5   0.155 ± 0.008   s/op