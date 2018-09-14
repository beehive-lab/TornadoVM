# Installation Process of Tornado SDK

### Pre-requisites

  * Maven Version 3
  * CMake 3.6 (or newer)
  * OpenCL (preferably v1.2 or newer)
  * GCC or clang/LLVM
  * Python 2.7 (>= 2.7.5)

### Tested OS
Tornado has been tested on:

  * OSx 10.13.2 (High Sierra)
  * CentOS >= 7.3
  * Fedora 21
  * Ubuntu 16.04 


## Installation

### 1. Compile Java with JVMCI-8

```bash
 $ git clone --depth 1 -b tornado https://github.com/beehive-lab/mx 
 $ export PATH=`pwd`/mx:$PATH 
 $ git clone --depth 1 -b tornado https://github.com/beehive-lab/graal-jvmci-8
 $ cd graal-jvmci-8
 $ mx build  
```

This will generate a new Java binary into the `jdk1.8.0_<your_version>/product`, e.g., `jdk1.8.0_181/product`.

### 1.2 Building Graal (optional) 

The Tornado maven installer will download `graal.jar` and `truffle-api.jar` dependencies automatically. 
These two jar files include a patch to execute Tornado. If you want to build Graal yourself, you can build it 

```bash
 $ cd ..
 $ git clone -b tornado https://github.com/beehive-lab/graal 
 $ cd graal
 $ export PATH=`pwd`/mx:$PATH 
 $ export JAVA_HOME=<path/to/JDK-JVMCI>
 $ mx build  
```

Then you will need to copy `graal.jar` and `truffle-api.jar` into the Tornado project.


### 2. Download Tornado

```bash
 $ cd ..
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

export CMAKE_ROOT=/usr            ## or <path/to/cmake/cmake-3.10.2> (see Step 4)
```

Then execute:

```bash
$ . etc/tornado.env
```


### 3. Setting default maven configuration

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

			 <!-- Your PATH TO JDK1.8-JVMCI-->
			 <jvmci.root>/home/user/jdk1.8.0_181/product</jvmci.root>
			 <!-- Your JDK1.8-JVMCI version-->
		 	 <jvmci.version>1.8.0_181</jvmci.version>

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

If the version of cmake is > 3.6 then skip the rest of this step and to to step 5.
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

NOTE: the tornado `Makefile` automatically sets the `cmake.root.dir` based on the variable `CMAKE_ROOT` set in `etc/tornado.env`

### 5. Compile Tornado

```bash
$ cd ~/tornado
$ . etc/tornado.env
$ make 
```
and done!! 


# Check Installation 

```bash
$ tornado
Usage: java [-options] class [args...]
           (to execute a class)
   or  java [-options] -jar jarfile [args...]
           (to execute a jar file)
where options include:
    -d32	  use a 32-bit data model if available
    -d64	  use a 64-bit data model if available
    -server	  to select the "server" VM
    -original	  to select the "original" VM
                  The default VM is server,
                  because you are running on a server-class machine.


    -cp <class search path of directories and zip/jar files>

...
```

__Testing__


Tornado provides a sets of unittests. You can run them using as follows:


```bash
tornado-test.py -V
```

Note: Not all of them are currently passing; expect around 4 or 5 to fail. 



