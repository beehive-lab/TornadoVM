# TornadoVM Frequently Asked Questions

## 1. What can TornadoVM do?

TornadoVM accelerates parts of your Java applications on heterogeneous hardware devices such as multicore CPUs, GPUs,
and FPGAs.

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision,
physics simulations, financial applications, computational photography, natural language processing and signal
processing.

## 2. Can I use TornadoVM in a commercial application?

Absolutely yes! TornadoVM employs many licenses as shown [here](https://github.com/beehive-lab/TornadoVM#11-licenses), but its API is under CLASSPATH EXCEPTION, and hence it can be freely used in any application.

## 3. How can I use it?

In Linux and Mac OSx, TornadoVM can be installed by the [installer](https://github.com/beehive-lab/tornadovm-installer).
Alternatively, TornadoVM can be installed either [from source](1_INSTALL.md) or by [using Docker](13_INSTALL_WITH_DOCKER.md).

#### List of compatible JDKs

TornadoVM can be currently executed with the following three configurations:

* TornadoVM with GraalVM (JDK 11, JDK 17): see the installation guide [here](10_INSTALL_WITH_GRAALVM.md).
* TornadoVM with JDK11+ (e.g. OpenJDK 11, OpenJDK 17, Red Hat Mandrel 11, Amazon Corretto 11, Amazon Corretto 17, Windows JDK 11, Windows JDK 17): see the installation guide [here](12_INSTALL_WITH_JDK11_PLUS.md).

Note: To run TornadoVM on ARM Mali GPUs, install TornadoVM with GraalVM and JDK 11. More information [here](18_MALI.md).

#### Usage

* [Examples of how to use TornadoVM](1_INSTALL.md#2-running-examples)
* [Code examples](https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples)

## 4. Which programming languages does TornadoVM support?

TornadoVM primarily supports Java. However, with the integration with GraalVM you can call your TornadoVM-compatible
Java code through other programming languages supported by GraalVM's polyglot runtime (e.g., Python, R, Ruby,
Javascript, Node.js, etc).

[Here](https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot)
you can find examples of how to use TornadoVM with GraalVM Polyglot.

## 5. Is TornadoVM a DSL?

No, TornadoVM is not a DSL. It compiles a subset of Java code to OpenCL and PTX.

The TornadoVM API only provides two Java annotations (`@Parallel` and `@Reduce`) plus a light API to create
task-schedules (groups of Java methods to be accelerated by TornadoVM).

## 6. Does it support the whole Java Language?

No, TornadoVM supports a subset of the Java programming language. A list of unsupported features along with the
reasoning behind it can be found [here](Unsupported.md).

## 7. Can TornadoVM degrade the performance of my application?

No, TornadoVM can only increase the performance of your application because it can dynamically change the execution of a
program at runtime onto another device. If a particular code segment cannot be accelerated, then execution falls back to
the host JVM which will execute your code on the CPU as it would normally do.

Also with the **Dynamic Reconfiguration**, TornadoVM discovers the fastest possible device for a particular code segment
completely transparently to the user.

## 8. Dynamic Reconfiguration? What is this?

It is a novel feature of TornadoVM, in which the user selects a metric on which the system decides how to map a specific
computation on a particular device. Further details and instructions on how to enable this feature can be found here:

* Dynamic
  reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 9. Does TornadoVM support only OpenCL devices?

No. Currently, TornadoVM supports three compiler backends and therefore, it is able to generate OpenCL, PTX, and SPIR-V code
depending on the hardware configuration.

## 10. Why is it called a VM?

The VM name is used because TornadoVM implements its own set of bytecodes for handling heterogeneous execution. These
bytecodes are used for handling JIT compilation, device exploration, data management and live task-migration for
heterogeneous devices (multi-core CPUs, GPUs, and FPGAs). We sometimes refer to a VM inside a VM (nested VM). The main
VM is the Java Virtual Machine, and TornadoVM sits on top of that.

You can find more information
here: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 11. How does it interact with OpenJDK?

TornadoVM makes use of the Java Virtual Machine Common Interface (JVMCI) that is included from Java 9 to compile Java
bytecode to OpenCL C / PTX at runtime. As a JVMCI implementation, TornadoVM uses Graal (it extends the Graal IR and
includes new backends for OpenCL C and PTX code generation).

## 12. How do I know which parts of my application are suitable for acceleration?

Workloads with for-loops that do not have dependencies between iterations are very good candidates to offload on
accelerators. Examples of this pattern are NBody computation, Black-scholes, DFT, KMeans, etc.

Besides, matrix-type applications are good candidates, such as matrix-multiplication widely used in machine and deep
learning.

## 13. How can I contribute to TornadoVM?

TornadoVM is an open-source project, and, as such, we welcome contributions from all levels.

* **Solve [issues](https://github.com/beehive-lab/TornadoVM/issues)** reported on the GitHub page. 
* **New proposals**: We welcome new proposals and ideas. To work on a new proposal, use
  the [discussion](https://github.com/beehive-lab/TornadoVM/discussions) page on GitHub. Alternatively, you can open a
  shared document (e.g., a shared Google doc) where we can discuss and analyse your proposal.

[Here](https://github.com/beehive-lab/TornadoVM/blob/master/CONTRIBUTING.md) you can find more information about how to
contribute, code conventions, and tasks.

## 14. Does TornadoVM support calls to standard Java libraries?

Partially yes. TornadoVM currently supports calls to the Math library. However, invocations that imply I/O are not
supported.
