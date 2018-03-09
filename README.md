# Installing Tornado #

### Pre-requisites

  * Maven Version 3
  * CMake 3.6 (or newer)
  * OpenCL (preferably v1.2 or newer)
  * GCC or clang/LLVM
  * Python 2.7 (>= 2.7.5)
  * Tornado OpenJDK 1.8.0_131 (jdk1.8.0_131_x86.tgz)

### Tested OS
Tornado has been tested on:

  * OSx 10.13.2 (High Sierra)
  * CentOS 7.4
  * Fedora 21
  * Ubuntu 16.4 


## Installation

```bash
 $ git clone https://github.com/beehive-lab/tornado.git tornado
 $ cd tornado
 $ vim etc/tornado.env
```

Copy and paste the following - but update paths into the etc/tornado.env file:

```bash
#!/bin/bash
export JAVA_HOME=<path to jvmci 8 jdk with JVMCI>
export PATH=$PWD/bin/bin:$PATH    ## We will create this directory during Tornado compilation
export TORNADO_SDK=$PWD/bin/sdk   ## We will create this directory during Tornado compilation

## If CMAKE is needed (See step 2)
export CMAKE_ROOT=<path/to/cmake/cmake-3.10.2>
```

Then execute:

```bash
$ . etc/tornado.env
```


### 1. Setting default maven configuration

Create (or update) the file in `~/.m2/settings.xml` with the following content. Modify the `jvmci.root` with your path to JDK 1.8.0_131.

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

			 <!-- Your PATH TO JDK1.8.0_131-->
			 <jvmci.root>/home/user/jdk1.8.0_131</jvmci.root>

		 	 <jvmci.version>1.8.0_131</jvmci.version>

		 </properties>
	 </profile>
	 </profiles>
	 <activeProfiles/>
</settings>

```


### 2. Install CMAKE (if cmake < 3.6) 

```
$ cmake -version
```

If the version of cmake is > 3.6 then skip the rest of this step and to to step 3.
Otherwise try in install cmake.

For simplicity it might be easier to install cmake in your home
directory.
  * Redhat Enterprise Linux / CentOS use cmake v2.8 
  * We need a newer version so that OpenCL is configured properly.

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

### 3. Compile Tornado

```bash
$ cd ~/tornado
$ mvn package 
$ bash bin/updatePATHS.sh
```
and done!! 

Alternately, you can just execute `make`

```bash
$ make
```

and done!! 

If you install a new version of Cmake (e.g in CentOS) you need to pass a variable to the mvn package:

```bash 
$ mvn -Dcmake.root.dir=$HOME/opt/cmake-3.10.1/ package
```

and done!! 

NOTE: the Makefile autoamtically sets the `cmake.root.dir` based on the variable `CMAKE_ROOT` set in `etc/tornado.env`




## Running Examples #

```bash
$ . etc/tornado.env
$ tornado uk.ac.manchester.tornado.examples.HelloWorld
```

Use the following option to identify id for Tornado devices: 

```bas
tornado uk.ac.manchester.tornado.drivers.opencl.TornadoDeviceOutput
```
Tornado device output corresponds to:
```bash
Tornado device=<driverNumber>:<deviceNumber>
```
Example output:
```bash
Number of Tornado drivers: 1
Number of devices: 2

Tornado device=0:0
Intel(R) OpenCL -- Intel(R) HD Graphics
Tornado device=0:1
Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
```

To run on a specific device user the following option:

```bash
 -D<s>.<t>.device=<driverNumber>:<deviceNumber>
```

Where s is the schedule task name and t is the task name.

For example:

```bash
$ tornado -Ds0.t0.device=0:1 uk.ac.manchester.tornado.examples.HelloWorld
```

## Running Benchmarks #

```bash
$ tornado uk.ac.manchester.tornado.benchmarks.BenchmarkRunner sadd
```


## Running Unittests

To run all unittest in Tornado:

```bash
make tests 

```

To run a separated unittest class:

```bash
$  tornado-test.py uk.ac.manchester.tornado.unittests.TestHello
```

Also, it can be executed in verbose mode:

```bash
$ tornado-test.py --verbose uk.ac.manchester.tornado.unittests.TestHello
```

To test just a method of a unittest class:

```bash
$ tornado-test.py --verbose uk.ac.manchester.tornado.unittests.TestHello#helloWorld
```


## IDE Code Formatter

### Using Eclipse and Netbeans

The code formatter in Eclipse is automatic after generating the setting files.

```bash
$ python scripts/eclipseSetup.py
```

For Netbeans, the Eclipse Formatter Plugin is needed.

### Using IntelliJ 

Install plugins:
 * Eclipse Code Formatter
 * Save Actions 

Then :
 1. Open File > Settings > Eclipse Code Formatter
 2. Check the Use the Eclipse code formatter radio button
 2. Set Eclipse Java Formatter config file to the XML file stored in /scripts/templates/eclise-settings/Tornado.xml
 3. Set Java formatter profile to Tornado

