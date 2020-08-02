# TornadoVM Frequently Asked Questions

## 1. What can TornadoVM do?

TornadoVM accelerates parts of your Java applications on heterogeneous hardware devices such as multicore CPUs, GPUs, and FPGAs.

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision, physics simulations, financial applications, computational photography, natural language processing and signal processing.


## 2. How can I use it?

#### Installation

TornadoVM can be currently executed with the following two configurations:

  * TornadoVM with JDK 8 with JVMCI support: see the installation guide [here](11_INSTALL_WITH_JDK8.md)
  * TornadoVM with GraalVM (either with JDK 8 or JDK 11): see the installation guide [here](10_INSTALL_WITH_GRAALVM.md)

Note: To run TornadoVM on ARM Mali, install TornadoVM with GraalVM and JDK 11. More information [here](assembly/src/docs/17_MALI.md).

#### Usage

* [Examples of how to use TornadoVM](1_INSTALL.md#2.-Running-Examples)
* [Code examples](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples)

## 3. Which programming languages does TornadoVM support?

TornadoVM primarily supports Java. However, with the integration with GraalVM you can call your TornadoVM-compatible Java code through other programming languages supported by GraalVM's polyglot runtime (e.g., Python, R, Ruby, Javascript, Node.js, etc).

[Here](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot) you can find examples of how to use TornadoVM with GraalVM Polyglot.


## 4. Is TornadoVM a DSL?

No, TornadoVM is not a DSL. It compiles a subset of Java code to OpenCL and PTX.

The TornadoVM API only provides two Java annotations (`@Parallel` and `@Reduce`) plus a light API to create task-schedules (groups of Java methods to be accelerated by TornadoVM).

## 5. Does it support the whole Java Language?

No, TornadoVM supports a subset of the Java programming language.
A list of unsupported features along with the reasoning behind it can be found [here](Unsupported.md).

## 6. Can TornadoVM degrade the performance of my application?

No, TornadoVM can only increase the performance of your application because it can dynamically change the execution of a program at runtime onto another device.
If a particular code segment cannot be accelerated, then execution falls back to the host JVM which will execute your code on the CPU as it would normally do.

Also with the **Dynamic Reconfiguration**, TornadoVM discovers the fastest possible device for a particular code segment completely transparently to the user.

## 7. Dynamic Reconfiguration? What is this?

It is a novel feature of TornadoVM, in which the user selects a metric on which the system decides how to map a specific computation on a particular device.
Further details and instructions on how to enable this feature can be found here:

* Dynamic reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 8. Is TornadoVM supported by a company?

No, TornadoVM has been developed in the Beehive-Lab of the Advanced Processor Technology Group ([APT](http://apt.cs.manchester.ac.uk/)) at The University of Manchester.


## 9. Does TornadoVM support only OpenCL devices?

No. Currently, TornadoVM supports two compiler backends and therefore, is able to generate OpenCL and PTX code depending on the user hardware and preference. We are planning on implementing additional compiler backends to further increase support.

## 10. Why is it called a VM?

The VM name is used because TornadoVM implements its own set of bytecodes for handling heterogeneous execution. These bytecodes are used for handling JIT compilation, device exploration, data management and live task-migration for heterogeneous devices (multi-core CPUs, GPUs, and FPGAs). We sometimes refer to a VM inside a VM (nested VM). The main VM is the Java Virtual Machine, and TornadoVM sits on top of that.

You can find more information here: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)


## 11. How it interacts with OpenJDK?

TornadoVM makes use of the Java Virtual Machine Common Interface (JVMCI) that is included from Java 9 to compile Java bytecode to OpenCL C / PTX at runtime. As a JVMCI implementation, TornadoVM uses Graal (it extends the Graal IR and includes new backends for OpenCL C and PTX code generation).

## 12. How do I know which parts of my application are suitable for acceleration?
Workloads with for-loops that do not have dependencies between iterations are very good candidates to offload on accelerators. Examples of this pattern are NBody computation, Black-scholes, DFT, KMeans, etc.

Besides, matrix-type applications are good candidates, such as matrix-multiplication widely used in machine and deep learning.


## 13. How can I contribute to TornadoVM?

TornadoVM is an open-source project, and, as such, we welcome contributions.

* Look at Github issues tagged with [good first issue](https://github.com/beehive-lab/TornadoVM/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).
* **Documentation**: you can help to improve install documentation, testing platforms, and scripts to easy deploy TornadoVM.
* **TornadoVM use-cases**: Develop use cases that use TornadoVM for acceleration: block-chain, graphical version of NBody, filters for photography, etc.
* **TornadoVM Development / Improvements**: If you would like to contribute to the TornadoVM internals, here is a list of pending tasks/improvements:

    - Port all Python-2 scripts to Python-3.
    - Implement a performance plot suite when running the benchmark runner. This should plot speedups against serial Java as well as stacked bars with breakdown analysis (e.g. time spent on compilation, execution, and data transfers).
    - Port TornadoVM to Windows 10 - port bash scripts and adapt Python scripts to build with Windows 10.

[Here](https://github.com/beehive-lab/TornadoVM/blob/master/CONTRIBUTIONS.md) you can find more information about how to contribute, code conventions, and tasks.


## 14. Does TornadoVM supports calls to standard Java libraries?

Partially yes. TornadoVM currently supports calls to the Math library. However, invocations that imply I/O are not supported.
