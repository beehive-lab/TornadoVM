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

## Supported operations

All factories are static methods on `uk.ac.manchester.tornado.cublas.CuBlas` and are
used as the second argument of `taskGraph.libraryTask(id, factory, args...)`.

| Factory | cuBLAS function | Types | Semantics |
|---|---|---|---|
| `cublasSgemv` | `cublasSgemv` | `FloatArray` | y = α·op(A)·x + β·y |
| `cublasSgemm` | `cublasSgemm` | `FloatArray` | C = α·op(A)·op(B) + β·C |
| `cublasSgemmTF32` | `cublasSgemm` + TF32 math mode | `FloatArray` | Same as `cublasSgemm`, executed on TF32 Tensor Cores (~1e-4 rel error, up to 1.6x faster) |
| `cublasSgemmStridedBatched` | `cublasSgemmStridedBatched` | `FloatArray` | C[i] = α·op(A[i])·op(B[i]) + β·C[i] for a whole batch in one call; operand i lives at `base + i*stride` in one flat array |
| `cublasGemmExFP16` | `cublasGemmEx` | `HalfFloatArray` in/out | FP16 GEMM with FP32 Tensor Core accumulation |
| `cublasGemmExFP16FP32` | `cublasGemmEx` | `HalfFloatArray` in, `FloatArray` out | FP16 inputs, FP32 output (standard inference config) |
| `CuBlasLt.ltMatmulFP32` | `cublasLtMatmul` | `FloatArray` | FP32 matmul (heuristic algorithm, plan cached per shape) |
| `CuBlasLt.ltMatmulFP16` | `cublasLtMatmul` | `HalfFloatArray` | FP16 matmul, FP32 Tensor Core accumulation |
| `CuBlasLt.ltMatmulBiasFP16` | `cublasLtMatmul` + `BIAS` | `HalfFloatArray` | C = op(A)·op(B) + bias, fused |
| `CuBlasLt.ltMatmulGeluBiasFP16` | `cublasLtMatmul` + `GELU_BIAS` | `HalfFloatArray` | C = GELU(op(A)·op(B) + bias), fully fused transformer MLP block |

See [ROADMAP.md](ROADMAP.md) for what is planned next (Level-1/2, batched pointer
arrays, cuBLASLt epilogue fusion, INT8/FP8, ...) and the design/test that gates each.

### Row-major convention

cuBLAS is column-major; TornadoVM arrays are row-major. The standard tricks, used
throughout the tests:

- **SGEMV** — row-major `W (d rows × n cols)` is the column-major matrix `(n × d)` with
  `lda = n`, so `y = W·x` is:
  `cublasSgemv(CUBLAS_OP_T, n, d, alpha, W, n, x, 1, beta, y, 1)`
- **SGEMM** — row-major `C = A·B` is computed as column-major `C_cm = B_cm · A_cm`
  (swap the operands, pass `CUBLAS_OP_N`):
  `cublasSgemm(OP_N, OP_N, n, m, k, alpha, B, n, A, k, beta, C, n)`

### `beta != 0` (output is also an input)

When `beta != 0`, cuBLAS reads the output operand (`y`/`C`). The factories mark it
`READ_WRITE` automatically — include it in `transferToDevice` if its initial values
come from the host (see `TestCuBlasSgemvBeta`).

## Usage

### Mixing with TornadoVM pre/post tasks

Library tasks are scheduled like any other task: buffers written by a preceding JIT
task are read by the library task on the device, and the library task's output can
feed a following JIT task — dependencies come from the standard `Access[]`-driven
data-flow graph, and everything stays on one in-order CUDA stream:

```java
taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
        .task("mutate", MyClass::mutateData, matrix)                 // writes matrix on device
        .libraryTask("sgemv", CuBlas::cublasSgemv, ...)              // reads mutated matrix
        .task("scale", MyClass::mutatePost, output)                  // reads sgemv output
        .transferToHost(DataTransferMode.EVERY_EXECUTION, output);
```

Run the full demonstration:

```bash
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST
```

### CUDA Graphs

