TornadoVM
===================================

.. image:: images/logo.jpg
  :width: 100
  :alt: Sample Text
  :align: left

TornadoVM is a plug-in to OpenJDK and other JDK distributions (e.g., GraalVM, Red Hat Mandrel, Amazon Corretto, Microsoft OpenJDK, Azul Zulu) 
that allows developers to automatically run Java programs on heterogeneous hardware. 
TornadoVM targets devices compatible with OpenCL, PTX and Level-Zero, which include multi-core CPUs,
dedicated GPUs (Intel, NVIDIA, AMD), integrated GPUs (Intel HD Graphics and ARM Mali), and FPGAs (Intel and Xilinx).

TornadoVM has three backends: OpenCL, NVIDIA CUDA PTX, and SPIR-V.
Developers can choose which backends to install and run.

TornadoVM does not replace existing VMs, but it rather complements them with the capability of offloading Java code to OpenCL, PTX and SPIR-V,
handling memory management between Java and hardware accelerators, and running/coordinating the compute-kernels.


Contents
--------

.. toctree::

   introduction
   installation
   simple-start
   programming
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
   faq
   unsupported
   resources
   publications
   CHANGELOG
