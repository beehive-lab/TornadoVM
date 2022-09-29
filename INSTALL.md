# Installing TornadoVM

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


**Supported Platforms**

The following table includes the platforms that TornadoVM can be executed.

| OS                         | OpenCL Backend                                             | PTX Backend | SPIR-V Backend            | 
| -------------------------- | ---------------------------------------------------------- | ----------- | ------------------------- |
| CentOS >= 7.3              | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Level-Zero >= 1.1.2       |
| Fedora >= 21               | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Level-Zero >= 1.1.2       |
| Ubuntu >= 16.04            | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Level-Zero >= 1.1.2       |
| Mac OS X Mojave 10.14.6    | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Not supported             |
| Mac OS X Catalina 10.15.3  | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Not supported             |
| Mac OS X Big Sur 11.5.1    | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Not supported             |
| Windows 10                 | OpenCL for GPUs and CPUs >= 2.1, OpenCL for FPGAs >= 1.0)  |  CUDA 9.0+  | Not supported/tested      |

Note: The SPIR-V backend is only supported for Linux OS. Besides, the SPIR-V backend with Level Zero runs on Intel HD Graphics (integrated GPUs). 

## 1. Installation

TornadoVM can be built with three compiler backends and is able to generate OpenCL, PTX and SPIR-V code. 

**Important [SPIR-V Backend Configuration]** Prior to the built with the SPIR-V backend, users have to ensure that Level Zero is installed in their system. Please follow the guidelines [here](assembly/src/docs/11_SPIRV_BACKEND_INSTALL.md).

There are two ways to install TornadoVM: 

### A) Automatic Installation

The `tornadoVMInstaller.sh` script provided in this repository will compile/download OpenJDK, `cmake` and it will build TornadoVM.
This installation script has been tested on Linux and OSx.
Additionally, this installation type will automatically trigger all dependencies, therefore it is recommended if users only need to invoke TornadoVM as a library.

Note that GraalVM Community Edition releases based on JDK8 are no longer being built for Mac OSx.

```bash
$ ./scripts/tornadovmInstaller.sh 
TornadoVM installer for Linux and OSx
$ ./script/tornadoVMInstaller.sh <JDK> <BACKENDS>
JDK (select one):
       --jdk11            : Install TornadoVM with OpenJDK 11
       --jdk17            : Install TornadoVM with OpenJDK 17
       --graal-jdk-11     : Install TornadoVM with GraalVM and JDK 11 (GraalVM 22.2.0)
       --graal-jdk-17     : Install TornadoVM with GraalVM and JDK 17 (GraalVM 22.2.0)
       --corretto-11      : Install TornadoVM with Corretto JDK 11
       --corretto-17      : Install TornadoVM with Corretto JDK 17
       --mandrel-11       : Install TornadoVM with Mandrel 22.2.0 (JDK 11)
       --mandrel-17       : Install TornadoVM with Mandrel 22.2.0 (JDK 17)
       --microsoft-jdk-11 : Install TornadoVM with Microsoft JDK 11
       --microsoft-jdk-17 : Install TornadoVM with Microsoft JDK 17
       --zulu-jdk-11      : Install TornadoVM with Azul Zulu JDK 11
       --zulu-jdk-17      : Install TornadoVM with Azul Zulu JDK 17
TornadoVM Backends:
       --opencl           : Install TornadoVM and build the OpenCL backend
       --ptx              : Install TornadoVM and build the PTX backend
       --spirv            : Install TornadoVM and build the SPIR-V backend
Help:
       --help             : Print this help
```

**NOTE** Select the desired backend:
  * `--opencl`: Enables the OpenCL backend (requires OpenCL drivers)
  * `--ptx`: Enables the PTX backend (requires NVIDIA CUDA drivers)
  * `--spirv`: Enables the SPIRV backend (requires Intel Level Zero drivers)

###### Installation 

To build TornadoVM with GraalVM and JDK 11:

