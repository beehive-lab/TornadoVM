TornadoVM
===================================

.. image:: images/tornadovm-logo.png
  :width: 350
  :alt: TornadoVM logo
  :align: left

TornadoVM is a GPU programming framework for Java (JDK 21+, including JDK 21 and JDK 25) that JIT-compiles Java bytecode at runtime to **NVIDIA PTX, NVIDIA CUDA C, OpenCL C, SPIR-V, and Apple Metal (MSL)**.
It runs the same Java source on NVIDIA, AMD, Intel and Apple Silicon GPUs, integrated GPUs (Intel HD Graphics, Apple M1-M5, ARM Mali), FPGAs (Intel and Xilinx), and multi-core CPUs.
TornadoVM is a plug-in to OpenJDK and other JDK distributions (GraalVM, Red Hat Mandrel, Amazon Corretto, Microsoft OpenJDK, SAP, Azul Zulu); it does not replace your JVM, it complements it.

TornadoVM provides five backends, and developers choose which one/s to install and run:
  - **OpenCL C**: dispatched through an OpenCL runtime.
  - **NVIDIA PTX**: dispatched through the NVIDIA driver API; emits PTX assembly directly.
  - **NVIDIA CUDA C**: a separate backend, also dispatched through the NVIDIA driver API, that emits CUDA C and compiles it to PTX via NVRTC at runtime.
  - **SPIR-V**: dispatched through the Level-Zero API.
  - **Apple Metal (MSL)**: dispatched on Apple Silicon GPUs.

On NVIDIA hardware, TornadoVM also calls directly into **cuBLAS, cuFFT and cuDNN**, and exposes Tensor Core ``mma.sync`` intrinsics from Java — see the :ref:`hybrid API guide <hybrid-api>`.


What is unique about TornadoVM?
--------------------------------

Beyond JIT-compiling Java bytecode to each backend, TornadoVM's runtime provides:

- **Live profiling:** utilities to profile and debug code on multiple platforms, including FPGAs (see :ref:`dev-tools`).
- **Batch processing:** automatic split and batch processing for big-data applications.
- **Multi-device & multi-backend support:** offload and run multiple kernels concurrently across different accelerators and backends.
- **Multi-vendor:** NVIDIA, Intel, AMD, Apple, ARM, and RISC-V hardware accelerators.


.. toctree::
   :caption: Overview
   :hidden:

   introduction

.. toctree::
   :caption: Installation
   :hidden:

   installation
   docker
   cloud

.. toctree::
   :caption: TornadoVM Command & Examples
   :hidden:

   simple-start
   multi-device
   benchmarking
   flags

.. toctree::
   :caption: Core Programming & Unsupported Features
   :hidden:

   programming
   offheap-types
   truffle-languages
   fpga-programming
   unsupported

.. toctree::
   :caption: Integrate Native CUDA Libraries
   :hidden:

   hybrid-api

.. toctree::
   :caption: Dev Tools
   :hidden:

   dev-tools

.. toctree::
   :caption: Developer Guidelines (Contributing)
   :hidden:

   developer-guidelines
   ide-integration
   build-documentation

.. toctree::
   :caption: Resources
   :hidden:

   faq
   resources
   publications
   CHANGELOG
