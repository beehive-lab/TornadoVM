# Installing TornadoVM #

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

### 1. Compile JDK 1.8 with JVMCI-8 support
TornadoVM is built by using a JDK 1.8 version with JVMCI-8 support. The directory which contains the Java binary is used as both the JAVA_HOME (Step 2) and the JVMCI root path (Step 3).

### 1.1 Steps for a Linux-based OS

```bash
 $ git clone -b tornado https://github.com/beehive-lab/mx 
 $ export PATH=`pwd`/mx:$PATH 
 $ git clone -b tornado https://github.com/beehive-lab/graal-jvmci-8
 $ cd graal-jvmci-8
 $ mx build
```

These steps will generate a new Java binary into the `jdk1.8.0_<your_version>/product`, e.g., `jdk1.8.0_131/product`. This directory is used as the JAVA_HOME (Step 2) and the JVMCI root path (Step 3).

### 1.2 Steps for Apple Mac OS X
Due to conflicts between the latest llvm-clang compiler in the Mac OS X and the current version of the JDK 1.8 used by TornadoVM, the Java binary cannot be built successfully. As a work-around, you can use [this binary](https://www.dropbox.com/s/2aguj98jg5b5yh4/jdk1.8.0_131-osx-10.11.6.tgxz?dl=0) which has been compiled in an earlier Mac OS version.

```bash
 $ wget https://www.dropbox.com/s/2aguj98jg5b5yh4/jdk1.8.0_131-osx-10.11.6.tgxz
 $ tar -xf jdk1.8.0_131-osx-10.11.6.tgxz
 $ cd jdk1.8.0_131
```

These steps produce the `jdk1.8.0_131` directory which contains the prebuilt Java binary for Mac OS X, and it is used as the JAVA_HOME (Step 2) and the JVMCI root path (Step 3).

### 2. Download TornadoVM

```bash
 $ cd ..
 $ git clone https://github.com/beehive-lab/TornadoVM tornadovm
 $ cd tornadovm
 $ vim etc/sources.env
```

Create the `etc/sources.env` file and add the following code in it **(after updating the paths to your correct ones)**:

```bash
#!/bin/bash
export JAVA_HOME=<path to 1.8 jdk with JVMCI> ## This path is produced in Step 1
export PATH=$PWD/bin/bin:$PATH    ## This directory will be automatically generated during Tornado compilation
export TORNADO_SDK=$PWD/bin/sdk   ## This directory will be automatically generated during Tornado compilation
export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see step 4)
```

This file should be loaded once after opening the command prompt for the setup of the required paths:

```bash
$ source ./etc/sources.env
```


### 3. Setting the default maven configuration

Create (or update) the file in `~/.m2/settings.xml` with the following content. Modify the `jvmci.root` with your path to JDK 1.8.0 that you built in step 1 and the `jvmci.version` with the corresponding version. 

```bash
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
	https://maven.apache.org/xsd/settings-1.0.0.xsd">
 <interactiveMode/>
 <usePluginRegistry/>
 	<offline/>
	<pluginGroups/>
	<servers/>
 	<mirrors/>
	<proxies/>
	<profiles>
	  <profile>
		<id>tornado-jvmci</id>
		<activation>
		 	<activeByDefault>true</activeByDefault>
		</activation>
		<properties>
			<!-- Your PATH TO YOUR JDK1.8-JVMCI-->
			<jvmci.root>/home/user/jdk1.8.0_<your_version>/product</jvmci.root>
			<!-- Your JDK1.8-JVMCI version-->
		 	<jvmci.version>1.8.0_<your_version></jvmci.version>
		</properties>
	   </profile>
	 </profiles>
	 <activeProfiles/>
</settings>
```


### 4. Install CMAKE (if cmake < 3.6) 

```
$ cmake -version
```

**If the version of cmake is > 3.6 then skip the rest of this step and go to Step 5.**
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

### 5. Compile TornadoVM

```bash
$ cd ~/tornadovm
$ . etc/sources.env
$ make 
```
and done!! 

## Know issues 

### For Ubuntu >= 16.04, install the package  `ocl-icd-opencl-dev`

In Ubuntu >= 16.04 CMake can cause the following error:  Could NOT find OpenCL (missing: OpenCL_LIBRARY) (found version "2.2"). Then the following package should be installed:
 
```bash 
$ apt-get install ocl-icd-opencl-dev
```

## Running Examples 

```bash
$ tornado uk.ac.manchester.tornado.examples.HelloWorld
```

Use the following command to identify the ids of the Tornado-compatible heterogeneous devices: 

```bash
tornado uk.ac.manchester.tornado.drivers.opencl.TornadoDeviceOutput
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
$ tornado -Ds0.t0.device=0:1 uk.ac.manchester.tornado.examples.HelloWorld
```

The command above will run the HelloWorld example on the integrated GPU (Intel HD Graphics).

## Running Benchmarks #

```bash
$ tornado uk.ac.manchester.tornado.benchmarks.BenchmarkRunner sadd
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

To use the TornadoVM API in our projects, you can checkout our maven repository as follows:


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
         <version>0.4</version>
      </dependency>
   </dependencies>
```

Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled. 

#### Versions available

* 0.4
* 0.3 
* 0.2   
* 0.1.0 
