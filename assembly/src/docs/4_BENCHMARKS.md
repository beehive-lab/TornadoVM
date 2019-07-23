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

For each of the benchmarks, a Java version exists in order to obtain timing measurements. All performance and time measurements obtained through a number of iterations (e.g. 130). Also, each benchmark can be tested for a various problem sizes varied from 256 to 16777216.

### How to run 

Go to the directory `<tornadovm path>/bin/sdk/bin`. Then, the run options can be found with the following command:

```!bash
$ tornado-benchmarks.py -h
```
The output about the options should look like this:

```bash
usage: tornado-benchmarks.py [-h] [--devices] [--sizes] [--benchmarks]
                             [--full] [--skipSeq] [--validate] [--skipPar]
                             [--default] [--verbose]

Tool to execute benchmarks in TornadoVM With no options, it runs the medium
sizes

optional arguments:
  -h, --help     show this help message and exit
  --devices      Run to all devices
  --sizes        Run for all problem sizes
  --benchmarks   Print list of benchmarks
  --full         Run for all sizes in all devices. Including big data sizes
  --skipSeq      Skip java version
  --validate     Enable result validation
  --skipPar      Skip parallel version
  --default      Run default benchmark configuration
  --verbose, -V  Enable verbose

```


### Example

Example of running all benchmark for all devices available in your system with small and medium sizes with 50 iterations. 


```bash
$ tornado-benchmarks.py --iterations 50
```


