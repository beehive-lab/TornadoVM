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

# SGEMV with beta != 0 (y = A * x + beta * y, output is READ_WRITE)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvBeta 512

# Standalone SGEMM (C = A * B)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm 256

# Mixed graph: JIT task -> cublasSgemv -> JIT task, 10 iterations
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST

# Matrix-vector: TornadoVM kernels (naive @Parallel + optimized KernelContext)
# vs the equivalent cuBLAS SGEMV library task
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.MatrixVectorRowMajorWithCuBlas 8192 2048 32

# Same mixed pre/post graph captured into a CUDA graph on the first execution
# and replayed on subsequent iterations (executionPlan.withCUDAGraph())
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTasksCudaGraph
```

Library tasks are CUDA Graph compatible: with `executionPlan.withCUDAGraph()` the cuBLAS
call is recorded into the captured graph together with the surrounding kernels and
transfers, and replayed with a single `cuGraphLaunch`. The cuBLAS handle is created
before capture starts (native handle creation allocates device memory, which is illegal
inside a capture region), and per-call profiling is disabled while capturing.

Each test prints `Result is correct` when the GPU result matches the sequential Java
reference. To inspect how the library task is scheduled (a regular `LAUNCH` between
`ALLOC`/`TRANSFER` bytecodes), add `--printBytecodes`. With `--enableProfiler console`,
library tasks report `TASK_KERNEL_TIME` alongside regular tasks.

## Benchmarks

All reference numbers below are on an NVIDIA GeForce RTX 4090 (FP32, CUDA 12.6).

### SGEMM: cuBLAS vs TornadoVM kernels (compute-bound)

`BenchmarkSgemm` compares two TornadoVM kernels — the naive `@Parallel` version and a
tiled local-memory KernelContext version (TS=32, myGEMM-style) as the optimized
baseline — against a cuBLAS SGEMM library task on the same device buffers, and
cross-validates all results:

```bash
# args: [size] [iterations]
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm 2048 50
```

| Size | Naive `@Parallel` | KernelContext (tiled) | cuBLAS library task | cuBLAS vs tiled |
|---|---|---|---|---|
| 1024 | 4.1 TFLOP/s | 3.9 TFLOP/s | 25.6 TFLOP/s | 6.5x |
| 2048 | 4.8 TFLOP/s | 6.0 TFLOP/s | 48.5 TFLOP/s | 8.1x |
| 4096 | 5.3 TFLOP/s | 5.8 TFLOP/s | 53.1 TFLOP/s | 9.1x |

### SGEMV: cuBLAS vs TornadoVM kernels (memory-bound)

`MatrixVectorRowMajorWithCuBlas` mirrors the `MatrixVectorRowMajor` example from
`tornado-examples`: it benchmarks `y = W * x` with the naive `@Parallel` kernel, the
optimized KernelContext workgroup-per-row kernel, and the equivalent cuBLAS SGEMV
library task, all validated against sequential Java:

```bash
# args: [inputDim] [outputDim] [localWorkGroupSize]
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.MatrixVectorRowMajorWithCuBlas 8192 2048 32
```

Results at 8192x2048 (120 iterations, end-to-end execute):

| Variant | Time | Bandwidth | Speedup vs sequential |
|---|---|---|---|
| Sequential Java | 8.551 ms | 7.9 GB/s | 1.0x |
| `@Parallel` kernel (naive) | 3.309 ms | 20.3 GB/s | 2.6x |
| KernelContext kernel (workgroup-per-row) | 0.164 ms | 410 GB/s | 52x |
| cuBLAS SGEMV library task | 0.116 ms | 578 GB/s | 74x |

For this memory-bound kernel, cuBLAS is 1.4x faster than the best hand-optimized
TornadoVM kernel — a much smaller gap than the compute-bound SGEMM above.

## Supported functions

| Function | Types | Notes |
|---|---|---|
| `cublasSgemv` | FP32 (`FloatArray`) | y = alpha * op(A) * x + beta * y |
| `cublasSgemm` | FP32 (`FloatArray`) | C = alpha * op(A) * op(B) + beta * C |

cuBLAS is column-major: for row-major TornadoVM arrays pass `CUBLAS_OP_T` (SGEMV) or
swap the operands (SGEMM), as done in the tests. When `beta != 0` the output operand is
also an input; the binding marks it `READ_WRITE` automatically so its device contents
stay valid (include it in `transferToDevice` if the initial values come from the host).
Batch processing (`withBatch`) is not supported for library tasks.

## Roadmap

[ROADMAP.md](ROADMAP.md) tracks cuBLAS API coverage (math modes, GemmEx mixed precision,
strided-batched, cuBLASLt epilogue fusion, Level-1/2 with device-pointer scalars, ...),
with the design, extension points, and gating test for each feature, plus the current
progress checklist.

## Adding a new library

Library bindings are discovered via `java.util.ServiceLoader` — no core runtime changes
are needed. Implement
`uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`, declare it with
`provides` in your module descriptor, expose factory methods that build a
`LibraryTaskDescriptor`, and add a JNI module for the native binding. See
`CuBlasLibraryProvider`, `CuBlas`, and `tornado-drivers/cublas-jni` as the reference
implementation, and `docs/source/hybrid-api.rst` for the full guide.
