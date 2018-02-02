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
export GRAAL_ROOT=<path to graal.jar and truffle-api.jar>
export TORNADO_ROOT=<path to cloned git dir>
```

Then execute:

```bash
$ . etc/tornado.env
```


### 1. Setting default maven configuration

Modify the `jvmci.root` with your path to JDK 1.8.0_131

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
```

and done!! 

If you install a new version of Cmake (e.g in CentOS) you need to pass a variable to the mvn package:

```bash 
mvn -Dcmake.root.dir=$HOME/opt/cmake-3.10.1/ package
```

and done!! 

### 4. Update PATH and TORNADO_SDK

The binaries are in dist/tornado-sdk/tornado-sdk-0.0.2-SNAPSHOT-commitID/ 

```bash
export PATH=dist/tornado-sdk/tornado-sdk-0.0.2-SNAPSHOT-ada5d03/bin:$PATH
export SDK_TORNADO=dist/tornado-sdk/tornado-sdk-0.0.2-SNAPSHOT-ada5d03/
```


## Running Examples #

```bash
$ . etc/tornado.env
$ tornado tornado.examples.HelloWorld
```

To run on a specific device, use the option: 

```bash
 -D<s>.<t>.device=<driverNumber>:<deviceNumber>
```

Where s is the schedule task name and t is the task name.

For example:

```bash
$ tornado -Ds0.t0.device=0:1 tornado.examples.HelloWorld
```

## Running Benchmarks #

```bash
$ tornado tornado.benchmarks.BenchmarkRunner sadd
```


## Running Unittests

To run all unittest in Tornado:

```bash
make tests 

```

To run a separated unittest class:

```bash
$  tornado-test.py tornado.unittests.TestHello
```

Also, it can be executed in verbose mode:

```bash
$ tornado-test.py --verbose tornado.unittests.TestHello
```

To test just a method of a unittest class:

```bash
$ tornado-test.py --verbose tornado.unittests.TestHello#helloWorld
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