Library tasks are CUDA Graph compatible. With `executionPlan.withCUDAGraph()` the
whole region — EVERY_EXECUTION copies, JIT kernels, and the cuBLAS call — is captured
into a CUDA graph on the first execution and replayed with a single `cuGraphLaunch`
afterwards:

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
    plan.withCUDAGraph();
    for (int i = 0; i < iterations; i++) {
        plan.execute();   // iteration 0 captures, the rest replay
    }
}
```

```bash
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTasksCudaGraph
```

Notes:
- The cuBLAS handle (and workspace) is created before capture starts — native handle
  creation allocates device memory, which is illegal inside a capture region. This is
  automatic.
- With the default host pointer mode, `alpha`/`beta` are baked into the captured graph
  by value; re-capture (new execution plan) if they must change between replays.
- Per-call profiler timing is disabled while capturing (synchronizing invalidates the
  capture).

### Per-call tuning: `CuBlasOptions`

Math mode and workspace are passed per call with `CuBlasOptions`, attached to the
descriptor via the opaque `LibraryTaskDescriptor.withTuning(...)`:

```java
// Convenience factory (recommended for common cases):
.libraryTask("sgemm", CuBlas::cublasSgemmTF32, ...)

// Full control with a lambda instead of a method reference:
CuBlasOptions options = new CuBlasOptions()
        .withMathMode(CuBlasMathMode.CUBLAS_TF32_TENSOR_OP_MATH)
        .withWorkspace(32L * 1024 * 1024);   // 32 MiB user-owned workspace

.libraryTask("sgemm", (Integer ta, Integer tb, Integer m, Integer n, Integer k,
                       Float alpha, FloatArray a, Integer lda, FloatArray b, Integer ldb,
                       Float beta, FloatArray c, Integer ldc)
                -> CuBlas.cublasSgemm(ta, tb, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc)
                        .withTuning(options),
        transa, transb, m, n, k, alpha, matrixA, lda, matrixB, ldb, beta, matrixC, ldc)
```

- **Math mode** is set before the call and restored to `CUBLAS_DEFAULT_MATH` after.
- **Workspace** (`cublasSetWorkspace`) is allocated lazily per (device, execution
  plan), grows monotonically, and is freed when the execution plan closes. Useful for
  run-to-run reproducibility and required for device-launched graphs.

### Fused epilogues (cuBLASLt)

`CuBlasLt` factories fuse the bias-add and activation of a transformer MLP block into
the GEMM itself — one library task replaces a GEMM plus a JIT activation kernel:

```java
// C = GELU(A * B + bias), one fused cuBLASLt call (row-major operand-swap trick):
.libraryTask("mlp", CuBlasLt::ltMatmulGeluBiasFP16,
        OP_N, OP_N, n, m, k, 1.0f, B, n, A, k, 0.0f, C, n, bias)
```

The bias vector is applied per column of the row-major result (per output feature).
GELU uses the tanh approximation. Matmul plans (descriptors + heuristic-selected
algorithm) are created once per problem shape and cached in the per-(device, execution
plan) context, together with a 32 MiB device workspace; everything is freed when the
execution plan closes.

`BenchmarkLtFusedMlp` measures fusion vs the unfused hybrid (GemmEx FP16 + JIT
bias/GELU kernel) on the RTX 4090:

```bash
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkLtFusedMlp 2048 50
```

| Size | Unfused (GemmEx + JIT epilogue) | Fused (cuBLASLt GELU_BIAS) | Fusion speedup |
|---|---|---|---|
| 1024 | 17.5 TFLOP/s | 29.4 TFLOP/s | 1.68x |
| 2048 | 73.8 TFLOP/s | 98.3 TFLOP/s | 1.33x |
| 4096 | 132.1 TFLOP/s | 147.3 TFLOP/s | 1.11x |

Fusion pays most at small/medium sizes — the launch-and-memory-bound regime of LLM
decoding.

### Batched GEMM

One call launches a whole batch of equally-shaped GEMMs stored in flat arrays:

```java
long stride = (long) size * size;   // elements between consecutive matrices
.libraryTask("batched", CuBlas::cublasSgemmStridedBatched,
        OP_N, OP_N, size, size, size,
        1.0f, matrixB, size, stride, matrixA, size, stride,
        0.0f, matrixC, size, stride, batchCount)
