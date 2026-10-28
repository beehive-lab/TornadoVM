.. _benchmarking:

Benchmarking TornadoVM
======================

The TornadoVM benchmarks live in their own repository:
`beehive-lab/tornadovm-benchmark-suite <https://github.com/beehive-lab/tornadovm-benchmark-suite>`_.
Its `README <https://github.com/beehive-lab/tornadovm-benchmark-suite#readme>`_ has the full instructions,
the list of benchmarks, all the runner options, and how to read the results.

Each benchmark runs a sequential Java version and a TornadoVM version of the same kernel (``saxpy``, ``sgemm``,
``nbody``, ``blackscholes``, ``mandelbrot``, ``dft`` and more) on every accelerator TornadoVM finds, and
reports the time of each and the speedup of TornadoVM over Java.

Quick start
-----------

Install a JDK 25 and a TornadoVM SDK with `SDKMAN! <https://sdkman.io/sdks/tornadovm/>`_, choosing the backend
for your hardware (``metal``, ``opencl``, ``cuda`` or ``full``):

.. code:: bash

   $ sdk install tornadovm 7.3.1-jdk25plus-opencl
   $ tornado --devices

Build the suite and run a benchmark, with result validation:

.. code:: bash

   $ git clone https://github.com/beehive-lab/tornadovm-benchmark-suite.git
   $ cd tornadovm-benchmark-suite
   $ mvn package
   $ python3 bin/tornado-benchmarks.py --benchmark sgemm --validate

Run all benchmarks with their default sizes (30-60 minutes):

.. code:: bash

   $ python3 bin/tornado-benchmarks.py

Each benchmark prints one line for Java and one per device, e.g.:

.. code:: bash

   Performance: bm=sgemm-20-512-512, id=java-reference, average(ns)=1.091877e+08, median(ns)=1.055532e+08, firstIteration(ns)=1.458769e+08, best(ns)=1.021011e+08
   Performance: bm=sgemm-20-512-512, device=0:0, average(ns)=2.596025e+06, median(ns)=1.605209e+06, firstIteration(ns)=1.960350e+07, best(ns)=1.243667e+06
   , speedupAvg=..., speedupMedian=..., speedupFirstIteration=..., CV=..., deviceName= [Apple Metal] -- Apple M3 Pro

The suite also builds a JMH runner (``python3 bin/tornado-benchmarks.py --jmh``).
