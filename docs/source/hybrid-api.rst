.. _hybrid-api:

Hybrid API: Native Library Tasks
================================

The hybrid API lets a :code:`TaskGraph` mix JIT-compiled Java tasks with calls
into vendor-optimized native libraries (e.g., NVIDIA cuBLAS, cuFFT, cuDNN).
Library tasks share TornadoVM-managed device buffers with regular tasks, so
data produced by a JIT kernel can feed a library call (and vice versa)
without extra copies or special memory management, and everything runs on
the **same CUDA stream** — no host synchronization is introduced between a
JIT task and a library call.

Requires the CUDA backend: :code:`make BACKEND=cuda` (see :ref:`build-from-source`).

Example: cuBLAS (dense linear algebra)
---------------------------------------

.. code:: java

   TaskGraph taskGraph = new TaskGraph("cuBLAS")
       .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
       .task("preprocess", MyClass::preprocess, matrix)                    // JIT-compiled kernel
       .libraryTask("sgemv", CuBlas::cublasSgemv,                          // native cuBLAS call
               CuBlasOperation.CUBLAS_OP_T.operation(),
               m, n, alpha, matrix, lda, vector, incx, beta, output, incy)
       .task("postprocess", MyClass::postprocess, output)                  // JIT-compiled kernel
       .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

Runnable examples in the ``tornado-cublas`` module:

.. code:: bash

   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemv
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvBeta
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST

Example: cuFFT (fast Fourier transforms)
-------------------------------------------

A low-pass filter pipeline that mixes **two library tasks and two JIT tasks** in a
single graph, entirely on-device: cuFFT real-to-complex forward transform, a
JIT-compiled kernel that zeroes the high-frequency bins, cuFFT complex-to-real
inverse transform, and a JIT normalization kernel.

.. code:: java

   TaskGraph taskGraph = new TaskGraph("filter")
       .transferToDevice(DataTransferMode.EVERY_EXECUTION, signal)
       .libraryTask("forward", CuFft::cufftForwardR2C, signal, spectrum, n, 1)   // native cuFFT call
       .task("lowPass", FrequencyFilterExample::lowPass, spectrum, cutoff, bins) // JIT-compiled kernel
       .libraryTask("inverse", CuFft::cufftInverseC2R, spectrum, filtered, n, 1) // native cuFFT call
       .task("normalize", FrequencyFilterExample::scaleBy, filtered, 1.0f / n)   // JIT-compiled kernel
       .transferToHost(DataTransferMode.EVERY_EXECUTION, filtered);

.. code:: bash

   tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.FrequencyFilterExample
   tornado -m tornado.cufft/uk.ac.manchester.tornado.cufft.tests.BenchmarkFft

Example: cuDNN (deep-learning primitives)
--------------------------------------------

``BenchmarkConv2d`` runs a ResNet-style 2D convolution both as a JIT-compiled
direct-convolution kernel and as a cuDNN library task on the same device
buffers, and cross-validates the results:

.. code:: java

   TaskGraph cudnnGraph = new TaskGraph("cudnn")
       .transferToDevice(DataTransferMode.FIRST_EXECUTION, input, filter)
       .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, output, n, c, h, w, k, r, s, pad, stride)
       .transferToHost(DataTransferMode.UNDER_DEMAND, output);

.. code:: bash

   tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkConv2d
   tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkSdpa

``cuDNN`` also exposes ``cudnnSoftmax``, ``cudnnRelu``/``cudnnSigmoid``/``cudnnTanh``,
``cudnnMaxPool2d``, and fused FP16 scaled-dot-product (flash) attention via
``sdpaForward`` — see the full provider catalog linked below.

Performance
-----------

``BenchmarkSgemm`` compares the TornadoVM JIT-generated matrix-multiply kernel
against a cuBLAS SGEMM library task on the same device buffers:

.. code:: bash

   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm 2048 50

Reference numbers on an NVIDIA GeForce RTX 4090 (FP32, CUDA 12.6): the cuBLAS
library task reaches 24 / 46 / 51 TFLOP/s at sizes 1024 / 2048 / 4096, a 6-10x
speedup over the JIT-generated kernel (~4-5 TFLOP/s) with identical results.
On the same GPU, ``BenchmarkFft`` shows cuFFT at ~793x the JIT DFT kernel, and
``BenchmarkSdpa`` shows fused FP16 attention at 339-499x the JIT attention
kernel (64-84 TFLOP/s).

