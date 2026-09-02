# TornadoVM Hybrid API — Complete Guide

The **hybrid API** lets a single TornadoVM `TaskGraph` mix JIT-compiled Java
tasks (`@Parallel` / `KernelContext`) with calls into vendor-optimized native
GPU libraries — **cuBLAS, cuBLASLt, cuFFT, cuDNN, cuSPARSE, and CUTLASS**.
A native call becomes a **library task**: it shares TornadoVM-managed device
buffers with the surrounding kernels, runs on the same CUDA stream, and is
captured into CUDA Graphs — so data produced by a JIT kernel feeds a library
call (and vice-versa) with **no extra copies and no manual memory management**.

> Requires the **CUDA backend** (`make BACKEND=cuda`) and an NVIDIA GPU.
> Library tasks are silently reported as `UNSUPPORTED` on OpenCL/Metal.

---

## Table of contents

1. [Quick start](#1-quick-start)
2. [Core concepts](#2-core-concepts)
3. [Provider catalog](#3-provider-catalog) — cuBLAS · cuBLASLt · cuFFT · cuDNN · CUTLASS · cuSPARSE
4. [Composition patterns](#4-composition-patterns)
5. [CUDA Graphs](#5-cuda-graphs)
6. [Execution-plan controls](#6-execution-plan-controls)
7. [Profiling](#7-profiling)
8. [Build, install & flags](#8-build-install--flags)
9. [Write your own provider](#9-write-your-own-provider)
10. [Reference: layout, data types, alignment](#10-reference-layout-data-types-alignment)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Quick start

A GEMV where a JIT kernel preprocesses the matrix, cuBLAS does the multiply, and
another JIT kernel postprocesses the result — all on the same device buffers:

```java
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.cublas.CuBlas;
import uk.ac.manchester.tornado.cublas.enums.CuBlasOperation;

TaskGraph graph = new TaskGraph("hybrid")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, vector)
    .task("pre",  MyKernels::preprocess, matrix)                       // JIT
    .libraryTask("sgemv", CuBlas::cublasSgemv,                         // native cuBLAS
            CuBlasOperation.CUBLAS_OP_T.operation(),
            m, n, 1.0f, matrix, lda, vector, 1, 0.0f, output, 1)
    .task("post", MyKernels::postprocess, output)                     // JIT
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.execute();
}
```

Run it:

```bash
make BACKEND=cuda
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.TestCuBlasSgemvWithTornadoVMTasksPOST
```

The only new API surface is **`.libraryTask(id, factory, args...)`** — a sibling
of `.task(...)` that takes a provider factory method reference plus its
arguments. There are 20 overloads (`libraryTask` with 1–20 typed arguments).

---

## 2. Core concepts

### 2.1 What a library task is

A library task is a `SchedulableTask` **without a sketch** (like a pre-built
task). It never goes through the JIT compiler. Instead:

- Its per-argument `Access[]` (READ_ONLY / WRITE_ONLY / READ_WRITE) comes from
  the provider factory, so the data-flow graph knows what to transfer and when.
- It flows through the standard `ALLOC` / `TRANSFER` / `LAUNCH` bytecodes.
- At `LAUNCH`, the interpreter resolves each *reference* argument to the **raw
  device pointer** of its TornadoVM buffer (past the 24-byte array header) and
  dispatches the call through a **library provider** instead of launching a
  kernel. Scalars are passed through as boxed values.

### 2.2 Shared device buffers

Because library tasks use the *same* TornadoVM buffers as JIT tasks, a value
computed on the GPU stays on the GPU across the JIT ↔ native boundary. You only
`transferToDevice` / `transferToHost` at the graph edges.

### 2.3 Same stream, automatic ordering

The provider binds its native handle to the backend's CUDA stream (e.g.
`cublasSetStream`, `cudnnSetStream`, `cusparseSetStream`; CUTLASS takes the
stream per call).
Everything — JIT kernels, transfers, library calls — runs **in order on one
stream**, with no host synchronization.

### 2.4 The `prepare()` hook (capture safety)

Libraries that create per-shape plans or workspaces (cuDNN, CUTLASS, cuFFT,
cuSPARSE, cuBLAS) do that allocation in a `prepare()` hook that the interpreter calls in
the **pre-compilation pass, before CUDA-Graph capture starts** (allocation is
illegal mid-capture). `prepare()` is idempotent (plan-cache lookup), so the
per-`libraryTask` `dispatch()` allocates nothing and is capture-safe.

### 2.5 Provider ids

Each provider registers a unique id, matched by the factory:

| Provider id | Module |
|---|---|
| `nvidia/cublas` | `tornado-cublas` |
| `nvidia/cublaslt` | `tornado-cublas` |
| `nvidia/cufft` | `tornado-cufft` |
| `nvidia/cudnn` | `tornado-cudnn` |
| `nvidia/cusparse` | `tornado-cusparse` |
| `nvidia/cutlass` | `tornado-cutlass` |

---

## 3. Provider catalog

### 3.1 cuBLAS — dense linear algebra (`nvidia/cublas`)

FP32/FP16 matrix-vector and matrix-matrix products. **cuBLAS is column-major**,
so for row-major TornadoVM arrays either pass the transpose op (SGEMV) or swap
operands (SGEMM).

Factories (`uk.ac.manchester.tornado.cublas.CuBlas`):

| Factory | Operation |
|---|---|
| `cublasSgemv(op, m, n, alpha, A, lda, x, incx, beta, y, incy)` | `y = alpha·op(A)·x + beta·y` |
| `cublasSgemm(opA, opB, m, n, k, alpha, A, lda, B, ldb, beta, C, ldc)` | `C = alpha·op(A)·op(B) + beta·C` |
| `cublasSgemmTF32(...)` | SGEMM using TF32 tensor cores |
| `cublasGemmExFP16(...)` | FP16 inputs and output, tensor-core GEMM |
| `cublasGemmExFP16FP32(...)` | FP16 inputs, **FP32 output** — keeps accumulation precision at the boundary |
| `cublasGemmExBF16(...)` | BF16 inputs and output (`BFloat16Array`), tensor-core GEMM |
| `cublasSgemmStridedBatched(...)` | batched SGEMM |

```java
// Row-major C = A·B computed as column-major C_cm = B_cm · A_cm (operands swapped)
taskGraph.libraryTask("sgemm", CuBlas::cublasSgemm,
        CuBlasOperation.CUBLAS_OP_N.operation(), CuBlasOperation.CUBLAS_OP_N.operation(),
        size, size, size, 1.0f, matrixB, size, matrixA, size, 0.0f, output, size);
```

> **`beta != 0`**: the output is also *read* by cuBLAS, so the binding marks it
> `READ_WRITE` automatically — include it in `transferToDevice` if its initial
> values come from the host.

Reference (RTX 4090, FP32, CUDA 12.6): cuBLAS SGEMM 24 / 46 / 51 TFLOP/s at
1024 / 2048 / 4096 — 6–10× the JIT kernel with identical results.

### 3.2 cuBLASLt — fused-epilogue GEMM (`nvidia/cublaslt`)

FP16 GEMM with a **fused bias + activation epilogue** (BIAS, GELU_BIAS) — one
kernel instead of GEMM + separate bias/activation passes. FP32 and FP8 (E4M3 in,
FP16 out) matmuls use the same plan cache and 32 MiB workspace.

| Factory | Epilogue |
|---|---|
| `ltMatmulFP32` / `ltMatmulFP16` | none |
| `ltMatmulFP8` | none (E4M3 operands, FP16 output, TN form, `ld` multiple of 16 B) |
| `ltMatmulBiasFP16` | `BIAS` |
| `ltMatmulGeluBiasFP16` | `GELU_BIAS` (tanh approximation) |

```java
taskGraph.libraryTask("mlp", CuBlasLt::ltMatmulGeluBiasFP16,
        transa, transb, m, n, k, alpha, aFP16, lda, bFP16, ldb, beta, dFP16, ldd, bias);
```

`BenchmarkLtFusedMlp`: fusion is **1.1–1.7×** the unfused path (biggest win in
the launch-bound regime).

### 3.3 cuFFT — fast Fourier transforms (`nvidia/cufft`)

Complex and real transforms with a per-`(n, batch)` cached plan.

| Factory | Transform |
|---|---|
| `cufftForwardC2C` / `cufftInverseC2C(input, output, n, batch)` | 1D complex→complex |
| `cufftForwardR2C` / `cufftInverseC2R(input, output, n, batch)` | real↔complex (Hermitian, `n ↔ n/2+1`) |
| `cufftForwardZ2Z` / `cufftInverseZ2Z(input, output, n, batch)` | FP64 complex→complex, 1D |
| `cufftForward2dC2C` / `cufftInverse2dC2C(input, output, nx, ny)` | 2D complex→complex |

```java
// fft → JIT low-pass filter → ifft → JIT normalize, all on-device
new TaskGraph("filter")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, signal)
    .libraryTask("fwd",  CuFft::cufftForwardC2C, signal, freq, n, 1)
    .task("lowpass",     Filters::lowPass, freq, cutoff)
    .libraryTask("inv",  CuFft::cufftInverseC2C, freq, out, n, 1)
    .task("normalize",   Filters::scale, out, 1.0f / n)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
```

`BenchmarkFft` (n=65536): cuFFT **793×** the JIT DFT kernel.

### 3.4 cuDNN — deep-learning primitives (`nvidia/cudnn`)

FP32/NCHW ops plus **fused FP16 SDPA / flash attention**.

| Factory | Operation |
|---|---|
| `cudnnSoftmax(input, output, rows, cols)` | per-row numerically-stable softmax |
| `cudnnRelu` / `cudnnSigmoid` / `cudnnTanh(input, output, size)` | activations |
| `cudnnMaxPool2d(input, output, n, c, h, w, window, stride)` | 2D max pooling |
| `cudnnConv2d(input, filter, output, n, c, h, w, k, r, s, pad, stride)` | 2D convolution |
| `sdpaForward(q, k, v, o, b, h, sQ, sKv, d, scale, causal)` | fused scaled-dot-product attention (FP16, BHSD) |

```java
// conv → JIT bias-add → relu → maxpool, one graph
new TaskGraph("cnn")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter)
    .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, convOut, n, c, h, w, k, r, s, pad, stride)
    .task("bias",        Layers::addBias, convOut, bias)
    .libraryTask("relu", CuDnn::cudnnRelu, convOut, reluOut, n * k * outH * outW)
    .libraryTask("pool", CuDnn::cudnnMaxPool2d, reluOut, pooled, n, k, outH, outW, 2, 2)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, pooled);
```

`BenchmarkSdpa`: **339× / 499×** the JIT attention kernel (64–84 TFLOP/s).
`BenchmarkConv2d`: 3.1–5.3× the JIT convolution.

### 3.5 CUTLASS — open-template GEMM (`nvidia/cutlass`)

FP32 SIMT, FP16 and BF16 tensor-core GEMM (single and batched) plus **fused
GEMM + bias + activation** epilogues. **Row-major** (`C = alpha·A·B + beta·C`) — no cuBLAS transpose dance.

| Factory | Operation |
|---|---|
| `cutlassSgemm(m, n, k, alpha, A, B, beta, C)` | FP32 SIMT GEMM |
| `cutlassHgemm(m, n, k, alpha, A, B, beta, D)` | FP16 tensor-core GEMM, FP32 accumulate |
| `cutlassHgemmBatched(m, n, k, alpha, A, B, beta, C, batchCount)` | batched FP16 tensor-core GEMM |
| `cutlassBgemm(m, n, k, alpha, A, B, beta, C)` | BF16 tensor-core GEMM (`BFloat16Array`) |
| `cutlassGemmBiasRelu(m, n, k, A, B, bias, D)` | fused `relu(A·B + bias)` |
| `cutlassGemmBiasGelu(m, n, k, A, B, bias, D)` | fused `gelu(A·B + bias)` |
| `cutlassGemmBiasSilu(m, n, k, A, B, bias, D)` | fused `silu(A·B + bias)` |
| `cutlassGemmBiasSigmoid(m, n, k, A, B, bias, D)` | fused `sigmoid(A·B + bias)` |
| `cutlassGemmBiasTanh(m, n, k, A, B, bias, D)` | fused `tanh(A·B + bias)` |
| `cutlassGemmBiasHardSwish(m, n, k, A, B, bias, D)` | fused `hardswish(A·B + bias)` |

```java
// A two-layer FFN block: relu(x·W1+b1) then gelu(h·W2+b2), two fused CUTLASS tasks
new TaskGraph("ffn")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, w1, b1, w2, b2)
    .libraryTask("l1", Cutlass::cutlassGemmBiasRelu, m, n1, k1, x, w1, b1, h)
    .libraryTask("l2", Cutlass::cutlassGemmBiasGelu, m, n2, n1, h, w2, b2, y)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
```

**Constraint:** TornadoVM device pointers are only 8-byte aligned (array base +
24-byte header), so the FP16 kernels use 4-half vector loads and require
**`k` and `n` to be multiples of 4** — the factory rejects other shapes with a
clear message. FP32 SIMT has no constraint.

The fused-epilogue factories take `HalfFloatArray` operands.

`BenchmarkCutlassGemm` (RTX 4090, 1024): FP16 tensor-core **39 TFLOP/s = 7.7×**
the tiled `KernelContext` kernel; fused epilogue **1.28×** the unfused path.

### 3.6 cuSPARSE — sparse linear algebra (`nvidia/cusparse`)

FP32 sparse matrix products over **CSR** (32-bit, zero-based indices), with
`alpha = 1.0` and `beta = 0.0`.

| Factory | Operation |
|---|---|
| `cusparseSpMV(rows, cols, nnz, csrRowOffsets, csrColInd, csrValues, x, y)` | `y = A·x` |
| `cusparseSpMM(rows, k, n, nnz, csrRowOffsets, csrColInd, csrValues, b, c)` | `C = A·B`, dense `B`/`C` row-major |

```java
new TaskGraph("spmv")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, rowOffsets, colIndices, values, x)
    .libraryTask("spmv", Cusparse::cusparseSpMV, rows, cols, nnz, rowOffsets, colIndices, values, x, y)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
```

The provider pre-allocates an 8 MiB workspace in `prepare()`. A matrix needing
more than that is rejected while capturing a CUDA graph, because the workspace
cannot grow inside a capture region — run the graph once without CUDA graphs
first, or reduce the problem size.

> **cuTENSOR** (`nvidia/cutensor`, tensor contractions / einsum) is implemented
> on branch `hybrid-cutensor` but is **not part of this build**.

---

## 4. Composition patterns

### 4.1 JIT → library → JIT (single graph)

The canonical pattern from §1: a native call sandwiched between JIT tasks,
sharing buffers. Loop the plan for repeated execution — buffers are reused.

### 4.2 Library → library chains

Two native calls in one graph, the intermediate staying on-device:

```java
new TaskGraph("chain")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, c)
    .libraryTask("gemm1", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, ab)  // ab = a·b
    .libraryTask("gemm2", Cutlass::cutlassSgemm, m, n, n, 1.0f, ab, c, 0.0f, e)  // e  = ab·c
    .transferToHost(DataTransferMode.EVERY_EXECUTION, e);
```

### 4.3 Shared buffers across task graphs

Persist a native result on the device and consume it in a second graph — no host
round trip:

```java
TaskGraph producer = new TaskGraph("producer")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
    .libraryTask("gemm1", Cutlass::cutlassSgemm, m, n, k, 1.0f, a, b, 0.0f, ab)
    .persistOnDevice(ab);

TaskGraph consumer = new TaskGraph("consumer")
    .consumeFromDevice(producer.getTaskGraphName(), ab)
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, c)
    .libraryTask("gemm2", Cutlass::cutlassSgemm, m, n, n, 1.0f, ab, c, 0.0f, e)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, e);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(producer.snapshot(), consumer.snapshot())) {
    plan.withGraph(0).execute();
    plan.withGraph(1).execute();
}
```

### 4.4 Mixed precision in one graph

FP32 and FP16 library tasks can coexist:

```java
.libraryTask("sgemm", Cutlass::cutlassSgemm, m, n, k, 1.0f, aF, bF, 0.0f, cF)   // FloatArray
.libraryTask("hgemm", Cutlass::cutlassHgemm, m, n, k, 1.0f, aH, bH, 0.0f, dH)   // HalfFloatArray
```

---

## 5. CUDA Graphs

Because library calls ride the backend stream, they are recorded into a captured
CUDA Graph together with the surrounding kernels and transfers, then replayed
with a single `cuGraphLaunch`:

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withCUDAGraph();          // iteration 0 captures; the rest replay
    for (int i = 0; i < iterations; i++) {
        plan.execute();
    }
}
```

Native contexts, plans, and workspaces are created in the pre-compile pass
(`prepare()`), before capture begins, so capture sees no illegal allocation.
Per-call profiler timing is disabled while capturing.

---

## 6. Execution-plan controls

Beyond `withCUDAGraph()`, a few `TornadoExecutionPlan` options matter
specifically when native library calls and JIT kernels share a plan.

| Option | What it does | Backends |
|---|---|---|
| `withPreCompilation()` | JIT every task graph up front. For hybrid plans this also runs each provider's `prepare()`, so native contexts, plans and workspaces exist before the first execution — the same reason it is a prerequisite for graph capture | all |
| `withIntraPlanConcurrency()` | Routes DAG-independent work (H2D, kernels, D2H) to separate role streams so it can overlap, with ordering preserved through device events derived from the bytecode dependency DAG. Off by default | CUDA; no-op on OpenCL/Metal |
| `withStagedTransfers()` | Stages non-batched read-only transfers through pinned host memory instead of the direct path | CUDA; no-op on OpenCL/Metal |
| `withMemoryLimit("8GB")` | Caps device memory for the plan. Worth setting explicitly when library workspaces sit alongside large operands | all |
| `withWarmUpIterations(n)` | Runs the whole plan `n` times first — transfers, compilation and execution — before the measured run | all |
| `withProfiler(ProfilerMode.CONSOLE)` | Same data as `--enableProfiler`, switched on programmatically | all |

### 6.1 Placing large, stable inputs once

`TornadoExecutionPlan.transferToDevice(Object...)` uploads the current host
contents of specific objects **without running the plan**. It is aimed at inputs
that are large and do not change — model weights, a lookup table, a mesh —
where the alternative is to let the first execution pay the whole upload, or to
run the plan once on dummy data purely to make the transfer happen:

```java
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withPreCompilation()
        .transferToDevice(weights);   // on the device when the call returns

    for (int i = 0; i < iterations; i++) {
        plan.execute();               // only the per-iteration inputs move
    }
}
```

Each object is uploaded by the task graphs of this plan that take it as a
parameter; graphs that do not know it ignore it. An object with no device buffer
yet gets one, so this works before the plan has ever run. Objects declared
`FIRST_EXECUTION` are not uploaded again by the first real execution.

---

## 7. Profiling

```bash
tornado --enableProfiler console -m <module>/<MainClass>
```

Library tasks report `TASK_KERNEL_TIME` (host-timed, bounded by stream markers)
alongside `BACKEND`, `DEVICE`, and `METHOD`, together with regular tasks.

To confirm native and JIT kernels share one stream, profile with **nsys**:

```bash
nsys profile --trace=cuda -o run tornado -m <module>/<MainClass>
nsys stats --report cuda_gpu_trace run.nsys-rep   # library kernels + JIT kernels, same Strm
```

---

## 8. Build, install & flags

### 8.1 Build

```bash
make BACKEND=cuda        # activates the cuda-backend Maven profile
```

The Java modules (`tornado-cublas`, `tornado-cufft`, `tornado-cudnn`,
`tornado-cusparse`, `tornado-cutlass`) always compile. `cublas`, `cufft` and
`cusparse` bind straight to the toolkit through `java.lang.foreign` — no
native module at all. `cudnn` and `cutlass` still carry a native module
(`cudnn-jni` for the cudnn-frontend SDPA shim, `cutlass-jni` for `nvcc`-compiled
device code) that builds only under the `cuda-backend` profile. Either way it is
**self-guarding**: if a library/toolkit is missing, the `SymbolLookup` (or the
native `.so`) comes back empty and that provider reports `UNSUPPORTED` at
runtime rather than failing the build.

The launcher adds the provider modules to `--add-modules` automatically when the
CUDA backend is present:
`tornado.cublas, tornado.cufft, tornado.cudnn, tornado.cusparse, tornado.cutlass`.

### 8.2 Per-library install requirements

| Provider | Extra dependency | How to get it |
|---|---|---|
| cuBLAS, cuFFT | in the CUDA toolkit | nothing |
| cuBLASLt | in the CUDA toolkit | nothing |
| cuDNN | libcudnn 9 | `apt install libcudnn9-cuda-12 libcudnn9-dev-cuda-12` |
| CUTLASS | header-only, **CUDA 12+** | fetched by CMake `FetchContent` (v3.5.1); no install |
| cuSPARSE | in the CUDA toolkit | nothing |

The CUTLASS kernel arch defaults to
`sm_80` SASS + `compute_80` PTX (runs on all Ampere/Ada, JITs for Hopper);
override with `CUDA_ARCH=<cc>` (e.g. `CUDA_ARCH=89`).

### 8.3 Useful CLI flags

| Flag | Effect |
|---|---|
| `-m <module>/<Main>` | run a class from a named module |
| `--params "a b c"` | pass program arguments |
| `--jvm="-Xmx8g ..."` | pass JVM flags |
| `--enableProfiler console` | print per-task timings (incl. library tasks) |
| `--printKernel` | dump generated kernels for JIT tasks |
| `--threadInfo` | print the launch grid per task |
| `--debug` | verbose runtime logging |
| `--devices` | list available devices |

Run a single unit-test suite:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cutlass.TestCutlass
```

---

## 9. Write your own provider

Adding a library needs **no core-runtime changes** — it is a module pair
discovered via `java.util.ServiceLoader`. Four steps (mirror `tornado-cudnn`):

**1. Factory** — build a `LibraryTaskDescriptor`:

```java
public final class MyLib {
    public static final String LIBRARY_NAME = "vendor/mylib";

    public static LibraryTaskDescriptor myOp(int n, FloatArray in, FloatArray out) {
        Access[] access = { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY };
        return new LibraryTaskDescriptor()
            .withLibrary(LIBRARY_NAME)
            .withFunction("myOp")
            .withParameters(new Object[] { n, in, out })
            .withAccess(access);
    }
}
```

**2. Provider** — implement `TornadoLibraryProvider`:

```java
public final class MyProvider implements TornadoLibraryProvider {
    public String libraryName() { return MyLib.LIBRARY_NAME; }

    public boolean canHandle(TornadoXPUDevice device) {
        return device instanceof TornadoNativeStreamSupport;   // CUDA only
    }

    public LibraryContext createContext(TornadoXPUDevice device, long planId) {
        long stream = ((TornadoNativeStreamSupport) device).getNativeStream(planId);
        return new MyContext(MyNativeLib.createHandle(), stream);  // bind stream here
    }

    public void prepare(LibraryTaskDescriptor d, LibraryContext ctx) {
        // idempotent: create/cache per-shape plans + workspace BEFORE capture
    }

    public void dispatch(String fn, LibraryInvocation call) {
        long dIn  = call.getDevicePointer(1);      // reference args → device pointers
        long dOut = call.getDevicePointer(2);
        int  n    = (int) call.getArg(0);          // scalars → boxed values
        MyNativeLib.myOp(((MyContext) call.getContext()).handle, n, dIn, dOut, stream);
    }

    public void destroyContext(LibraryContext ctx) { /* free plans, workspace, handle */ }
}
```

**3. Register** in `module-info.java`:

```java
open module tornado.mylib {
    requires transitive tornado.api;
    requires tornado.runtime;
    exports vendor.mylib;
    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider
        with vendor.mylib.provider.MyProvider;
}
```
…and add the same class name to
`src/main/resources/META-INF/services/uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`.

**4. Native binding** — bind the library's calls with `java.lang.foreign`
directly in `MyNativeLib.java` (see `CuBlasNativeLib`): a `SymbolLookup`
resolved at class-init time and one downcall handle per entry point, no
separate module or build step. Only add a `tornado-drivers/mylib-jni` CMake
module (see `cudnn-jni` for a host library, `cutlass-jni` for one that
compiles device code) under the `cuda-backend` profile — plus wiring in the
root `pom.xml`, the `tornado-drivers` profile, `tornado-assembly`
(`assembly.xml` + `pom.xml`), and `tornado.py` (`--add-modules`) — when the
library needs actual compiled C/C++, not just a symbol table.

Key SPI types (in `tornado-runtime/.../runtime/library/spi/`):

- **`TornadoLibraryProvider`** — the interface above.
- **`LibraryContext`** — marker; your per-`(device, plan)` state (handle, stream,
  plan cache, workspace).
- **`LibraryInvocation`** — per-call payload: `getArg(i)`, `getDevicePointer(i)`,
  `isReference(i)`, `getContext()`, `getTuning()`.
- **`TornadoNativeStreamSupport`** — `getNativeStream(planId)` / `getNativeContext(planId)`,
  implemented by the CUDA backend device.

---

## 10. Reference: layout, data types, alignment

- **Layout.** CUTLASS and cuSPARSE are **row-major** (match TornadoVM arrays
  directly). **cuBLAS is column-major** — pass the transpose op or swap operands.
- **Data types.** `FloatArray` (FP32), `HalfFloatArray` (FP16, via
  `new HalfFloat(float)` / `.get(i).getFloat32()`), `DoubleArray` (FP64, cuFFT
  Z2Z). Device pointer = buffer base **+ 24-byte header**.
- **Alignment.** Guaranteed 8-byte. The FP16 CUTLASS kernels therefore need
  `k, n` multiples of 4.
- **`beta != 0`.** The output operand becomes `READ_WRITE`; transfer its initial
  values to device if they matter.
- **Batching.** `withBatch(...)` is not supported for library tasks; use a
  library's own batched entry point (e.g. `cublasSgemmStridedBatched`).

---

## 11. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Test reports `UNSUPPORTED` | Default device is not CUDA, or the native `.so` / vendor library is missing. Build `make BACKEND=cuda`; install the library (§8.2). |
| `UnsatisfiedLinkError: libtornado-<x>` | Native module was skipped at build time (library not found). Set the corresponding `*_ROOT` and rebuild. |
| CUTLASS FP16 rejects a shape | `k` or `n` not a multiple of 4 (8-byte alignment). Pad, or use `cutlassSgemm` (FP32, unconstrained). |
| Wrong result from cuBLAS | Column-major mismatch — transpose (SGEMV) or swap operands (SGEMM). |
| `CUDA_ERROR_LAUNCH_FAILED` after a tensor-core call | The kernel was built for the wrong SM. Rebuild with `CUDA_ARCH=<your cc>`. |

---

## See also

- `docs/source/hybrid-api.rst` — architecture reference (SPI internals).
- Per-provider READMEs: `tornado-cublas/`, `tornado-cufft/`, `tornado-cudnn/`,
  `tornado-cutlass/`, `tornado-cusparse/`.
- Unit tests double as worked examples:
  `tornado-unittests/.../unittests/{cublas,cufft,cudnn,cusparse,cutlass}/`.
