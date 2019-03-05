# Benchmarking TornadoVM


### Benchmarks 

Currently the benchmark runner script extends to the following benchmarks:

* montecarlo
* nbody
* saxpy
* sgemm
* scopy
* blackscholes
* bitset
* matrixmult
* dft

For each of the benchmarks, a Java version exists in order to obtain timing measurements. All performance and time measurements obtained through
a number of iterations (e.g. 130). Also, each benchmark can be tested for a various problem sizes varied from 256 to 16777216.

### How to run 

Go to the directory <tornadovm path>/tornado/assembly/src/bin`

Then, the run options can be found with the following command:

```!bash
$ python tornado-benchmarks.py -h
```
The output about the options should look like this:

```
Tool to execute benchmarks in TornadoVM

optional arguments:
  -h, --help         show this help message and exit
  --devices, -D      Run to all devices
  --sizes, -S        Run for all problem sizes
  --benchmarks, -BL  Print list of benchmarks
  --metrics, -M      Run for all sizes in all devices
  --skipSeq, -SS     Skip java version
  --validate, -VL    Enable result validation
  --skipPar, -SP     Skip Tornado version
  --verbose, -V      Enable verbose

```
