# Installing TornadoVM with JDK 11+

**Pre-requisites**

  * Maven Version 3.6.3
  * CMake 3.6 (or newer)
  * At least one of:   
    * OpenCL: GPUs and CPUs >= 1.2, FPGAs >= 1.0
    * CUDA 9.0 +
  * GCC or clang/LLVM (GCC >= 5.5)
  * Python (>= 2.7)


  For Mac OS X users: the OpenCL support for your Apple model can be confirmed [here](https://support.apple.com/en-gb/HT202823).

  ###### DISCLAIMER:
  
  TornadoVM is based on the Graal compiler that depends on JVMCI (Java Virtual Machine Compiler Interface). Different JDKs come with different versions of JVMCI. Therefore, the version of the Graal compiler that TornadoVM uses might not be compatible with the JVMCI version of some JDKs. Below are listed the Java 11+ JDK distributions against which TornadoVM has been tested, but compatibility is not guaranteed.

  ```bash
  Red Hat Mandrel 11.0.12 (Mandrel 21.3.0.0 Final)
  Amazon Corretto >= 11.0.12.7
  GraalVM LabsJDK 11.0.13+7 (GraalVM 21.3.0)
  OpenJDK 11.0.13+7
  OpenJDK 17.0.1
  Microsoft Build of OpenJDK (OpenJDK 11.0.13+8)
  Microsoft Build of OpenJDK (OpenJDK 17.0.1)
  ```


### 1. Download a JDK 11+ distribution

OpenJDK distributions are available to download at [https://adoptium.net/](https://adoptium.net/).<br/>
Red Hat Mandrel releases are available at [https://github.com/graalvm/mandrel/releases](https://github.com/graalvm/mandrel/releases).<br/>
Amazon Coretto releases are available at [https://aws.amazon.com/corretto/](https://aws.amazon.com/corretto/).<br/>
Microsoft OpenJDK releases are available at [https://docs.microsoft.com/en-us/java/openjdk/download](https://docs.microsoft.com/en-us/java/openjdk/download).

After downloading and extracting the JDK distribution, point your `JAVA_HOME` variable to the JDK root.

Example:
```bash
 $ wget https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz
 $ tar xf amazon-corretto-11-x64-linux-jdk.tar.gz
 $ export JAVA_HOME=$PWD/amazon-corretto-11.0.13.8.1-linux-x64
```

### 2. Download TornadoVM

```bash
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
export JAVA_HOME=<path to JDK11+> ## This path is produced in Step 1
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

To build with a distribution of JDK 11+

```bash
$ make jdk-11-plus BACKEND={ptx,opencl}
```

and done!!


## Running with JDK 11+


TornadoVM uses modules:

To run examples:

```bash
$ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D 512
```

To run benchmarks:

```bash
$ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner dft
```

To run individual tests:

```bash
tornado -Dtornado.unittests.verbose=True -Xmx6g  -m  tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner uk.ac.manchester.tornado.unittests.arrays.TestArrays
```


## Known issues

##### For Ubuntu >= 16.04, install the package  `ocl-icd-opencl-dev`

In Ubuntu >= 16.04 CMake can cause the following error:  Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2"). Then the following package should be installed:

```bash
$ apt-get install ocl-icd-opencl-dev
```
