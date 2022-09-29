# Installing TornadoVM with GraalVM 22.2.0

**Pre-requisites**

  * Maven Version >= 3.6.3
  * CMake 3.6 (or newer)
  * At least one of:
    * OpenCL: GPUs and CPUs >= 2.1, FPGAs >= 1.0
    * CUDA 9.0 +
    * Level Zero >= 1.2
  * GCC or clang/LLVM (GCC >= 9.0)
  * Python (>= 3.0)

  For Mac OS X users: the OpenCL support for your Apple model can be confirmed [here](https://support.apple.com/en-gb/HT202823).


### 1. Download GraalVM Community Edition 22.2.0

GraalVM 22.2.0 builds are available to download at [https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-22.2.0](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-22.2.0).

The examples below show how to download and extract GraalVM based on JDK 11 and 17 for Linux.

* Example for GraalVM based on JDK 11:
```bash
$ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java11-linux-amd64-22.2.0.tar.gz
$ tar -xf graalvm-ce-java11-linux-amd64-22.2.0.tar.gz
```
* Example for GraalVM based on JDK 17:
```bash
$ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
$ tar -xf graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
```


The Java binary will be found in the `graalvm-ce-java{JDK_VERSION}-22.2.0` directory. This directory is used as the JAVA_HOME (See step 2).


For OSX:

* Example for GraalVM based on JDK 11:
```bash
$ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java11-darwin-amd64-22.2.0.tar.gz
```

* Example for GraalVM based on JDK 17:
```bash
$ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-darwin-amd64-22.2.0.tar.gz
```

then `untar` it to the OSX standard JDK location `/Library/Java/JavaVirtualMachines/` or to a folder of your choice.


### 2. Download TornadoVM

```bash
 $ cd ..
 $ git clone https://github.com/beehive-lab/TornadoVM tornadovm
 $ cd tornadovm
```

Create/edit your configuration file:
```bash
$ vim etc/sources.env
```

The first time you need to create the `etc/sources.env` file and add the following code in it **(after updating the paths to your correct ones)**:

```bash
#!/bin/bash
export JAVA_HOME=<path to GraalVM 22.2.0 jdk> ## This path is produced in Step 1
export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)
```

This file should be loaded once after opening the command prompt for the setup of the required paths:

```bash
$ source ./etc/sources.env
```
For OSX: the exports above may be added to `~/.profile`

### 3. Install CMAKE (if cmake < 3.6)

#### For Linux:
```
$ cmake -version
```

**If the version of cmake is > 3.6 then skip the rest of this step and go to Step 4.**
Otherwise try to install cmake.

For simplicity it might be easier to install cmake in your home directory.
  * Redhat Enterprise Linux / CentOS use cmake v2.8
  * We require a newer version so that OpenCL is configured properly.

```bash
$ cd ~/Downloads
$ wget https://cmake.org/files/v3.10/cmake-3.10.1-Linux-x86_64.tar.gz
$ cd ~/opt
$ tar -tvf ~/Downloads/cmake-3.10.1-Linux-x86_64.tar.gz
$ mv cmake-3.10.1-Linux-x86_64 cmake-3.10.1
$ export PATH=$HOME/opt/cmake-3.10.1/bin/:$PATH
$ cmake -version
cmake version 3.10.1
```

Then export `CMAKE_ROOT` variable to the cmake installation. You can add it to the `./etc/sources.env` file.

```bash
$ export CMAKE_ROOT=/opt/cmake-3.10.1
```

#### For OSX:

Install cmake:
```bash
$ brew install cmake
```
then

```bash
export CMAKE_ROOT=/usr/local
```
which can be added to `~/.profile`

### 4. Compile TornadoVM

```bash
$ cd ~/tornadovm
$ . etc/sources.env
```

To build with GraalVM and JDK 11 and JDK 17:

```bash
$ make graal-jdk-11-plus BACKEND={ptx,opencl}
```

and done!!


## Running with Graal JDK >= 11


TornadoVM uses modules:

To run examples:

```bash
$ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D --params "512"
```

To run benchmarks:

```bash
$ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner --params "dft"
```

To run individual tests:

```bash
tornado --jvm="-Dtornado.unittests.verbose=True -Xmx6g" -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner --params="uk.ac.manchester.tornado.unittests.arrays.TestArrays"
```


## Known issues

##### For Ubuntu >= 16.04, install the package  `ocl-icd-opencl-dev`

In Ubuntu >= 16.04 CMake can cause the following error:  Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2"). Then the following package should be installed:

```bash
$ apt-get install ocl-icd-opencl-dev
```
