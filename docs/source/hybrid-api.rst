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
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm
   tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST

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
CUDA backend. Batch processing (:code:`withBatch`) is not supported for
library tasks.

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