With :code:`--enableProfiler console`, library tasks report ``TASK_KERNEL_TIME``
(host-timed, bounded by stream markers) together with ``BACKEND``, ``DEVICE``
and ``METHOD``, alongside regular tasks.

How it works
------------

A library task is a :code:`SchedulableTask` without a sketch (like a pre-built
task): its per-argument :code:`Access[]` comes from the library binding, and it
flows through the standard data-flow graph and bytecode pipeline. The regular
``ALLOC`` / ``TRANSFER`` / ``LAUNCH`` bytecodes manage its buffers and
dependencies; at ``LAUNCH`` the interpreter resolves each argument to the raw
device pointer of its TornadoVM buffer (past the array header) and dispatches
the call through a library provider instead of launching a kernel.

The native library runs on the **same CUDA stream** as the backend's kernels
and transfers (the provider binds its handle with :code:`cublasSetStream`), so
ordering with surrounding tasks is automatic and no host synchronization is
introduced.

Because library calls share the backend stream, they are also **CUDA Graph
compatible**: with :code:`executionPlan.withCUDAGraph()` the library call is
recorded into the captured graph together with the surrounding kernels and
transfers, and replayed with a single :code:`cuGraphLaunch` on subsequent
executions (see ``TestCuBlasSgemvWithTasksCudaGraph``). Native contexts are
created in the pre-compilation pass, before capture starts, since handle
creation allocates device memory; per-call profiler timing is disabled while
capturing.

Provider SPI
------------

Library bindings are discovered with :code:`java.util.ServiceLoader` — adding
a new library requires **no changes to the core runtime**:

1. Implement
   :code:`uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`
   (see ``CuBlasLibraryProvider`` in ``tornado-cublas``): create a native
   context per (device, execution plan), dispatch function calls from a
   :code:`LibraryInvocation` (resolved device pointers + boxed scalars), and
   release the context when the execution plan closes.
2. Declare it in the module descriptor:
   :code:`provides TornadoLibraryProvider with MyProvider;`
3. Expose user-facing factory methods that build a
   :code:`LibraryTaskDescriptor` (library name, function name, parameters,
   accesses), following ``CuBlas.cublasSgemv``.
4. Add a JNI module for the native binding (see
   ``tornado-drivers/cublas-jni``), linked under the ``cuda-backend`` Maven
   profile.

Backends expose their native stream to providers through
:code:`TornadoNativeStreamSupport` (implemented by the CUDA backend). Provider
:code:`canHandle(device)` rejects devices without native-stream interop.

Scope and roadmap
-----------------

Implemented today, via the same provider SPI: **cuBLAS** and **cuBLASLt**
(dense linear algebra, fused-epilogue GEMM), **cuFFT** (FFTs), **cuDNN**
(deep-learning primitives, including fused FP16 flash attention), **CUTLASS**
(open-template FP32/FP16 GEMM with fused epilogues), and **cuTENSOR** (tensor
contractions / einsum). Each is a Java module pair (a ``tornado-<lib>`` API
module plus a native ``*-jni`` module) with per-(plan, device) contexts for
cached descriptors and plans. The native ``*-jni`` module for each library
builds only under the ``cuda-backend`` Maven profile, and is
self-guarding — if the library/toolkit isn't installed, the native side is
skipped and that provider reports ``UNSUPPORTED`` at runtime rather than
failing the build. Some libraries need an extra runtime dependency (e.g.,
cuDNN needs ``libcudnn9``); see the full guide linked below for per-library
install requirements.

When :code:`beta != 0` in a cuBLAS GEMM/GEMV call, the output operand is also
read; the binding marks it ``READ_WRITE`` automatically (include it in
:code:`transferToDevice` if its initial values come from the host). Batch
processing (:code:`withBatch`) is not supported for library tasks.

Header-only device libraries (CUB, CUTLASS/CuTe) plug into the CUDA-C
backend's NVRTC compilation rather than the library-task path.

Note that cuBLAS assumes column-major storage: for row-major TornadoVM arrays
pass the transpose operation (SGEMV) or swap operands (SGEMM), as in the
example tests.

See also
--------

`HYBRID_API_GUIDE.md <https://github.com/beehive-lab/TornadoVM/blob/master/HYBRID_API_GUIDE.md>`__
(repository root) is a complete, example-driven guide to every provider
(cuBLAS, cuBLASLt, cuFFT, cuDNN, CUTLASS, cuTENSOR): factory tables, code
snippets, composition patterns, CUDA-Graph usage, build/install
requirements, CLI flags, a "write your own provider" walkthrough, and a
troubleshooting table.
