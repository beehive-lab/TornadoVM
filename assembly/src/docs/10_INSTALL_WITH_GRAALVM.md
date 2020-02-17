# Installing TornadoVM with GraalVM 19.3.0

### Pre-requisites

  * Maven Version 3
  * CMake 3.6 (or newer)
  * OpenCL: GPUs and CPUs >= 1.2, FPGAs >= 1.0 
  * GCC or clang/LLVM (GCC >= 5.5)
  * Python 2.7 (>= 2.7.5)
  
  For Mac OS X users: the OpenCL support for your Apple model can be confirmed [here](https://support.apple.com/en-gb/HT202823).

### Tested Operating Systems

TornadoVM has been succefully tested on the following platforms:

  * CentOS >= 7.3
  * Fedora >= 21
  * Ubuntu >= 16.04
  * Mac OS X Mojave 10.14.6


## Installation

### 1. Install GraalVM Community Edition 19.3.0

TornadoVM is built using GraalVM Community Edition 19.3.0 [[link](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-19.3.0)]. The directory which contains the Java binary is used by the `JAVA_HOME` environment variable (See step 2).

GraalVM Community Edition 19.3.0 supports both JDK8 and JDK11. This means that TornadoVM is able to run on top of both versions.

#### 1.1 Download GraalVM Community Edition 19.3.0

GraalVM 19.3.0 builds are available to download at [https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-19.3.0](https://github.com/graalvm/graalvm-ce-builds/releases/tag/vm-19.3.0).

You can then extract the zip file:

Example, download for Linux:
```bash 
$ wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz
$ tar -xf graalvm-ce-java8-linux-amd64-19.3.0.tar.gz
```

The Java binary will be found in the `graalvm-ce-java{JDK_VERSION}-19.3.0` directory. This directory is used as the JAVA_HOME (See step 2).

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
export JAVA_HOME=<path to GraalVM 19.3.0 jdk> ## This path is produced in Step 1
export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)
```

This file should be loaded once after opening the command prompt for the setup of the required paths:

```bash
$ source ./etc/sources.env
```

### 3. Install CMAKE (if cmake < 3.6) 

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
export CMAKE_ROOT=/opt/cmake-3.10.1
```

### 4. Compile TornadoVM

```bash
$ cd ~/tornadovm
$ . etc/sources.env
```

To build with GraalVM and JDK 8

```bash
$ make 
```

To build with GraalVM and JDK 11:

```bash
$ make graal-jdk-11 
```

and done!! 

## Know issues 

##### For Ubuntu >= 16.04, install the package  `ocl-icd-opencl-dev`

In Ubuntu >= 16.04 CMake can cause the following error:  Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2"). Then the following package should be installed:
 
```bash 
$ apt-get install ocl-icd-opencl-dev
```

## Running Examples

The examples and benchmarks of Tornado live in two different modules (`tornado.examples` and `tornado.benchmarks`). If you are using **JDK11** version with GraalVM 19.3.0, then you must specify the module and package in a similar manner with `java`.

```bash
# Example of using TornadoVM with JDK11
$ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.HelloWorld
```

When using GraalVM with JDK8 it is enough to specify the package only.

```bash
# Example of using TornadoVM with JDK8
$ tornado uk.ac.manchester.tornado.examples.HelloWorld
```
For simplicity, we will only provide examples of running `tornado` with GraalVM on top of JDK11. It is straightforward to adapt the command line arguments to JDK8.

Use the following command to identify the ids of the Tornado-compatible heterogeneous devices: 

```bash
tornado -m tornado.drivers.opencl/uk.ac.manchester.tornado.drivers.opencl.TornadoDeviceOutput
```
Tornado device output corresponds to:
```bash
Tornado device=<driverNumber>:<deviceNumber>
```
Example output:
```bash
Number of Tornado drivers: 1
Number of devices: 3

Tornado device=0:0
  NVIDIA CUDA -- GeForce GTX 1050
Tornado device=0:1
  Intel(R) OpenCL -- Intel(R) HD Graphics
Tornado device=0:2
  Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
```

**The output might vary depending on your OpenCL installation. To run TornadoVM, you should see at least one device.**

To run on a specific device use the following option:

```bash
 -D<s>.<t>.device=<driverNumber>:<deviceNumber>
```

Where `s` is the *schedule name* and `t` is the task name.

For example running on device [1] (Intel HD Graphics in our example) will look like this:

```bash
$ tornado -Ds0.t0.device=0:1 -m tornado.examples/uk.ac.manchester.tornado.examples.HelloWorld
```

The command above will run the HelloWorld example on the integrated GPU (Intel HD Graphics).

## Running Benchmarks #

```bash
$ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner sadd
```


## Running Unittests

To run all unittests in Tornado:

```bash
$ make tests
```

To run an individual unittest:

```bash
$  tornado-test.py uk.ac.manchester.tornado.unittests.TestHello
```

Also, it can be executed in verbose mode:

```bash
$ tornado-test.py --verbose uk.ac.manchester.tornado.unittests.TestHello
```

To test just a method of a unittest class:

```bash
$ tornado-test.py --verbose uk.ac.manchester.tornado.unittests.TestHello#testHello
```

To see the OpenCL generated kernel for a unittest:

```bash
$ tornado-test.py --verbose -pk uk.ac.manchester.tornado.unittests.TestHello#testHello
```

To execute in debug mode:

```bash
$ tornado-test.py --verbose --debug uk.ac.manchester.tornado.unittests.TestHello#testHello
task info: s0.t0
	platform          : NVIDIA CUDA
	device            : GeForce GTX 1050 CL_DEVICE_TYPE_GPU (available)
	dims              : 1
	global work offset: [0]
	global work size  : [8]
	local  work size  : [8]
```


## IDE Code Formatter

### Using Eclipse and Netbeans

The code formatter in Eclipse is automatically applied after generating the setting files.

```bash
$ mvn eclipse:eclipse
$ python scripts/eclipseSetup.py
```

For Netbeans, the Eclipse Formatter Plugin is needed.

### Using IntelliJ 

Install plugins:
 * Eclipse Code Formatter
 * Save Actions 

Then :
 1. Open File > Settings > Eclipse Code Formatter
 2. Check the `Use the Eclipse code` formatter radio button
 2. Set the Eclipse Java Formatter config file to the XML file stored in /scripts/templates/eclise-settings/Tornado.xml
 3. Set the Java formatter profile in Tornado


## TornadoVM Maven Projects

To use the TornadoVM API in your projects, you can checkout our maven repository as follows:


```xml
   <repositories>
     <repository>
       <id>universityOfManchester-graal</id>
       <url>https://raw.githubusercontent.com/beehive-lab/tornado/maven-tornadovm</url>
     </repository>
   </repositories>
  
   <dependencies>   
      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-api</artifactId>
         <version>0.5</version>
      </dependency>
   </dependencies>
```

Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled. 

#### Versions available

* 0.5
* 0.4
* 0.3 
* 0.2   
* 0.1.0 
