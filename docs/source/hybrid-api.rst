Hybrid API: Native Library Tasks
================================

The hybrid API lets a :code:`TaskGraph` mix JIT-compiled Java tasks with calls
into vendor-optimized native libraries (e.g., NVIDIA cuBLAS). Library tasks
share TornadoVM-managed device buffers with regular tasks, so data produced by
a JIT kernel can feed a library call (and vice versa) without extra copies or
special memory management.

.. code:: java

   TaskGraph taskGraph = new TaskGraph("cuBLAS")
       .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
       .task("preprocess", MyClass::preprocess, matrix)                    // JIT-compiled kernel
       .libraryTask("sgemv", CuBlas::cublasSgemv,                          // native cuBLAS call
               CuBlasOperation.CUBLAS_OP_T.operation(),
               m, n, alpha, matrix, lda, vector, incx, beta, output, incy)
       .task("postprocess", MyClass::postprocess, output)                  // JIT-compiled kernel
       .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

Run with the CUDA backend (:code:`make BACKEND=cuda`). Examples live in the
``tornado-cublas`` module:

.. code:: bash

   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemv
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvBeta
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST

Performance
-----------

``BenchmarkSgemm`` compares the TornadoVM JIT-generated matrix-multiply kernel
against a cuBLAS SGEMM library task on the same device buffers:

.. code:: bash

   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm 2048 50

Reference numbers on an NVIDIA GeForce RTX 4090 (FP32, CUDA 12.6): the cuBLAS
library task reaches 24 / 46 / 51 TFLOP/s at sizes 1024 / 2048 / 4096, a 6-10x
speedup over the JIT-generated kernel (~4-5 TFLOP/s) with identical results.

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

Currently supported: FP32 :code:`cublasSgemv` and :code:`cublasSgemm` on the
CUDA backend. When :code:`beta != 0` the output operand is also read by cuBLAS;
the binding marks it ``READ_WRITE`` automatically (include it in
:code:`transferToDevice` if its initial values come from the host). Batch
processing (:code:`withBatch`) is not supported for library tasks.

The same SPI accommodates other host-API libraries (cuBLASLt, cuDNN, cuFFT,
cuSPARSE, cuSOLVER, cuTENSOR, NCCL): each is a module pair implementing the
provider interface, with per-(plan, device) contexts for cached descriptors
and plans, and a workspace-allocation hook planned for libraries that need
scratch buffers. Header-only device libraries (CUB, CUTLASS/CuTe) are a
different integration track — they plug into the CUDA-C backend's NVRTC
compilation, not the library-task path.

Note that cuBLAS assumes column-major storage: for row-major TornadoVM arrays
pass the transpose operation (SGEMV) or swap operands (SGEMM), as in the
example tests.

See also
--------

``HYBRID_API_GUIDE.md`` (repository root) is a complete, example-driven guide to
every provider (cuBLAS, cuBLASLt, cuFFT, cuDNN, CUTLASS, cuTENSOR): factory
tables, code snippets, composition patterns, CUDA-Graph usage, build/install
requirements, CLI flags, a "write your own provider" walkthrough, and a
troubleshooting table.
