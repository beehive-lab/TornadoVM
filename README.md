# TornadoVM

![TornadoVM version](https://img.shields.io/badge/version-1.1.1-purple)  [![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-1.4-4baaaa.svg)](CODE_OF_CONDUCT.md)  [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2) [![License: GPL v2](https://img.shields.io/badge/License-GPL%20V2%20Classpth%20Exeception-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)

<img align="left" width="250" height="250" src="etc/tornadoVM_Logo.jpg">

TornadoVM is a plug-in to OpenJDK and GraalVM that allows programmers to automatically run Java programs on
heterogeneous hardware.
TornadoVM targets OpenCL, PTX and SPIR-V compatible devices which include multi-core CPUs, dedicated
GPUs (Intel, NVIDIA, AMD), integrated GPUs (Intel HD Graphics and ARM Mali), and FPGAs (Intel and Xilinx).

TornadoVM has three backends that generate OpenCL C, NVIDIA CUDA PTX assembly, and SPIR-V binary.
Developers can choose which backends to install and run.

----------------------

**Website**: [tornadovm.org](https://www.tornadovm.org)

**Documentation**: [https://tornadovm.readthedocs.io/en/latest/](https://tornadovm.readthedocs.io/en/latest/)

For a quick introduction please read the following [FAQ](https://tornadovm.readthedocs.io/en/latest/).

**Latest Release:** TornadoVM 1.1.1 - 07/07/2025 :
See [CHANGELOG](https://tornadovm.readthedocs.io/en/latest/CHANGELOG.html).

----------------------

## 1. Installation

In Linux and macOS, TornadoVM can be installed automatically with
the [installation script](https://tornadovm.readthedocs.io/en/latest/installation.html). For example:

```bash
$ ./bin/tornadovm-installer --help
usage: tornadovm-installer [-h] [--jdk JDK] [--backend BACKEND] [--version] [--listJDKs] [--polyglot] [--mvn_single_threaded] [--auto-deps]

TornadoVM Installer Tool. It will install all software dependencies except the GPU/FPGA drivers

options:
  -h, --help            show this help message and exit
  --jdk JDK             Specify a JDK to install by its keyword (e.g., 'jdk21', 'graal-jdk-21'). Run with --listJDKs to view all available JDK keywords.
  --backend BACKEND     Select the backend to install: { opencl, ptx, spirv }
  --version             Print version
  --listJDKs            List supported JDKs
  --polyglot            Enable Truffle Interoperability with GraalVM
  --mvn_single_threaded
                        Run Maven in single-threaded mode
  --auto-deps           Automatic download and use any missing dependencies
```

**NOTE** Select the desired backend:

* `opencl`: Enables the OpenCL backend (requires OpenCL drivers)
* `ptx`: Enables the PTX backend (requires NVIDIA CUDA drivers)
* `spirv`: Enables the SPIRV backend (requires Intel Level Zero drivers)

Example of installation:

```bash
# Install the OpenCL backend with OpenJDK 21
$ ./bin/tornadovm-installer --jdk jdk21 --backend opencl

# It is also possible to combine different backends:
$ ./bin/tornadovm-installer --jdk jdk21 --backend opencl,spirv,ptx
```

Alternatively, TornadoVM can be installed either
manually [from source](https://tornadovm.readthedocs.io/en/latest/installation.html#b-manual-installation) or
by [using Docker](https://tornadovm.readthedocs.io/en/latest/docker.html).

If you are planning to use Docker with TornadoVM on GPUs, you can also
follow [these](https://github.com/beehive-lab/docker-tornado#docker-for-tornadovm) guidelines.

You can also run TornadoVM on Amazon AWS CPUs, GPUs, and FPGAs following the
instructions [here](https://tornadovm.readthedocs.io/en/latest/cloud.html).

## 2. Usage Instructions

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision,
physics simulations, financial applications, computational photography, and signal processing.

Featured use-cases:

- [kfusion-tornadovm](https://github.com/beehive-lab/kfusion-tornadovm): Java application for accelerating a
  computer-vision application using the Tornado-APIs to run on discrete and integrated GPUs.
- [Java Ray-Tracer](https://github.com/Vinhixus/TornadoVM-Ray-Tracer): Java application accelerated with TornadoVM for
  real-time ray-tracing.

We also have a set
of [examples](https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples)
that includes NBody, DFT, KMeans computation and matrix computations.

**Additional Information**

- [General Documentation](https://tornadovm.readthedocs.io/en/latest/introduction.html)
- [Benchmarks](https://tornadovm.readthedocs.io/en/latest/benchmarking.html)
- [How TornadoVM executes reductions](https://tornadovm.readthedocs.io/en/latest/programming.html#parallel-reductions)
- [Execution Flags](https://tornadovm.readthedocs.io/en/latest/flags.html)
- [FPGA execution](https://tornadovm.readthedocs.io/en/latest/fpga-programming.html)
- [Profiler Usage](https://tornadovm.readthedocs.io/en/latest/profiler.html)

## 3. Programming Model

TornadoVM exposes to the programmer task-level, data-level and pipeline-level parallelism via a light Application
Programming Interface (API). In addition, TornadoVM uses single-source property, in which the code to be accelerated and
the host code live in the same Java program.

Compute-kernels in TornadoVM can be programmed using two different approaches (APIs):

#### a) Loop Parallel API

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

        // Create a task-graph with multiple tasks. Each task points to an exising Java method
        // that can be accelerated on a GPU/FPGA
        TaskGraph taskGraph = new TaskGraph("myCompute")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B) // Transfer data from host to device only in the first execution
                .task("mxm", Compute::mxmLoop, A, B, C, size)             // Each task points to an existing Java method
                .transferToHost(DataTransferMode.EVERY_EXECUTION, C);     // Transfer data from device to host

        // Create an immutable task-graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snaphot();

        // Create an execution plan from an immutable task-graph
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Run the execution plan on the default device
            TorandoExecutionResult executionResult = executionPlan.execute();

        } catch (TornadoExecutionPlanException e) {
            // handle exception
            // ...
        }
    }
}
```

#### b) Kernel API

Another way to express compute-kernels in TornadoVM is via the **Kernel API**.
To do so, TornadoVM exposes the `KernelContext` data structure, in which the application can directly access the thread-id, allocate
memory in local memory (shared memory on NVIDIA devices), and insert barriers.
This model is similar to programming compute-kernels in SYCL, oneAPI, OpenCL and CUDA.
Therefore, this API is more suitable for GPU/FPGA expert programmers that want more control or want to port existing
CUDA/OpenCL compute kernels into TornadoVM.

The following code-snippet shows the Matrix Multiplication example using the kernel-parallel API:

```java
public class Compute {
    private static void mxmKernel(KernelContext context, Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        int idx = context.globalIdx
        int jdx = context.globalIdy;
        float sum = 0;
        for (int k = 0; k < size; k++) {
            sum += A.get(idx, k) * B.get(k, jdx);
        }
        C.set(idx, jdx, sum);
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        // When using the kernel-parallel API, we need to create a Grid and a Worker
        WorkerGrid workerGrid = new WorkerGrid2D(size, size);    // Create a 2D Worker
        GridScheduler gridScheduler = new GridScheduler("myCompute.mxm", workerGrid);  // Attach the worker to the Grid
        KernelContext context = new KernelContext();             // Create a context
        workerGrid.setLocalWork(16, 16, 1);                      // Set the local-group size

        TaskGraph taskGraph = new TaskGraph("myCompute")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B) // Transfer data from host to device only in the first execution
                .task("mxm", Compute::mxmKernel, context, A, B, C, size)   // Each task points to an existing Java method
                .transferToHost(DataTransferMode.EVERY_EXECUTION, C);     // Transfer data from device to host

        // Create an immutable task-graph
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        // Create an execution plan from an immutable task-graph
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // Run the execution plan on the default device
            // Execute the execution plan
            TorandoExecutionResult executionResult = executionPlan
                        .withGridScheduler(gridScheduler)
                        .execute();
        } catch (TornadoExecutionPlanException e) {
            // handle exception
            // ...
        }
    }
}
```

Additionally, the two modes of expressing parallelism (kernel and loop parallelization) can be combined in the same task
graph object.

## 4. Dynamic Reconfiguration

Dynamic reconfiguration is the ability of TornadoVM to perform live task migration between devices, which means that
TornadoVM decides where to execute the code to increase performance (if possible). In other words, TornadoVM switches
devices if it can detect that a specific device can yield better performance (compared to another).

With the task-migration, the TornadoVM's approach is to only switch device if it detects an application can be executed
faster
than the CPU execution using the code compiled by C2 or Graal-JIT, otherwise it will stay on the CPU. So TornadoVM can
be seen as a complement to C2 and Graal JIT compilers. This is because there is no single hardware to best execute all
workloads
efficiently. GPUs are very good at exploiting SIMD applications, and FPGAs are very good at exploiting pipeline
applications. If your applications follow those models, TornadoVM will likely select heterogeneous hardware. Otherwise,
it will stay on the CPU using the default compilers (C2 or Graal).

To use the dynamic reconfiguration, you can execute using TornadoVM policies.
For example:

```java
// TornadoVM will execute the code in the best accelerator.
executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.PARALLEL)
             .execute();
```

Further details and instructions on how to enable this feature can be found here.

* Dynamic
  reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 5. How to Use TornadoVM in your Projects?

To use TornadoVM, you need two components:

a) The TornadoVM `jar` file with the API. The API is licensed as GPLV2 with Classpath Exception.
b) The core libraries of TornadoVM along with the dynamic library for the driver code (`.so` files for OpenCL, PTX
and/or SPIRV/Level Zero).

You can import the TornadoVM API by setting this the following dependency in the Maven `pom.xml` file:

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
    <version>1.1.1</version>
</dependency>
<dependency>
    <groupId>tornado</groupId>
    <artifactId>tornado-matrices</artifactId>
    <version>1.1.1</version>
</dependency>
</dependencies>
```

To run TornadoVM, you need to either install the TornadoVM extension for GraalVM/OpenJDK, or run with our
Docker [images](https://github.com/beehive-lab/docker-tornado).

## 6. Additional Resources

[Here](https://tornadovm.readthedocs.io/en/latest/resources.html) you can find videos, presentations, tech-articles and
artefacts describing TornadoVM, and how to use it.

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

Selected publications can be found [here](https://tornadovm.readthedocs.io/en/latest/publications.html).

## 8. Acknowledgments

This work is partially funded by [Intel corporation](https://www.intel.com/content/www/us/en/homepage.html).
In addition, it has been supported by the following EU & UKRI grants (most recent first):

- EU Horizon Europe & UKRI [AERO 101092850](https://aero-project.eu/).
- EU Horizon Europe & UKRI [P2CODE 101093069](https://p2code-project.eu/).
- EU Horizon Europe & UKRI [ENCRYPT 101070670](https://encrypt-project.eu).
- EU Horizon Europe & UKRI [TANGO 101070052](https://tango-project.eu).
- EU Horizon 2020 [ELEGANT 957286](https://www.elegant-h2020.eu/).
- EU Horizon 2020 [E2Data 780245](https://e2data.eu).
- EU Horizon 2020 [ACTiCLOUD 732366](https://acticloud.eu).

Furthermore, TornadoVM has been supported by the following [EPSRC](https://www.ukri.org/councils/epsrc/) grants:

- [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/).
- [AnyScale Apps EP/L000725/1](https://gow.epsrc.ukri.org/NGBOViewGrant.aspx?GrantRef=EP/L000725/1).

## 9. Contributions and Collaborations

We welcome collaborations! Please see how to contribute to the project in the [CONTRIBUTING](CONTRIBUTING.md) page.

### Write your questions and proposals:

Additionally, you can open new proposals on the GitHub
discussions [page](https://github.com/beehive-lab/TornadoVM/discussions).

Alternatively, you can share a Google document with us.

### Collaborations:

For Academic & Industry collaborations, please contact [here](https://www.tornadovm.org/contact-us).

## 10. TornadoVM Team

Visit our [website](https://tornadovm.org) to meet the [team](https://www.tornadovm.org/about-us).

## 11. Licenses Per Module

To use TornadoVM, you can link the TornadoVM API to your application which is under [Apache 2](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2).

Each Java TornadoVM module is licensed as follows:

| Module                         | License                                                                                                                                                          |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tornado-API                    | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Runtime                | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2%20Classpath%20Exeception-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) |
| Tornado-Assembly               | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Drivers                | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20V2%20Classpath%20Exeception-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) |
| Tornado-Drivers-OpenCL-Headers | [![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/KhronosGroup/OpenCL-Headers/blob/master/LICENSE)                      |
| Tornado-scripts                | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Annotation             | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Unittests              | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Benchmarks             | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Examples               | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
| Tornado-Matrices               | [![License: Apache 2](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://github.com/beehive-lab/TornadoVM/blob/master/LICENSE_APACHE2)          |
|                                |                                                                                                                                                                  |