```bash
## Install with Graal JDK 17 using PTX, OpenCL and SPIRV backends
./scripts/tornadovmInstaller.sh --graal-jdk-17 --opencl --ptx --spirv
```

To build TornadoVM with Red Hat Mandrel JDK 11 with OpenCL and PTX backends:

```bash
./scripts/tornadovmInstaller.sh --mandrel-11 --opencl --ptx
```

After the installation, the scripts create a directory with the TornadoVM SDK. The directory also includes a source file with all variables needed to start using TornadoVM. 

After the script finished the installation, set the env variables needed by using:

```bash
$ source source.sh
```

### B) Manual Installation

TornadoVM can be executed with the following three configurations:

  * TornadoVM with GraalVM (JDK 11 and JDK 17): see the installation guide [here](assembly/src/docs/10_INSTALL_WITH_GRAALVM.md).
  * TornadoVM with JDK11+ (e.g. OpenJDK [11-17], Red Hat Mandrel, Amazon Corretto): see the installation guide [here](assembly/src/docs/12_INSTALL_WITH_JDK11_PLUS.md).

_Note 1_: To run TornadoVM on **Windows OS**, install TornadoVM with GraalVM. More information [here](assembly/src/docs/20_INSTALL_WINDOWS_WITH_GRAALVM.md).

_Note 2_: To run TornadoVM on ARM Mali, install TornadoVM with GraalVM and JDK 11. More information [here](assembly/src/docs/18_MALI.md).


This installation type requires users to manually install the dependencies, therefore it is recommended for developing the TornadoVM.
At least one backend must be specified at build time to the `make` command:

```bash
## Choose the desired backend
$ make BACKENDS=opencl,ptx,spirv
```

## 2. Running Examples

TornadoVM includes a tool for launching applications from the command-line:

```bash
$ tornado --help
usage: tornado [-h] [--version] [-version] [--debug] [--threadInfo] [--igv] [--igvLowTier] [--printKernel] [--printBytecodes] [--enableProfiler ENABLE_PROFILER] [--dumpProfiler DUMP_PROFILER] [--printJavaFlags] [--devices] [--ea]
               [--module-path MODULE_PATH] [--classpath CLASSPATH] [--jvm JVM_OPTIONS] [-m MODULE_APPLICATION] [-jar JAR_FILE] [--params APPLICATION_PARAMETERS]
               [application]

Tool for running TornadoVM Applications. This tool sets all Java options for enabling TornadoVM.

positional arguments:
  application

optional arguments:
  -h, --help            show this help message and exit
  --version             Print version of TornadoVM
  -version              Print JVM Version
  --debug               Enable debug mode
  --threadInfo          Print thread deploy information per task on the accelerator
  --igv                 Debug Compilation Graphs using Ideal Graph Visualizer (IGV)
  --igvLowTier          Debug Low Tier Compilation Graphs using Ideal Graph Visualizer (IGV)
  --printKernel, -pk    Print generated kernel (OpenCL, PTX or SPIR-V)
  --printBytecodes, -pc
                        Print the generated TornadoVM bytecodes
  --enableProfiler ENABLE_PROFILER
                        Enable the profiler {silent|console}
  --dumpProfiler DUMP_PROFILER
                        Dump the profiler to a file
  --printJavaFlags      Print all the Java flags to enable the execution with TornadoVM
  --devices             Print information about the accelerators available
  --ea, -ea             Enable assertions
  --module-path MODULE_PATH
                        Module path option for the JVM
  --classpath CLASSPATH, -cp CLASSPATH, --cp CLASSPATH
                        Set class-path
  --jvm JVM_OPTIONS, -J JVM_OPTIONS
                        Pass Java options to the JVM. Use without spaces: e.g., --jvm="-Xms10g" or -J"-Xms10g"
  -m MODULE_APPLICATION
                        Application using Java modules
  -jar JAR_FILE         Main Java application in a JAR File
  --params APPLICATION_PARAMETERS
                        Command-line parameters for the host-application. Example: --params="param1 param2..."
```

