Introduction to TornadoVM
=====

Definition

.. _installation:

Installation
------------


Pre-requisites
--------------

  * Maven Version >= 3.6.3
  * CMake 3.6 (or newer)
  * At least one of:
    * OpenCL: GPUs and CPUs >= 2.1, FPGAs >= 1.0
    * CUDA 9.0 +
    * Level Zero >= 1.2
  * GCC or clang/LLVM (GCC >= 9.0)
  * Python (>= 3.0)

  For Mac OS X users: the OpenCL support for your Apple model can be confirmed [here](https://support.apple.com/en-gb/HT202823).



Supported Platforms
--------------------

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


.. code-block:: console

   sample 



.. _fundamentals:

Fundamentals
--------------