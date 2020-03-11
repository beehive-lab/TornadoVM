# Installing TornadoVM

**Supported Platforms**

The following table includes the platforms that TornadoVM can be executed.

| OS                         | Hardware    |
| -------------------------- | ----------- |
| CentOS >= 7.3              | Any OpenCL compatible device (GPUs and CPUs >= 1.2, FPGAs >= 1.0)           |
| Fedora >= 21               | Any OpenCL compatible device (GPUs and CPUs >= 1.2, FPGAs >= 1.0)        |
| Ubuntu >= 16.04            | Any OpenCL compatible device (GPUs and CPUs >= 1.2, FPGAs >= 1.0)        |
| Mac OS X Mojave 10.14.6    | Any OpenCL compatible device (GPUs and CPUs >= 1.2)        |
| Mac OS X Catalina 10.15.3  | Any OpenCL compatible device (GPUs and CPUs >= 1.2)          |


## 1. Installation

TornadoVM can be currently executed with the following two configurations:

  * TornadoVM with JDK 8 with JVMCI-8 support: see the installation guide [here](assembly/src/docs/11_INSTALL_WITH_JDK8.md)
  * TornadoVM with GraalVM (either with JDK 8 or JDK 11): see the installation guide [here](assembly/src/docs/10_INSTALL_WITH_GRAALVM.md)

## 2. Running Examples

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

## 3. Running Benchmarks

```bash
$ tornado uk.ac.manchester.tornado.benchmarks.BenchmarkRunner sadd
```


## 4. Running Unittests

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


## 5. IDE Code Formatter

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


## 6. TornadoVM Maven Projects

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
         <version>0.6</version>
      </dependency>
   </dependencies>
```

Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled.

#### Versions available

* 0.6
* 0.5
* 0.4
* 0.3
* 0.2   
* 0.1.0
