---
name: tornadovm-nvidia
description: NVIDIA-specific TornadoVM workflows on the CUDA backend — profile with Nsight Systems (nsys) and read NVTX ranges, use the hybrid library-task API (cuBLAS/cuBLASLt/cuDNN/cuFFT/cuSPARSE/cuTENSOR/CUTLASS/NCCL native calls from Java), print the generated CUDA-C kernel, and fuzz-test the CUDA-C backend with tornado-fuzz. Use for anything about nsys/Nsight, hybrid/library tasks, cuBLAS & friends, inspecting CUDA kernels, or fuzzing. Requires an NVIDIA GPU + `make BACKEND=cuda`. For generic build/test/commit basics see the `tornadovm` skill.
---

# TornadoVM on NVIDIA (CUDA backend)

All of this targets the **CUDA-C backend**: build with `make BACKEND=cuda` (which also builds the `*-jni` library modules under the `cuda-backend` Maven profile), then `source setvars.sh`. Repo-root refs: `HYBRID_API_GUIDE.md`, `NVIDIA_ROADMAP.md`, and `docs/source/hybrid-api.rst`.

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
| NCCL | `tornado-nccl` | standalone `TornadoNccl` API | multi-GPU collectives |

**Key patterns:**
- **CUDA graphs:** library tasks are graph-capturable. Providers that allocate device workspace (cuFFT/cuDNN/cuSPARSE plans, cuBLAS handles) do it in the SPI `prepare(descriptor, context)` pre-compile hook (idempotent) *before* capture — never allocate mid-capture. Wrap with `withCUDAGraph`.
- **Cross-graph device residency:** `persistOnDevice` / `consumeFromDevice` keep buffers on the GPU between graphs (avoid round-trips).
- **Scalar outputs** (e.g. `cublas dot`) use `CUBLAS_POINTER_MODE_DEVICE` with a 1-element TornadoVM array to stay capture-safe.
- **Every hybrid feature ships with a test** (validated vs sequential Java) and perf features with a **WoW benchmark** vs both Java and the best KernelContext kernel (patterns `BenchmarkSgemm`, `MatrixVectorRowMajorWithCuBlas`, `BenchmarkSdpa`). Tests: `tornado-unittests/.../unittests/{cublas,cudnn,cufft,cusparse,nvtx}/...`; benchmarks/examples in `tornado-examples`.

Build note: library JNI modules link against the CUDA toolkit at `/usr/local/cuda` (prefer it over any older apt CUDA to avoid NVRTC-version mismatch); each JNI CMake is guarded — a missing library skips that provider.

## 3. Print the generated CUDA-C kernel

To see the CUDA-C that the JIT emits for a `task(...)`:

```bash
# via the test runner:
tornado-test uk.ac.manchester.tornado.unittests.arrays.TestArrays#testAdd -V -pk
# via any app (JVM property):
tornado -m <module>/<MainClass> --jvm="-Dtornado.printKernel=true"
# dump kernels to files instead of stdout:
tornado -m <module>/<MainClass> --jvm="-Dtornado.printKernel=true -Dtornado.print.kernel.dir=/tmp/kernels"
```

Flags: `-pk`/`--printKernel` (test runner) ↔ `-Dtornado.printKernel=true` (`TornadoOptions.PRINT_KERNEL_SOURCE`); `-Dtornado.print.kernel.dir=<dir>` writes each kernel to a file; add `--printBytecodes`/`-pbc` to also see the TornadoVM bytecode. Library tasks emit no kernel (they call native code) — inspect those with nsys/NVTX instead.

## 4. Fuzz-testing the CUDA-C backend (`tornado-fuzz`)

`tornado-fuzz` (module `tornado.fuzz`) is a differential fuzzer for the **CUDA-C backend only**. Oracle: a kernel is a static Java method → run it as plain Java on the host (golden) and via the CUDA backend, then compare (int/bitwise exact; float rel+abs 1e-3; NaN==NaN); plus a crash/exception oracle.

```bash
source setvars.sh
# Phase 1 — fixed KernelContext templates (elementwise/math/local-mem-reduce/atomic):
tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
        --params "seed=1 count=300 phase=1 outDir=/tmp/fuzz"

# Phase 2 — generative: random int-expression kernels compiled in-process, run CUDA vs JVM reference.
# The generated class dir MUST be on -cp AND passed via -Dtornado.fuzz.genDir (system classloader
# requirement — ASM reads bytecode via getSystemClassLoader):
tornado -cp /tmp/gen -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \
        --jvm="-Dtornado.fuzz.genDir=/tmp/gen" \
        --params "seed=1 count=200 phase=2 outDir=/tmp/fuzz2"
```

Each finding writes a bundle under `<outDir>/findings/<kind>-<seed>/`: `config.json`, `kernel.java`, `diff.txt`, `stacktrace`, and a standalone `Repro_*.java` (JUnit extending `TornadoTestBase` with the failing inputs + golden embedded as literals). Confirmed integer-codegen divergences it has surfaced (regression suite `tornado-unittests/.../unittests/fuzz/`): `min/max(NaN,x)` semantics, `a<<31`, `INT_MIN*b`, sign-bit masks, signed `>>` emitted as logical, and an `INT_MIN` division that crashes the CUDA reassociation phase.

When adding an expression shape or template, keep the JVM `reference` method the single source of truth (identical loop to the emitted kernel), and use a **unique generated class name per compile** (the system loader caches by name).
