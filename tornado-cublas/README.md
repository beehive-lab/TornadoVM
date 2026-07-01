# TornadoVM Hybrid API — cuBLAS Library Tasks

This module lets a TornadoVM `TaskGraph` mix JIT-compiled Java tasks with calls into
NVIDIA cuBLAS. Library tasks share TornadoVM-managed device buffers with regular tasks
and run on the same CUDA stream, so a kernel can produce data for a cuBLAS call (and
consume its output) with no extra copies and no host synchronization.

```java
TaskGraph taskGraph = new TaskGraph("cuBLAS")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
    .task("preprocess", MyClass::preprocess, matrix)                  // JIT-compiled kernel
    .libraryTask("sgemv", CuBlas::cublasSgemv,                        // native cuBLAS call
            CuBlasOperation.CUBLAS_OP_T.operation(),
            m, n, alpha, matrix, lda, vector, incx, beta, output, incy)
    .task("postprocess", MyClass::postprocess, output)                // JIT-compiled kernel
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
    plan.execute();
}
```

## Requirements

- NVIDIA GPU + driver
- CUDA toolkit with cuBLAS (headers and `libcublas`); on systems with multiple
  toolkits, `/usr/local/cuda` (or `$CUDA_PATH`) is preferred
- JDK 21

## Build

From the repository root:

```bash
make BACKEND=cuda
source setvars.sh
```

This builds the CUDA backend, the `tornado-cublas` Java module, and the
`tornado-drivers/cublas-jni` native binding (`libtornado-cublas.so`), and bundles them
into the SDK (`tornado.cublas` is added to the launcher's `--add-modules` automatically).

## Run

```bash
# Standalone SGEMV (y = A * x), validated against sequential Java
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemv 512

# Standalone SGEMM (C = A * B)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm 256

# Mixed graph: JIT task -> cublasSgemv -> JIT task, 10 iterations
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST
```

Each test prints `Result is correct` when the GPU result matches the sequential Java
reference. To inspect how the library task is scheduled (a regular `LAUNCH` between
`ALLOC`/`TRANSFER` bytecodes), add `--printBytecodes`.

## Supported functions

| Function | Types | Notes |
|---|---|---|
| `cublasSgemv` | FP32 (`FloatArray`) | y = alpha * op(A) * x + beta * y |
| `cublasSgemm` | FP32 (`FloatArray`) | C = alpha * op(A) * op(B) + beta * C |

cuBLAS is column-major: for row-major TornadoVM arrays pass `CUBLAS_OP_T` (SGEMV) or
swap the operands (SGEMM), as done in the tests. Batch processing (`withBatch`) is not
supported for library tasks.

## Adding a new library

Library bindings are discovered via `java.util.ServiceLoader` — no core runtime changes
are needed. Implement
`uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`, declare it with
`provides` in your module descriptor, expose factory methods that build a
`LibraryTaskDescriptor`, and add a JNI module for the native binding. See
`CuBlasLibraryProvider`, `CuBlas`, and `tornado-drivers/cublas-jni` as the reference
implementation, and `docs/source/hybrid-api.rst` for the full guide.