```

### Flags, profiling, debugging

All standard TornadoVM flags apply; the most useful ones with library tasks:

| Flag | Effect |
|---|---|
| `--enableProfiler console` | Library tasks report `TASK_KERNEL_TIME` (host-timed, stream-marker bounded) plus `BACKEND`/`DEVICE`/`METHOD`, alongside regular tasks |
| `--printBytecodes` | Shows the library task as a regular `LAUNCH` between `ALLOC`/`TRANSFER` bytecodes (and inside `EXECUTION_GRAPH_BEGIN/END_CAPTURE` under CUDA Graphs) |
| `--devices` | Verify the CUDA backend/device is visible |
| `--jvm "-Dtornado.unittests.device=0:0"` | Select backend:device for the unit tests |

Errors from cuBLAS surface as `TornadoRuntimeException` with the decoded
`cublasStatus_t` (e.g. `CUBLAS_STATUS_INVALID_VALUE`). If the native library cannot be
loaded, the error message says so explicitly — rebuild with `make BACKEND=cuda` and
check that cuBLAS is installed.

## Tests

### Unit tests (tornado-unittests)

The JUnit suite lives in `tornado-unittests`
(`uk.ac.manchester.tornado.unittests.cublas.TestCuBlas`) and covers every supported
operation plus the mixed pre/post and CUDA Graph paths. The tests **skip automatically**
(JUnit assumption) when the default device is not on the CUDA backend or
`libtornado-cublas` is not available:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlas
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlasLt
```

`TestCuBlas#testSharedBufferAcrossTaskGraphs` additionally exercises the shared-buffer
pattern (`persistOnDevice` / `consumeFromDevice`): a JIT task graph produces the matrix
on the device and a second task graph consumes it with a cuBLAS call, without the data
ever returning to the host.

### Runnable examples

`main()`-runnable examples with validation against sequential Java:

```bash
# Standalone SGEMV (y = A * x)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemv 512

# SGEMV with beta != 0 (y = A * x + beta * y, output is READ_WRITE)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvBeta 512

# Standalone SGEMM (C = A * B)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemm 256

# TF32 Tensor Core SGEMM vs FP32 (accuracy + speed)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemmTF32 2048

# FP16 GemmEx (FP16 and FP32 outputs)
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasGemmExFP16 512

# User-owned cuBLAS workspace
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasWorkspace 256

# Mixed graph: JIT task -> cublasSgemv -> JIT task, 10 iterations
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST

# Same mixed pre/post graph under CUDA Graph capture/replay
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTasksCudaGraph
```

## Benchmarks

All reference numbers below are on an NVIDIA GeForce RTX 4090 (CUDA 12.6).

### SGEMM: cuBLAS vs TornadoVM kernels (compute-bound)

`BenchmarkSgemm` compares the naive `@Parallel` kernel, a tiled local-memory
KernelContext kernel (TS=32, myGEMM-style) as the optimized TornadoVM baseline, and
cuBLAS in FP32 / TF32 / FP16, all cross-validated:

```bash
# args: [size] [iterations]
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm 2048 50
```

| Size | Naive `@Parallel` | KernelContext (tiled) | cuBLAS FP32 | cuBLAS TF32 | cuBLAS FP16 | FP16 vs tiled |
|---|---|---|---|---|---|---|
| 1024 | 4.0 TFLOP/s | 3.9 TFLOP/s | 26.1 TFLOP/s | 28.2 TFLOP/s | 44.1 TFLOP/s | 11.3x |
| 2048 | 5.1 TFLOP/s | 6.0 TFLOP/s | 48.3 TFLOP/s | 62.8 TFLOP/s | 120.6 TFLOP/s | 20.4x |
| 4096 | 5.3 TFLOP/s | 5.8 TFLOP/s | 57.0 TFLOP/s | 81.1 TFLOP/s | **160.6 TFLOP/s** | **27.4x** |

160.6 TFLOP/s is the RTX 4090's peak FP16 tensor throughput; 81 TFLOP/s its TF32 peak.
Each step up the ladder is a factory-name swap in the task graph.

### SGEMV: cuBLAS vs TornadoVM kernels (memory-bound)

`MatrixVectorRowMajorWithCuBlas` mirrors the `MatrixVectorRowMajor` example from
`tornado-examples`, benchmarking `y = W * x` against the naive `@Parallel` kernel and
the optimized KernelContext workgroup-per-row kernel:

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

## Roadmap

[ROADMAP.md](ROADMAP.md) tracks cuBLAS API coverage (Level-1/2 with device-pointer
scalars, batched pointer arrays, cuBLASLt epilogue fusion, INT8/FP8, ...), with the
design, extension points, and gating test for each feature, plus the current progress
checklist.

## Adding a new library

Library bindings are discovered via `java.util.ServiceLoader` — no core runtime changes
are needed. Implement
`uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`, declare it with
`provides` in your module descriptor, expose factory methods that build a
`LibraryTaskDescriptor`, and add a JNI module for the native binding. See
`CuBlasLibraryProvider`, `CuBlas`, and `tornado-drivers/cublas-jni` as the reference
implementation, and `docs/source/hybrid-api.rst` for the full guide.
