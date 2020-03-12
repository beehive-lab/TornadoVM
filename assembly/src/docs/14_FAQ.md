# TornadoVM Frequently Asked Questions

## 1. What can TornadoVM do?

TornadoVM accelerates parts of your Java applications on heterogeneous hardware devices such as multicore CPUs, GPUs, and FPGAs.

TornadoVM is currently being used to currently accelerate machine learning and deep learning applications, computer vision, physics simulations, financial applications, computational photography, natural language processing and signal processing. 


## 2. How can I use it?

###### Installation

TornadoVM can be currently executed with the following two configurations:

  * TornadoVM with JDK 8 with JVMCI-8 support: see the installation guide [here](11_INSTALL_WITH_JDK8.md)
  * TornadoVM with GraalVM (either with JDK 8 or JDK 11): see the installation guide [here](10_INSTALL_WITH_GRAALVM.md)


###### Usage

* [Examples of how to use TornadoVM](1_INSTALL.md#2.-Running-Examples)
* [Code examples](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples)

## 3. Which programming languages does TornadoVM support?

TornadoVM primarily supports Java. However, with the integration with GraalVM you can call your TornadoVM-compatible Java code through other programming languages supported by GraalVM's polyglot runtime (e.g., Python, R, Ruby, Javascript, Node.js, etc). 

[Here](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot) you can find examples of how to use TornadoVM with GraalVM Polyglot.


## 4. Is TornadoVM a DSL?

No, TornadoVM is not a DSL. It executes nominal Java code. 

TornadoVM only provides two Java annotations (`@Parallel` and `@Reduce`) plus a light API to create task-schedules (group of Java methods to be accelerated by TornadoVM).

## 5. Does it support the whole Java Language?

No, TornadoVM supports a subset of the Java programming language.
A list of unsupported features along with the reasoning behind it can be found [here](Unsupported.md).

## 6. How does TornadoVM compares to APARAPI and IBM J9?

Although TornadoVM shares some similarities with APARAPI and IBM J9, it has a number of key advantages which are listed below:

###### Aparapi
Aparapi is a direct translation from Java bytecodes to OpenCL. To do so, Aparapi provides a compiler and a runtime system to automatically handle data and execute the generated OpenCL Kernel.

###### IBM J9
IBM J9 accelerates the `forEach` method within the Java Stream API to run on NVIDIA GPUs by offloading Java code to NVIDIA PTX. IBM J9 also provides pre-compiled CUDA kernels for some common operations, such as sorting. 


###### TornadoVM

TornadoVM also compiles from Java bytecodes to OpenCL. But additionally, it optimizes and specializes the code by interleaving Graal compiler optimizations, such as partial escape analysis, canonicalization, loop unrolling, constant propagation, etc) with GPU/CPU/FPGA specific optimizations (e.g., parallel loop exploration, automatic use of local memory, parallel skeletons exploration such as reductions). TornadoVM generates different OpenCL code depending on the target device, which means that the code generated for GPUs is different for FPGAs and multi-cores. This is because although OpenCL is portable across devices, its performance is not. TornadoVM addresses this challenge by applying compiler specializations depending on the device.

Additionally, TornadoVM performs live task migration between devices, which means that TornadoVM decides where to execute the code to increase performance (if possible). In other words, TornadoVM switches devices if it knows the new device offers better performance. As far as we know, this is not available in Aparapi (in which device selection is static). With the task-migration, the TornadoVM's approach is to only switch device if it detects applications can be executed faster than the CPU execution using the code compiled by C2 or Graal-JIT, otherwise it will stay on the CPU. So TornadoVM can be seen as a complement to C2 and Graal. This is because there is no single hardware to best execute all workloads efficiently. GPUs are very good at exploiting SIMD applications, and FPGAs are very good at exploiting pipeline applications. If your applications follow those models, TornadoVM will likely select heterogeneous hardware. Otherwise it will stay on the CPU using the default compilers (C2 or Graal).

Some references:
* Compiler specializations: [https://dl.acm.org/doi/10.1145/3237009.3237016](https://dl.acm.org/doi/10.1145/3237009.3237016)
* Parallel skeletons: [https://dl.acm.org/doi/10.1145/3281287.3281292](https://dl.acm.org/doi/10.1145/3281287.3281292)
* Live task-migration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)


## 7. Can TornadoVM degrade the performance of my application?

No, TornadoVM can only increase the performance of your application because it can dynamically change the execution of a program at runtime to another device. 
If a particular code segment cannot be accelerated, then execution falls back to the host JVM which will execute your code on the CPU as it would normally do.

Also with the **Dynamic Reconfiguration**, TornadoVM discovers the fastest possible device for a particular code segment completely transparently to the user.

## 8. Dynamic Reconfiguration? What is this?

It is a novel feature of TornadoVM, in which the user selects a metric on which the system decides how to map a specific computation on particular device.
Further details and instructions on how to enable this feature can be found here:

* Dynamic reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

## 9. Is TornadoVM supported by a company?

No, TornadoVM has been developed in the Beehive-Lab of the Advanced Processor Technology Group ([APT](http://apt.cs.manchester.ac.uk/)) at The University of Manchester. 


## 10. Does TornadoVM support only OpenCL devices?

Currently, yes. However, due to its decoupled software architecture, we are adding support for other back-ends as well. Therefore users can decide which one to use.

## 11. Finally, why is it called a VM?

The VM name is used because TornadoVM implements its own set of bytecodes for handling heterogeneous execution. These bytecodes are used for handling JIT compilation, device exploration, data management and live task-migration for heterogeneous devices (multi-core CPUs, GPUs, and FPGAs). We sometimes refer to a VM inside a VM (nested VM). The main VM is the Java Virtual Machine, and TornadoVM sits on top of that.

You can find more information here: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)
