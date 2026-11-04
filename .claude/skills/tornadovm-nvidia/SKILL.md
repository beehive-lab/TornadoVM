---
name: tornadovm-nvidia
description: NVIDIA-specific TornadoVM workflows on the CUDA backend — profile with Nsight Systems (nsys) and read NVTX ranges, use the hybrid library-task API (cuBLAS/cuBLASLt/cuDNN/cuFFT/cuSPARSE/cuTENSOR/CUTLASS/NCCL native calls from Java), print the generated CUDA-C kernel, and fuzz-test the code generator (TC, on CUDA and OpenCL) with tornado-fuzz. Use for anything about nsys/Nsight, hybrid/library tasks, cuBLAS & friends, inspecting CUDA kernels, or fuzzing. Requires an NVIDIA GPU + `make BACKEND=cuda`. For generic build/test/commit basics see the `tornadovm` skill.
---

# TornadoVM on NVIDIA (CUDA backend)

All of this targets the **CUDA-C backend**: build with `make BACKEND=cuda` (the CUDA backend itself and most library providers — cuBLAS/cuBLASLt/cuFFT/cuSPARSE — bind through `java.lang.foreign`, no native module to build; cuDNN's SDPA shim and CUTLASS still build a native `*-jni` module under the `cuda-backend` Maven profile), then `source setvars.sh`. Refs: `tornado-native-libs/HYBRID_API_GUIDE.md` and `docs/source/hybrid-api.rst`.

## 1. Profiling with Nsight Systems (nsys)

TornadoVM labels its work with **NVTX ranges** (always on, no profiler flag): JIT kernels, host↔device transfers, *and* hybrid library calls. Library tasks appear as ranges like `:nvidia/cublas/cublasSgemm`. So an `nsys` timeline reads like the TornadoVM task graph.

```bash
source setvars.sh
nsys profile --trace=cuda,nvtx -o run tornado -m <module>/<MainClass>   # → run.nsys-rep
# also works with the test runner / examples, e.g.:
nsys profile --trace=cuda,nvtx -o streams tornado-test uk.ac.manchester.tornado.unittests.streams.TestStreamsPerformance
```

Open `run.nsys-rep` in the Nsight Systems GUI. `--trace=cuda` alone gives kernel/API timing; add `nvtx` to get the TornadoVM labels. See `TestNvtx` (`tornado-unittests/.../unittests/nvtx/TestNvtx.java`) for the transparency contract (balanced push/pop across JIT / repeated / mixed / CUDA-graph runs).

**Correctness profiling:** `compute-sanitizer` catches races/OOB/uninit that timing can't. Use the toolkit build, not the distro one, and trace the java child process:
```bash
/usr/local/cuda/bin/compute-sanitizer --tool racecheck --target-processes all tornado -m <module>/<MainClass>
```
(`racecheck`, `memcheck`, `initcheck`, `synccheck`.)

## 2. Hybrid library-task API (NVIDIA CUDA-X from Java)

A **library task** hands a step of the task graph to a native NVIDIA library instead of JIT-generating a kernel. It reuses the same TornadoVM device buffers and rides the normal LAUNCH pipeline (no unified-memory copies), dispatched through a ServiceLoader SPI (`TornadoLibraryProvider`).

```java
TaskGraph graph = new TaskGraph("g")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, matrix, x)
    .task("pre", MyKernels::prepare, x, tmp)            // ordinary JIT task
    .libraryTask("sgemv", CuBlas::cublasSgemv,          // native cuBLAS call
                 matrix, x, y, rows, cols)
    .task("post", MyKernels::finish, y)                 // ordinary JIT task
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
```

`libraryTask(String id, LibraryTaskN code, args...)` — `code` is a method reference to a provider function; JIT `task(...)` and `libraryTask(...)` mix freely in one graph. Arities go up to 20.

**Providers on `develop`** (module → provider id / entry class):

| Library | Module | Provider id | Examples |
|---|---|---|---|
| cuBLAS | `tornado-cublas` | `nvidia/cublas` | `cublasSgemm`, `cublasSgemv`, GemmEx FP16/TF32, stridedBatched |
| cuBLASLt | `tornado-cublas` | `nvidia/cublaslt` | fused BIAS / GELU_BIAS epilogues |
| cuDNN | `tornado-cudnn` | `nvidia/cudnn` | SDPA (`cudnnSdpaForward` FP16), conv2d, softmax, pooling, activations |
| cuFFT | `tornado-cufft` | `nvidia/cufft` | C2C/R2C/C2R/Z2Z 1D & 2D, batched |
| cuSPARSE | `tornado-cusparse` | `nvidia/cusparse` | CSR SpMV / SpMM (FP32) |
| cuTENSOR | `tornado-cutensor` | (see module) | FP32 tensor contractions (einsum) |
| CUTLASS | `tornado-cutlass` | `nvidia/cutlass` | FP32/FP16 GEMM + fused epilogues (NVRTC track) |
| NCCL | `tornado-nccl` | `nvidia/nccl` | multi-GPU collectives, send/recv, groups, multi-process (see the `tornadovm-nccl` skill) |

**Key patterns:**
- **CUDA graphs:** library tasks are graph-capturable. Providers that allocate device workspace (cuFFT/cuDNN/cuSPARSE plans, cuBLAS handles) do it in the SPI `prepare(descriptor, context)` pre-compile hook (idempotent) *before* capture — never allocate mid-capture. Wrap with `withCUDAGraph`.
- **Cross-graph device residency:** `persistOnDevice` / `consumeFromDevice` keep buffers on the GPU between graphs (avoid round-trips).
- **Scalar outputs** (e.g. `cublas dot`) use `CUBLAS_POINTER_MODE_DEVICE` with a 1-element TornadoVM array to stay capture-safe.
- **Every hybrid feature ships with a test** (validated vs sequential Java) and perf features with a **WoW benchmark** vs both Java and the best KernelContext kernel (patterns `BenchmarkSgemm`, `MatrixVectorRowMajorWithCuBlas`, `BenchmarkSdpa`). Tests: `tornado-unittests/.../unittests/{cublas,cudnn,cufft,cusparse,nvtx}/...`; benchmarks/examples in `tornado-examples`.

Build note: the FFM providers (cuBLAS/cuBLASLt/cuFFT/cuSPARSE) resolve their toolkit libraries at class-init time via `java.lang.foreign` — a missing library leaves the provider `UNSUPPORTED` at runtime rather than failing the build. `cudnn-jni`/`cutlass-jni` still link against the CUDA toolkit at `/usr/local/cuda` (prefer it over any older apt CUDA to avoid NVRTC-version mismatch); each JNI CMake is guarded the same way at build time — a missing library skips that provider.

## 3. Print the generated CUDA-C kernel

To see the CUDA-C that the JIT emits for a `task(...)`:

```bash
# via the test runner:
tornado-test uk.ac.manchester.tornado.unittests.arrays.TestArrays#testAdd -V -pk
# via any app (JVM property):
tornado -m <module>/<MainClass> --jvm="-Dtornado.print.kernel=true"
# dump kernels to files instead of stdout:
tornado -m <module>/<MainClass> --jvm="-Dtornado.print.kernel=true -Dtornado.print.kernel.dir=/tmp/kernels"
```

Flags: `-pk`/`--printKernel` (test runner) ↔ `-Dtornado.print.kernel=true` (`DebugFlags.PRINT_KERNEL`); `-Dtornado.print.kernel.dir=<dir>` writes each kernel to a file; add `--printBytecodes`/`-pbc` to also see the TornadoVM bytecode. Library tasks emit no kernel (they call native code) — inspect those with nsys/NVTX instead.

## 4. Fuzz-testing the code generator (`tornado-fuzz`)

`tornado-fuzz` (module `tornado.fuzz`) is a differential fuzzer for TC, the kernel compiler, on **CUDA and OpenCL** (`backend=cuda|opencl`, `device=N` within that backend). Oracle: a kernel is a static Java method, run as plain Java on the host (golden) and on the device, then compared (int/long exact; float rel+abs 1e-3; NaN==NaN); plus a crash/exception oracle.

```bash
source setvars.sh
cd /tmp/fuzz          # not a directory holding jars: the launcher puts "." on the module path
# Phase 3 (start here): typed programs - int/long/float/double, casts, if/else, loops (constant and
# data-dependent trip counts), private arrays, Integer/Long/Math helpers, computed static finals -
# each run as a KernelContext kernel AND an @Parallel kernel, under withStrictFloatingPoint().
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
        --params "seed=1 count=300 phase=3 backend=cuda outDir=/tmp/fuzz/cuda"
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
        --params "seed=1 count=300 phase=3 backend=opencl device=1 outDir=/tmp/fuzz/igpu"   # fp64=auto

# Replay a finding: recompiles its Kernel.java (edit it to bisect) on the saved inputs.
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
        --params "replay=/tmp/fuzz/cuda/phase3/miscompile_int-104 backend=cuda outDir=/tmp/fuzz/replay"

# Phase 1: fixed KernelContext templates (elementwise/math/local-mem-reduce/atomic).
# Phase 2: single-line int expressions with an expression shrinker.
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain --params "seed=1 count=300 phase=1 outDir=/tmp/fuzz1"
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain --params "seed=1 count=200 phase=2 outDir=/tmp/fuzz2"
```

Phase 3 outcomes (`<outDir>/phase3/summary.json`; one bundle per outcome with `Kernel.java`, `inputs.bin`, `finding.txt`): `miscompile:<outputs>` (marked `(float, transcendental)` when only float outputs differ in a program with sin/cos/exp/log - usually device ulp, not a bug), `internal:TC-E9xxx` (compiler bug), `crash`, `refused:TC-Exxxx` (TC declined: a shortcoming, e.g. TC-E4012 double precision on a device without it), `harness`. Failing programs are shrunk by dropping statements. Generated classes load through their own class loader (TC reads class files through the kernel's loader; `-Dtornado.fuzz.gen.dir` is no longer needed). javac targets the running JDK; `--enable-preview` only for a jdk21 SDK.

Findings fixed in TC from phase 3 (regressions in `tornado-unittests/.../unittests/fuzz/TestFuzzRegressions`): signed overflow (C UB) folded by the driver compilers (`Long.compare(0, x + x)`, `compareUnsigned`) - TC now prints int/long `+ - *` and negation with Java's wrap-around unless it marked them `nsw`; NaN to long/int (CUDA `__float2ll_rz`, `__double2int_rz` and NVIDIA OpenCL `convert_long_sat_rtz` give MIN_VALUE); `Math.rint` folded by `-cl-fast-relaxed-math`; NaN tests folded by fast math (bit tests on OpenCL); `MIN_VALUE / -1`; float `Math` done in double (FloatNarrowing; runs on devices without fp64); strict plans on OpenCL now drop fast math, add `-cl-fp32-correctly-rounded-divide-sqrt` and `#pragma OPENCL FP_CONTRACT OFF`. The older phase-2 regressions (`TestCudaCodegenRegressions`: `a<<31`, `INT_MIN*b`, sign-bit masks, signed `>>`, `INT_MIN` division) all pass with TC. Known device issue: Intel's OpenCL compiler treats every abs idiom as non-negative, so `max(abs(MIN_VALUE), 2)` is wrong there.

When adding a shape, keep the JVM `reference` method the single source of truth (the same text as the kernels), and use a **unique generated class name per compile**.
