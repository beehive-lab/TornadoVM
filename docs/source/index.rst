TornadoVM
===================================

.. image:: images/logo.jpg
  :width: 150
  :alt: Sample Text
  :align: left

TornadoVM is a plug-in to OpenJDK and other JDK distributions (e.g., GraalVM, Red Hat Mandrel, Amazon Corretto, Microsoft OpenJDK, SAP, Azul Zulu)
that allows developers to automatically run Java programs on heterogeneous hardware.
TornadoVM targets devices compatible with OpenCL, NVIDIA PTX and Level-Zero, which include multi-core CPUs,
dedicated GPUs (Intel, NVIDIA, AMD), integrated GPUs (Intel HD Graphics, Apple M1/M2/M3, and ARM Mali), and FPGAs (Intel and Xilinx).

TornadoVM provides three backends:
  - OpenCL C: dispatched through an OpenCL runtime. 
  - NVIDIA CUDA PTX: dispatched through the NVIDIA driver API. 
  - SPIR-V: dispatched through the Level-Zero API. 

Developers can choose which backend/s to install and run.


What is unique about TornadoVM? 
--------------------------------

TornadoVM does not only provide a set of JIT compilers to transpile Java bytecode to the target backend-code. 
It also provides a set of runtime features that make it unique in the Java scope:

- **Dynamic reconfiguration and live-task migration** across devices at runtime.
- **Live profiling:** TornadoVM provides utilities to profile and debug code on multiple platforms, including FPGAs.
- **Batch processing:** TornadoVM provides utilities to perform automatic split and batch processing for acceleration of big-data applications.
- **Multi-device & Multi-backend support:** TornadoVM can offload and run multiple kernels concurrently using different hardware accelerators using different backends. 
- **TornadoVM is multi-vendor**, running applications on NVIDIA, Intel, AMD, ARM and even RISC-V hardware accelerators. 


**Important:** TornadoVM does not replace existing VMs, but it rather complements them with the capability of offloading Java code to OpenCL, PTX and SPIR-V,
handling memory management between Java and hardware accelerators, and running/coordinating the compute-kernels.


Content:
--------

.. toctree::

   introduction
   installation
   simple-start
   programming
   offheap-types
   truffle-languages
   profiler
   benchmarking
   fpga-programming
   docker
   cloud
   spirv-backend
   cuda-backend
   multi-device
   flags
   ide-integration
   developer-guidelines
   build-documentation
   faq
   unsupported
   resources
   publications
   CHANGELOG
