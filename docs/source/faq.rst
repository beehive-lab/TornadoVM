Frequently Asked Questions
====================================

1. What can TornadoVM do?
-------------------------

TornadoVM accelerates parts of your Java applications on heterogeneous hardware devices such as multicore CPUs, GPUs, and FPGAs.

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision, physics simulations,
financial applications, computational photography, natural language processing and signal processing.

2. Can I use TornadoVM in a commercial application?
---------------------------------------------------

Absolutely yes! TornadoVM employs several licenses as shown `here <https://github.com/beehive-lab/TornadoVM#licenses-per-module>`__. The **Tornado-API** module that your application links against is **Apache 2.0**, and hence it can be freely used in any application. The runtime and drivers are **GPLv2 with Classpath Exception** (the same license as OpenJDK itself), which does not impose copyleft obligations on your application.

3. How can I use it?
--------------------

You can be running a TornadoVM example on a GPU in three commands:

.. code-block:: bash

   # 1. Install with SDKMAN!
   $ sdk install tornadovm

   # 2. Verify devices
   $ tornado --devices

   # 3. Run an example
   $ java @$TORNADOVM_HOME/tornado-argfile -cp $TORNADOVM_HOME/share/java/tornado/tornado-examples-5.0.0-jdk21.jar uk.ac.manchester.tornado.examples.compute.MatrixVectorRowMajor

See :ref:`installation` for all install options (SDKMAN!, the official downloads page, Maven Central, or Docker images), or :ref:`build-from-source` if you want to build TornadoVM yourself.

List of compatible JDKs
^^^^^^^^^^^^^^^^^^^^^^^

TornadoVM currently supports:

-  JDK 21 (e.g. OpenJDK 21, Red Hat Mandrel 21, Amazon Corretto 21, Azul Zulu JDK 21).
-  JDK 25.
-  GraalVM (JDK 21), for polyglot interoperability (see :ref:`truffle-languages`).

Windows
~~~~~~~~~~

To run TornadoVM on **Windows 10/11 OS**, more information here: :ref:`installation_windows`.


ARM Mali GPUs and Linux
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To run TornadoVM on ARM Mali, install TornadoVM with GraalVM and JDK 21. More information here: :ref:`installation_mali`.

Usage
^^^^^
-  Examples of how to use TornadoVM: :ref:`examples`.
-  `Code examples <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples>`__

4. Which programming languages does TornadoVM support?
------------------------------------------------------

TornadoVM primarily supports Java. However, with the integration with GraalVM you can call your TornadoVM-compatible Java code through other
programming languages supported by GraalVM's polyglot runtime (e.g., Python, R, Ruby, Javascript, Node.js, etc).

`Here <https://github.com/beehive-lab/TornadoVM/tree/master/tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/polyglot>`__
you can find examples of how to use TornadoVM with GraalVM Polyglot.

5. Is TornadoVM a Domain Specific Language (DSL)?
--------------------------------------------------------------

No, TornadoVM is not a DSL. It compiles a subset of Java code to OpenCL C, NVIDIA PTX, NVIDIA CUDA C, SPIR-V, and Apple Metal.

To express parallelism, TornadoVM offers the :ref:`Loop Parallel API <loop-parallel-api>` (``@Parallel`` and ``@Reduce`` annotations) and the lower-level :ref:`Kernel API <kernel-context-api>` (``KernelContext``), plus an API to:

a) Create and define task-graphs with the :ref:`Task-Graph API <task-graph-api>` (groups of Java methods to be accelerated by TornadoVM, and the data involved).
b) Define :ref:`execution plans <execution-plan>`.

6. Does it support the whole Java Language?
-------------------------------------------

No, TornadoVM supports a subset of the Java programming language. A list of unsupported features along with the reasoning behind it can be found  here: :ref:`unsupported`.

7. Can TornadoVM degrade the performance of my application?
-----------------------------------------------------------