#### Examples: 


```bash
$ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
```

Use the following command to identify the ids of the Tornado-compatible heterogeneous devices:

```bash
$ tornado --devices
```
Tornado device output corresponds to:
```bash
Tornado device=<driverNumber>:<deviceNumber>
```
Example output:
```bash
Number of Tornado drivers: 2
Total number of PTX devices  : 1
Tornado device=0:0
  PTX -- GeForce GTX 1650
      Global Memory Size: 3.8 GB
      Local Memory Size: 48.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [1024, 1024, 64]
      Device OpenCL C version: N/A

Total number of OpenCL devices  : 4
Tornado device=1:0
  NVIDIA CUDA -- GeForce GTX 1650
      Global Memory Size: 3.8 GB
      Local Memory Size: 48.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [1024, 1024, 64]
      Device OpenCL C version: OpenCL C 1.2

Tornado device=1:1
  Intel(R) OpenCL HD Graphics -- Intel(R) Gen9 HD Graphics NEO
      Global Memory Size: 24.8 GB
      Local Memory Size: 64.0 KB
      Workgroup Dimensions: 3
      Max WorkGroup Configuration: [256, 256, 256]
      Device OpenCL C version: OpenCL C 2.0

Tornado device=1:2
	Intel(R) OpenCL -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
		Global Memory Size: 31.0 GB
		Local Memory Size: 32.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [8192, 8192, 8192]
		Device OpenCL C version: OpenCL C 1.2

Tornado device=1:3
	AMD Accelerated Parallel Processing -- Intel(R) Core(TM) i7-7700HQ CPU @ 2.80GHz
		Global Memory Size: 31.0 GB
		Local Memory Size: 32.0 KB
		Workgroup Dimensions: 3
		Max WorkGroup Configuration: [1024, 1024, 1024]
		Device OpenCL C version: OpenCL C 1.2

```

**The output might vary depending on which backends you have included in the build process. To run TornadoVM, you should see at least one device.**

To run on a specific device use the following option:

```bash
 -D<s>.<t>.device=<driverNumber>:<deviceNumber>
```

Where `s` is the *TaskSchedule name* and `t` is the *task name*.

For example running on `driver:device` `1:1` (Intel HD Graphics in our example) will look like this:

```bash
$ tornado --jvm="-Ds0.t0.device=1:1" -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication1D
```

The command above will run the MatrixMultiplication1D example on the integrated GPU (Intel HD Graphics).

## 3. Running Benchmarks


###### Running all benchmarks with default values
```bash
$ tornado-benchmarks.py
Running TornadoVM Benchmarks
[INFO] This process takes between 30-60 minutes
List of benchmarks: 
       *saxpy
       *addImage
       *stencil
       *convolvearray
       *convolveimage
       *blackscholes
       *montecarlo
       *blurFilter
       *renderTrack
       *euler
       *nbody
       *sgemm
       *dgemm
       *mandelbrot
       *dft
[INFO] TornadoVM options: -Xms24G -Xmx24G -server 
....
```

###### Running a specific benchmark

```bash
$ tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner --params="sgemm"
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

To see the OpenCL/PTX generated kernel for a unittest:

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
         <version>0.14.1</version>
      </dependency>

      <dependency>
         <groupId>tornado</groupId>
         <artifactId>tornado-matrices</artifactId>
         <version>0.14.1</version>
      </dependency>
   </dependencies>
```

Notice that, for running with TornadoVM, you will need either the docker images or the full JVM with TornadoVM enabled.

#### Versions available

* 0.14.1
* 0.14
* 0.13
* 0.12
* 0.11
* 0.10
* 0.9
* 0.8
* 0.7
* 0.6
* 0.5
* 0.4
* 0.3
* 0.2   
* 0.1.0
