# Testing TornadoVM

TornadoVM Unit Tests are located in the `tornado-unittests` module.
It contains a set of unit tests that can be run with Maven or with a custom TornadoRunner infrastructure.


## Testing TornadoVM Unit Tests with Maven and JUnit Core
Run any of the following commands from the `tornado-unittests` module.

```bash
# Basic data types
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestFloats
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestIntegers
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestDoubles
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestLong
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestShorts

# Control flow and algorithms
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestIf
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestLinearAlgebra
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.MultipleRuns
```


### Debug and Profiling Options 

```bash
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestFloats \
  -Dtornado.printKernel=True
```

#### Expected Output

**Success** 

```bash
WARNING: Using incubator modules: jdk.incubator.vector
JUnit version 4.13.2
.....
Time: 0.815

OK (5 tests)
```


**Failure**
```bash
JUnit version 4.13.2
.E
Time: 0.001
There was 1 failure:
1) testMethod(TestClass)
java.lang.AssertionError: Expected <expected> but was: <actual>
    at org.junit.Assert.fail(Assert.java:XX)
    ...

FAILURES!!!
Tests run: 1,  Failures: 1
```


## Testing TornadoVM Unit Tests with Custom TornadoRunner infrastructure    
The TornadoVM project provides a specialized test infrastructure that offers enhanced reporting, backend-aware exception handling, and comprehensive test categorization. This approach provides more detailed output and better integration with TornadoVM's heterogeneous computing environment.

**Prerequisites**

* TornadoVM built and installed with TORNADO_SDK configured
* Run from the TornadoVM root directory

#### Basic Usage

```bash
# Run all unit tests
make tests

# Alternative test command
tests:

# Check available devices
tornado --devices

# Run tests with verbose output
tornado-test --ea --verbose

# Run all tests verbosely (short form)
tornado-test -V
```

#### Running Specific Test Classes

```bash
# Run specific test class with verbose output
tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrixvector.TestMatrixVectorMultiplication

# Run specific test class from foundation package
tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestFloats

# Run compute tests
tornado-test -V uk.ac.manchester.tornado.unittests.compute.ComputeTests
```

#### Running Specific Test Methods`
```bash
# Run specific test method
tornado-test -V uk.ac.manchester.tornado.unittests.compute.ComputeTests#testNBody

# Run with kernel printing enabled
tornado-test -V --printKernel uk.ac.manchester.tornado.unittests.compute.ComputeTests#testNBody
```

#### Expected Output

**Success**

```bash
Test: class uk.ac.manchester.tornado.unittests.foundation.TestFloats
	Running test: testFloatsCopy             ................  [PASS] 
	Running test: testVectorFloatAdd         ................  [PASS] 
	Running test: testVectorFloatSub         ................  [PASS] 
	Running test: testVectorFloatMul         ................  [PASS] 
	Running test: testVectorFloatDiv         ................  [PASS] 
Test ran: 5, Failed: 0, Unsupported: 0
```


**Failure**
```bash
Running test: testMethod                 ................  [FAILED] 
		\_[REASON] Expected <10> but was: <20>
```

#### Test Options

```bash
╰─cmd ➜ tornado-test --help
usage: tornado-test [-h] [--version] [--verbose] [--ea] [--threadInfo] [--printKernel] [--junit] [--igv] [--igvLowTier] [--printBytecodes] [--debug]
                    [--fullDebug] [--live] [--quickPass] [--device DEVICE] [--printExec] [--jvm JVMFLAGS] [--enableProfiler ENABLE_PROFILER]
                    [--monitorClass MONITORCLASS]
                    [testClass]

Tool to execute tests in Tornado

positional arguments:
  testClass             testClass#method

options:
  -h, --help            show this help message and exit
  --version             Print version
  --verbose, -V         Run test in verbose mode
  --ea, --enableassertions
                        Enable Tornado assertions
  --threadInfo          Print thread information
  --printKernel, -pk    Print OpenCL kernel
  --junit               Run within JUnitCore main class
  --igv                 Dump GraalIR into IGV
  --igvLowTier          Dump OpenCL Low-TIER GraalIR into IGV
  --printBytecodes, -pbc
                        Print TornadoVM internal bytecodes
  --debug, -d           Enable the Debug mode in Tornado
  --fullDebug           Enable the Full Debug mode. This mode is more verbose compared to --debug only
  --live, -l            Visualize output in live mode (no wait)
  --quickPass, -qp      Quick pass without stress memory and output for logs in a file.
  --device DEVICE       Set an specific device. E.g `s0.t0.device=0:1`
  --printExec           Print OpenCL Kernel Execution Time
  --jvm JVMFLAGS, -J JVMFLAGS
                        Pass options to the JVM e.g. -J="-Ds0.t0.device=0:1"
  --enableProfiler ENABLE_PROFILER
                        Enable the profiler {silent|console}
  --monitorClass MONITORCLASS
                        Monitor class to monitor the JVM process. Options: outOfMemoryMonitor

```