No, TornadoVM cannot degrade the performance of your application: if a particular code segment cannot be accelerated, execution falls back to the host JVM, which runs your code on the CPU exactly as it would without TornadoVM.

With **Dynamic Reconfiguration** opted into (see :ref:`dynamic_reconfiguration`), TornadoVM can also discover the fastest available device for a particular code segment.

8. Does TornadoVM support only OpenCL devices?
----------------------------------------------

No. Currently, TornadoVM supports multiple compiler backends and therefore, it is able to generate OpenCL C, NVIDIA PTX, CUDA C
(compiled to PTX via NVRTC), SPIR-V, and Metal code depending on the hardware configuration.

9. Why is it called a VM?
--------------------------

The VM name is used because TornadoVM implements its own set of bytecodes for handling heterogeneous execution.
These bytecodes are used for handling JIT compilation, device exploration, data management and live task-migration
for heterogeneous devices (multi-core CPUs, GPUs, and FPGAs). We sometimes refer to a VM inside a VM (nested VM).
The main VM is the Java Virtual Machine, and TornadoVM sits on top of that.

You can find more information here: `https://dl.acm.org/doi/10.1145/3313808.3313819 <https://dl.acm.org/doi/10.1145/3313808.3313819>`_.

10. How does it interact with OpenJDK?
--------------------------------------

TornadoVM makes use of the Java Virtual Machine Common Interface (JVMCI) that is included from Java 9 to compile Java bytecode to OpenCL C, NVIDIA PTX, NVIDIA CUDA C, SPIR-V, and Apple Metal at runtime.
As a JVMCI implementation, TornadoVM uses Graal (it extends the Graal IR and includes new backends for each of these targets).

11.  How do I know which parts of my application are suitable for acceleration?
-------------------------------------------------------------------------------

Workloads with for-loops that do not have dependencies between iterations are very good candidates to offload on accelerators.
Examples of this pattern are NBody computation, Black-scholes, DFT, KMeans, etc.

Besides, matrix-type applications are good candidates, such as matrix-multiplication widely used in machine and deep learning.

12. How can I contribute to TornadoVM?
--------------------------------------

TornadoVM is an open-source project, and, as such, we welcome contributions from all levels.

-  **Solve** `issues <https://github.com/beehive-lab/TornadoVM/issues>`__
   reported on the GitHub page.
-  **Work on New Proposals**: We welcome new proposals and ideas. To work on a new proposal, use the
   `discussion <https://github.com/beehive-lab/TornadoVM/discussions>`__
   page on GitHub. Alternatively, you can open a shared document (e.g., a shared Google doc) where we can discuss and analyse your proposal.

`Here <https://github.com/beehive-lab/TornadoVM/blob/master/CONTRIBUTING.md>`__
you can find more information about how to contribute, code conventions,
and tasks. See also :ref:`developer-guidelines` for how to build from source, configure your IDE, and set up code formatting and pre-commit hooks.

13. Does TornadoVM support calls to standard Java libraries?
------------------------------------------------------------

Partially yes. TornadoVM currently supports calls to the Math library.
However, invocations that imply I/O are not supported.
Note that this restriction also applies to low-level parallel programming models such as OpenCL, SYCL, oneAPI and CUDA.


14. Do I need a GPU to run TornadoVM?
------------------------------------------------------------

No. TornadoVM can also run on multi-core CPUs and/or FPGAs. What TornadoVM needs is a compatible driver/runtime installed in the machine.
For example, to enable TornadoVM getting access to an Intel CPU, developers can use the `Intel CPU Runtime for OpenCL <https://www.intel.com/content/www/us/en/developer/articles/technical/intel-cpu-runtime-for-opencl-applications-with-sycl-support.html>`__ (also part of the `Intel oneAPI Base Toolkit <https://www.intel.com/content/www/us/en/developer/tools/oneapi/base-toolkit.html>`__).

To enable TornadoVM accessing FPGAs, developers can use the Intel and AMD OpenCL implementations for the Intel and Xilinx FPGAs, respectively.
