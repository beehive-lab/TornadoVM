# TornadoVM

<img align="right" width="250" height="250" src="etc/tornadoVM_Logo.jpg">

TornadoVM is a plug-in to OpenJDK and GraalVM that allows programmers to automatically run Java programs on
heterogeneous hardware. TornadoVM currently targets OpenCL-compatible devices and it runs on multi-core CPUs, dedicated
GPUs (NVIDIA, AMD), integrated GPUs (Intel HD Graphics and ARM Mali), and FPGAs (Intel and Xilinx).

#####

**Website**: [tornadovm.org](https://www.tornadovm.org)

For a quick introduction please read the following [FAQ](assembly/src/docs/15_FAQ.md).

**Current Release:** TornadoVM 0.14 - 15/06/2022 : See [CHANGELOG](assembly/src/docs/CHANGELOG.md#tornadovm-0.14)

Previous Releases can be found [here](assembly/src/docs/Releases.md)

## 1. Installation

In Linux and Mac OSx, TornadoVM can be installed automatically with the [installation script](INSTALL.md#a-automatic-installation). For example:
```bash
$ ./scripts/tornadovmInstaller.sh 
TornadoVM installer for Linux and OSx
./scripts/tornadoVMInstaller.sh <JDK> <BACKENDS>
JDK (select one):
       --jdk11            : Install TornadoVM with OpenJDK 11
       --jdk17            : Install TornadoVM with OpenJDK 17
       --graal-jdk-11     : Install TornadoVM with GraalVM and JDK 11 (GraalVM 22.1.0)
       --graal-jdk-17     : Install TornadoVM with GraalVM and JDK 17 (GraalVM 22.1.0)
       --corretto-11      : Install TornadoVM with Corretto JDK 11
       --corretto-17      : Install TornadoVM with Corretto JDK 17
       --mandrel-11       : Install TornadoVM with Mandrel 22.1.0 (JDK 11)
       --mandrel-17       : Install TornadoVM with Mandrel 22.1.0 (JDK 17)
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

Alternatively, TornadoVM can be installed either manually [from source](INSTALL.md#b-manual-installation) or 
by [using Docker](assembly/src/docs/13_INSTALL_WITH_DOCKER.md).

You can also run TornadoVM on Amazon AWS CPUs, GPUs, and FPGAs following the
instructions [here](assembly/src/docs/17_AWS.md).

## 2. Usage Instructions

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision,
physics simulations, financial applications, computational photography, and signal processing.

We have a use-case, [kfusion-tornadovm](https://github.com/beehive-lab/kfusion-tornadovm), for accelerating a
computer-vision application implemented in Java using the Tornado-API to run on GPUs.

We also have a set
of [examples](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples)
that includes NBody, DFT, KMeans computation and matrix computations.

**Additional Information**

[Documentation](assembly/src/docs)

[Benchmarks](assembly/src/docs/4_BENCHMARKS.md)

[Reductions](assembly/src/docs/5_REDUCTIONS.md)

[Execution Flags](assembly/src/docs/6_TORNADO_FLAGS.md)

[FPGA execution](assembly/src/docs/7_FPGA.md)

[Profiler Usage](assembly/src/docs/9_PROFILER.md)

## 3. Programming Model

TornadoVM exposes to the programmer task-level, data-level and pipeline-level parallelism via a light Application
Programming Interface (API). In addition, TornadoVM uses single-source property, in which the code to be accelerated and
the host code live in the same Java program.

Compute-kernels in TornadoVM can be programmed using two different approaches:

#### a) Loop-parallelism

Compute kernels are written in a sequential form (tasks programmed for a single thread execution). To express
parallelism, TornadoVM exposes two annotations that can be used in loops and parameters: a) `@Parallel` for annotating
parallel loops; and b) `@Reduce` for annotating parameters used in reductions.

The following code snippet shows a full example to accelerate Matrix-Multiplication using TornadoVM and the
loop-parallel API:

```java
public class Compute {
    private static void mxmLoop(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        TaskSchedule ts = new TaskSchedule("s0")
                .streamIn(A, B)                               // Stream data from host to device
                .task("t0", Compute::mxmLoop, A, B, C, size)  // Each task points to an existing Java method
                .streamOut(C);                                // sync arrays with the host side
        ts.execute();   // It will execute the code on the default device (e.g. a GPU)
    }
}
```

#### b) Kernel Parallelism

Another way to express compute-kernels in TornadoVM is via the kernel-parallel API. To do so, TornadoVM exposes
a `KernelContext` with which the application can directly access the thread-id, allocate memory in local memory
(shared memory on NVIDIA devices), and insert barriers. This model is similar to programming compute-kernels in OpenCL
and CUDA. Therefore, this API is more suitable for GPU/FPGA expert programmers that want more control or want to port
existing CUDA/OpenCL compute kernels into TornadoVM.

The following code-snippet shows the Matrix Multiplication example using the kernel-parallel API:

```java
public class Compute {
    private static void mxmKernel(KernelContext context, Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        int idx = context.threadIdx;
        int jdx = context.threadIdy;
        float sum = 0;
        for (int k = 0; k < size; k++) {
            sum += A.get(idx, k) * B.get(k, jdx);
        }
        C.set(idx, jdx, sum);
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        // When using the kernel-parallel API, we need to create a Grid and a Worker

        WorkerGrid workerGrid = new WorkerGrid2D(size, size);    // Create a 2D Worker
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);  // Attach the worker to the Grid
        KernelContext context = new KernelContext();             // Create a context
        workerGrid.setLocalWork(32, 32, 1);                      // Set the local-group size

        TaskSchedule ts = new TaskSchedule("s0")
                .streamIn(A, B)                                 // Stream data from host to device
                .task("t0", Compute::mxmKernel, context, A, B, C, size)  // Each task points to an existing Java method
                .streamOut(C);                                  // sync arrays with the host side
        ts.execute(gridScheduler);   // Execute with a GridScheduler
    }
}
```

Additionally, the two modes of expressing parallelism (kernel and loop parallelization) can be combined in the same
task-schedule object.

## 4. Dynamic Reconfiguration

Dynamic reconfiguration is the ability of TornadoVM to perform live task migration between devices, which means that
TornadoVM decides where to execute the code to increase performance (if possible). In other words, TornadoVM switches
devices if it can detect that a specific device can yield better performance (compared to another). With the
task-migration, the TornadoVM's approach is to only switch device if it detects an application can be executed faster
than the CPU execution using the code compiled by C2 or Graal-JIT, otherwise it will stay on the CPU. So TornadoVM can
be seen as a complement to C2 and Graal. This is because there is no single hardware to best execute all workloads
efficiently. GPUs are very good at exploiting SIMD applications, and FPGAs are very good at exploiting pipeline
applications. If your applications follow those models, TornadoVM will likely select heterogeneous hardware. Otherwise,
it will stay on the CPU using the default compilers (C2 or Graal).

To use the dynamic reconfiguration, you can execute using TornadoVM policies. For example:

```java
// TornadoVM will execute the code in the best accelerator.
ts.execute(Policy.PERFORMANCE);
```

Further details and instructions on how to enable this feature can be found here.

* Dynamic
  reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 5. How to Use it in your Projects?

You can import the API and start using TornadoVM. Set this in the `pom.xml` file.

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
    <version>0.14</version>
</dependency>
<dependency>
    <groupId>tornado</groupId>
    <artifactId>tornado-matrices</artifactId>
    <version>0.14</version>
</dependency>
</dependencies>
```

To run TornadoVM, you need to either install the TornadoVM extension for GraalVM/OpenJDK, or run with our
Docker [images](assembly/src/docs/12_INSTALL_WITH_DOCKER.md).

## 6. Additional Resources

[Here](assembly/src/docs/16_RESOURCES.md) you can find videos, presentations, and articles and artefacts describing
TornadoVM and how to use it.

## 7. Academic Publications


If you are using **TornadoVM >= 0.2** (which includes the Dynamic Reconfiguration, the initial FPGA support and CPU/GPU
reductions), please use the following citation:

```bibtex
@inproceedings{Fumero:DARHH:VEE:2019,
 author = {Fumero, Juan and Papadimitriou, Michail and Zakkak, Foivos S. and Xekalaki, Maria and Clarkson, James and Kotselidis, Christos},
 title = {{Dynamic Application Reconfiguration on Heterogeneous Hardware.}},
 booktitle = {Proceedings of the 15th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments},
 series = {VEE '19},
 year = {2019},
 doi = {10.1145/3313808.3313819},
 publisher = {Association for Computing Machinery}
}
```

If you are using **Tornado 0.1** (Initial release), please use the following citation in your work.

```bibtex
@inproceedings{Clarkson:2018:EHH:3237009.3237016,
 author = {Clarkson, James and Fumero, Juan and Papadimitriou, Michail and Zakkak, Foivos S. and Xekalaki, Maria and Kotselidis, Christos and Luj\'{a}n, Mikel},
 title = {{Exploiting High-performance Heterogeneous Hardware for Java Programs Using Graal}},
 booktitle = {Proceedings of the 15th International Conference on Managed Languages \& Runtimes},
 series = {ManLang '18},
 year = {2018},
 isbn = {978-1-4503-6424-9},
 location = {Linz, Austria},
 pages = {4:1--4:13},
 articleno = {4},
 numpages = {13},
 url = {http://doi.acm.org/10.1145/3237009.3237016},
 doi = {10.1145/3237009.3237016},
 acmid = {3237016},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Java, graal, heterogeneous hardware, openCL, virtual machine},
}

```

Selected publications can be found [here](assembly/src/docs/14_PUBLICATIONS.md).

## 8. Acknowledgments

This work is partially funded by [Intel corporation](https://www.intel.com/content/www/us/en/homepage.html)
the [EU Horizon 2020 ELEGANT 957286](https://www.elegant-h2020.eu/) grant. In addition, it has been supported
by [EU Horizon 2020 E2Data 780245](https://e2data.eu), the [EU Horizon 2020 ACTiCLOUD 732366](https://acticloud.eu),
and [EPSRC PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/), and AnyScale Apps EP/L000725/1 grants.

## 9. Contributions and Collaborations

We welcome collaborations! Please see how to contribute to the project in the [CONTRIBUTING](CONTRIBUTING.md) page.

### Write your questions and proposals:

Additionally, you can open new proposals on the Github discussions
page:[https://github.com/beehive-lab/TornadoVM/discussions](https://github.com/beehive-lab/TornadoVM/discussions)

### Mailing List:

A mailing list is also available to discuss TornadoVM related issues: tornado-support@googlegroups.com

### Collaborations:

For Academic & Industry collaborations, please contact [here](https://www.tornadovm.org/contact-us).

## 10. TornadoVM Team

Visit our [website](https://tornadovm.org) to meet the [team](https://www.tornadovm.org/about-us).

## 11. Licenses

To use TornadoVM, you can link the TornadoVM API to your application which is under the CLASSPATH Exception of GPLv2.0.

Each TornadoVM module is licensed as follows:

|  Module | License  |
|---|---|
| Tornado-API       |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_GPLv2CEl) + CLASSPATH Exception |
| Tornado-Runtime   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Assembly  |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)  |
| Tornado-Drivers   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Drivers-OpenCL-Headers |  [![License: MIT](https://img.shields.io/badge/License-MIT%20-orange.svg)](https://github.com/KhronosGroup/OpenCL-Headers/blob/master/LICENSE) |
| Tornado-scripts   |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)   |
| Tornado-Annotation|  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](hhttps://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2) |
| Tornado-Unittests |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| Tornado-Benchmarks|  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| Tornado-Examples  |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)   |
| Tornado-Matrices  |  [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)  |
| JNI Libraries (OpenCL, PTX and LevelZero)  |  [![License](https://img.shields.io/badge/License-MIT%20-orange.svg)](https://mit-license.org/)  |
