# TornadoVM Frequently Asked Questions

## 1. What can TornadoVM do?

TornadoVM can accelerate parts of your application on heterogeneous co-processors such as multicore CPUs, GPUs, and FPGAs.

## 2. How can I use it?

For installation and usage instructions please refer to the following pages: Installation, Usage Instructions.

## 3. Which programming languages does TornadoVM support?

TornadoVM primarily supports Java. However, with the integration with GraalVM you can call your TornadoVM-compatible Java code through other programming languages supported by GraalVM's polyglot runtime.

A usage example can be found here.

## 4. Is TornadoVM a DSL?

No, TornadoVM is not a DSL. It executes nominal Java code.

## 5. Does it support the whole Java API?

No, TornadoVM supports a subset of the Java programming language.
A list of unsupported features along with the reasoning behind it can be found here.

## 6. How does TornadoVM compares to APARAPI and IBM J9?

Although TornadoVM shares some similarities with APARAPI and IBM J9, it has a number of key advantages which are listed below:

## 7. Can TornadoVM degrade the performance of my application?

No, TornadoVM can only increase the performance of your application.
If a particular code segment can not be accelerated, then execution will fall back to the host JVM which will execute your code on the CPU as it would normally do.

Also with Dynamic Reconfiguration, TornadoVM can discover the fastest possible device for a particular code segment completely transparently to the user.

## 8. Dynamic Reconfiguration? What is this?

It is a novel feature of TornadoVM, in which the user selected a metric on which the system decides how to map a specific computation on particular device.
Further details and instructions on how to enable this feature can be found here.

## 9. Is TornadoVM supported by a company?

No, TornadoVM has been developed in the Beehive Lab of the APT Group at The University of Manchester.

## 10. Does TornadoVM support only OpenCL devices?

Currently, yes. However, due to its decoupled archtiecture we are adding support for other back-ends also, so the users can decide which one to use.
