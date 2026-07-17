# TornadoVM Hybrid API — Complete Guide

The **hybrid API** lets a single TornadoVM `TaskGraph` mix JIT-compiled Java
tasks (`@Parallel` / `KernelContext`) with calls into vendor-optimized native
GPU libraries — **cuBLAS, cuBLASLt, cuFFT, cuDNN, CUTLASS, and cuTENSOR**.
A native call becomes a **library task**: it shares TornadoVM-managed device
buffers with the surrounding kernels, runs on the same CUDA stream, and is
captured into CUDA Graphs — so data produced by a JIT kernel feeds a library
call (and vice-versa) with **no extra copies and no manual memory management**.

> Requires the **CUDA backend** (`make BACKEND=cuda`) and an NVIDIA GPU.
> Library tasks are silently reported as `UNSUPPORTED` on OpenCL/PTX/CUDA/Metal.

---

## Table of contents

1. [Quick start](#1-quick-start)
2. [Core concepts](#2-core-concepts)
3. [Provider catalog](#3-provider-catalog) — cuBLAS · cuBLASLt · cuFFT · cuDNN · CUTLASS · cuTENSOR
4. [Composition patterns](#4-composition-patterns)
5. [CUDA Graphs](#5-cuda-graphs)
6. [Profiling](#6-profiling)
7. [Build, install & flags](#7-build-install--flags)
8. [Write your own provider](#8-write-your-own-provider)
9. [Reference: layout, data types, alignment](#9-reference-layout-data-types-alignment)
10. [Troubleshooting](#10-troubleshooting)

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
arguments. There are 18 overloads (`libraryTask` with 1–18 typed arguments).

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
`cublasSetStream`, `cudnnSetStream`; cuTENSOR/CUTLASS take the stream per call).
Everything — JIT kernels, transfers, library calls — runs **in order on one
stream**, with no host synchronization.

### 2.4 The `prepare()` hook (capture safety)

Libraries that create per-shape plans or workspaces (cuDNN, cuTENSOR, CUTLASS,
cuFFT) do that allocation in a `prepare()` hook that the interpreter calls in
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
| `nvidia/cutlass` | `tornado-cutlass` |
| `nvidia/cutensor` | `tornado-cutensor` |

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
| `cublasGemmExFP16(...)` | FP16 inputs, tensor-core GEMM |
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
kernel instead of GEMM + separate bias/activation passes. Tuned via an opaque
`withTuning` descriptor and a cached plan + 32 MiB workspace.

```java
taskGraph.libraryTask("mlp", CuBlasLt::matmulBiasGelu, m, n, k, aFP16, bFP16, bias, dFP16);
```

`BenchmarkLtFusedMlp`: fusion is **1.1–1.7×** the unfused path (biggest win in
the launch-bound regime).

### 3.3 cuFFT — fast Fourier transforms (`nvidia/cufft`)

Complex and real transforms with a per-`(n, batch)` cached plan.

| Factory | Transform |
|---|---|
| `cufftC2C(input, output, n, batch, direction)` | 1D complex→complex fwd/inv |
| `cufftR2C` / `cufftC2R` | real↔complex (Hermitian, `n ↔ n/2+1`) |
| `cufftZ2Z` | FP64 complex→complex |
| `cufft2D_C2C` | 2D complex→complex |

```java
// fft → JIT low-pass filter → ifft → JIT normalize, all on-device
new TaskGraph("filter")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, signal)
    .libraryTask("fwd",  CuFft::cufftC2C, signal, freq, n, 1, CuFft.FORWARD)
    .task("lowpass",     Filters::lowPass, freq, cutoff)
    .libraryTask("inv",  CuFft::cufftC2C, freq, out, n, 1, CuFft.INVERSE)
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

FP32 SIMT and FP16 tensor-core GEMM plus **fused GEMM + bias + ReLU/GELU**
epilogues. **Row-major** (`C = alpha·A·B + beta·C`) — no cuBLAS transpose dance.

| Factory | Operation |
|---|---|
| `cutlassSgemm(m, n, k, alpha, A, B, beta, C)` | FP32 SIMT GEMM |
| `cutlassHgemm(m, n, k, alpha, A, B, beta, D)` | FP16 tensor-core GEMM, FP32 accumulate |
| `cutlassGemmBiasRelu(m, n, k, A, B, bias, D)` | fused `relu(A·B + bias)` |
| `cutlassGemmBiasGelu(m, n, k, A, B, bias, D)` | fused `gelu(A·B + bias)` |

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

`BenchmarkCutlassGemm` (RTX 4090, 1024): FP16 tensor-core **39 TFLOP/s = 7.7×**
the tiled `KernelContext` kernel; fused epilogue **1.28×** the unfused path.

### 3.6 cuTENSOR — tensor contractions / einsum (`nvidia/cutensor`)

FP32 **tensor contractions** — the einsum generalization of matmul. Row-major.

| Factory | Contraction |
|---|---|
| `cutensorContraction(m, n, k, A, B, C)` | `C[m,n] = Σ_k A[m,k]·B[k,n]` (matmul) |
| `cutensorContraction2(i, j, k, l, A, B, C)` | `C[i,j] = Σ_{k,l} A[i,k,l]·B[k,l,j]` |

```java
// Two-mode contraction — the thing cuBLAS cannot express in one call
taskGraph.libraryTask("contract", Cutensor::cutensorContraction2, i, j, k, l, a, b, c);
```

`BenchmarkCutensor` (RTX 4090, 1024): matmul **21 TFLOP/s = 5.4×** the JIT
kernel; two-mode contraction **2.3×**.

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

## 6. Profiling

```bash
tornado --enableProfiler console -m <module>/<MainClass>
```

Library tasks report `TASK_KERNEL_TIME` (host-timed, bounded by stream markers)
alongside `BACKEND`, `DEVICE`, and `METHOD`, together with regular tasks.

To confirm native and JIT kernels share one stream, profile with **nsys**:

```bash
nsys profile --trace=cuda -o run tornado -m <module>/<MainClass>
nsys stats --report cuda_gpu_trace run.nsys-rep   # cutlass/cutensor kernels + JIT kernels, same Strm
```

---

## 7. Build, install & flags

### 7.1 Build

```bash
make BACKEND=cuda        # activates the cuda-backend Maven profile
```

The Java modules (`tornado-cublas`, `tornado-cufft`, `tornado-cudnn`,
`tornado-cutlass`, `tornado-cutensor`) always compile. Their native `*-jni`
counterparts build only under the `cuda-backend` profile, and each is
**self-guarding**: if its library/toolkit is missing the native `.so` is skipped
(the build still succeeds) and that provider reports `UNSUPPORTED` at runtime.

The launcher adds the provider modules to `--add-modules` automatically when the
CUDA backend is present:
`tornado.cublas, tornado.cufft, tornado.cudnn, tornado.cutlass, tornado.cutensor`.

### 7.2 Per-library install requirements

| Provider | Extra dependency | How to get it |
|---|---|---|
| cuBLAS, cuFFT | in the CUDA toolkit | nothing |
| cuBLASLt | in the CUDA toolkit | nothing |
| cuDNN | libcudnn 9 | `apt install libcudnn9-cuda-12 libcudnn9-dev-cuda-12` |
| CUTLASS | header-only, **CUDA 12+** | fetched by CMake `FetchContent` (v3.5.1); no install |
| cuTENSOR | libcutensor, **CUDA 12+** | `pip install cutensor-cu12` (or NVIDIA download), then `export CUTENSOR_ROOT=<dir>` |

`CUTENSOR_ROOT` (or `~/.local/cutensor`, `/usr/local/cutensor`) points at a
directory with `include/` and `lib/`. The CUTLASS kernel arch defaults to
`sm_80` SASS + `compute_80` PTX (runs on all Ampere/Ada, JITs for Hopper);
override with `CUDA_ARCH=<cc>` (e.g. `CUDA_ARCH=89`).

### 7.3 Useful CLI flags

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

## 8. Write your own provider

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

**4. Native binding** — a `tornado-drivers/mylib-jni` CMake module (see
`cudnn-jni` for a host library, `cutlass-jni` for one that compiles device code)
under the `cuda-backend` profile, plus wiring in the root `pom.xml`, the
`tornado-drivers` profile, `tornado-assembly` (`assembly.xml` + `pom.xml`), and
`tornado.py` (`--add-modules`).

Key SPI types (in `tornado-runtime/.../runtime/library/spi/`):

- **`TornadoLibraryProvider`** — the interface above.
- **`LibraryContext`** — marker; your per-`(device, plan)` state (handle, stream,
  plan cache, workspace).
- **`LibraryInvocation`** — per-call payload: `getArg(i)`, `getDevicePointer(i)`,
  `isReference(i)`, `getContext()`, `getTuning()`.
- **`TornadoNativeStreamSupport`** — `getNativeStream(planId)` / `getNativeContext(planId)`,
  implemented by the CUDA backend device.

---

## 9. Reference: layout, data types, alignment

- **Layout.** CUTLASS and cuTENSOR are **row-major** (match TornadoVM arrays
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

## 10. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Test reports `UNSUPPORTED` | Default device is not CUDA, or the native `.so` / vendor library is missing. Build `make BACKEND=cuda`; install the library (§7.2). |
| `UnsatisfiedLinkError: libtornado-<x>` | Native module was skipped at build time (library not found). Set the corresponding `*_ROOT` and rebuild. |
| cuTENSOR `SIGSEGV` in `cutensorCreatePlan` | Native stack overflow — plan selection needs a large stack. The JNI already runs it on a 64 MB pthread; if you hit it elsewhere, raise `ulimit -s`. |
| CUTLASS FP16 rejects a shape | `k` or `n` not a multiple of 4 (8-byte alignment). Pad, or use `cutlassSgemm` (FP32, unconstrained). |
| Wrong result from cuBLAS | Column-major mismatch — transpose (SGEMV) or swap operands (SGEMM). |
| `CUDA_ERROR_LAUNCH_FAILED` after a tensor-core call | The kernel was built for the wrong SM. Rebuild with `CUDA_ARCH=<your cc>`. |

---

## See also

- `docs/source/hybrid-api.rst` — architecture reference (SPI internals).
- `HYBRID_API_BRANCHES.md` — which branch contains which provider.
- Per-provider READMEs: `tornado-cublas/`, `tornado-cudnn/`, `tornado-cutlass/`,
  `tornado-cutensor/`.
- Unit tests double as worked examples:
  `tornado-unittests/.../unittests/{cublas,cufft,cudnn,cutlass,cutensor}/`.
