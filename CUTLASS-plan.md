# CUTLASS provider for the TornadoVM hybrid API (`hybrid-cutlass`)

## Context

The hybrid API (branch `hybrid`, tag `hybrid-libs-v1`) lets native NVIDIA library calls
ride the standard TornadoVM task-graph pipeline as **library tasks**: a ServiceLoader SPI
(`TornadoLibraryProvider`) with providers for cuBLAS, cuBLASLt, cuFFT, cuDNN. Library
tasks reuse TornadoVM device buffers (`XPUBuffer.toBuffer() + ARRAY_HEADER(24)`), bind to
the Tornado CUstream via `TornadoNativeStreamSupport`, run interleaved with JIT-compiled
tasks in one graph, and are CUDA-graph capturable via the `prepare()` pre-capture hook.

Goal: add **NVIDIA CUTLASS** as the next provider — `"nvidia/cutlass"` — as a
**library-task module pair** (user-confirmed; deviates from the tracker's earlier
"NVRTC codegen track" note — update the tracker). Op scope (user-confirmed):
**FP32 SIMT GEMM + FP16 TensorCore GEMM + fused GEMM+bias+ReLU/GELU epilogue**.
CUTLASS's differentiator vs cuBLAS: open template kernels with native row-major layouts
and composable fused epilogues. Verification includes `nsys` proof that CUTLASS kernels
run interleaved with Tornado JIT kernels on the same stream.

Environment verified: nvcc 12.6 (/usr/local/cuda), RTX 4090 (sm_89), cmake 3.22.1,
nsys 2024.5.

**Base (user-directed):** latest `origin/master` (beehive-lab, `9471c654d`, v5.0.1-jdk21-dev)
— the hybrid API stack (SPI, tornado-cublas/cufft/cudnn, jni modules, `HYBRID_API_BRANCHES.md`)
is already merged upstream, so master is the correct base; `hybrid`-branch drift is
negligible (4 files, minor). Plus **PR #910** (`origin/pr-910`, fetched: 2 commits —
NVRTC diagnostics fix in cuda-jni/CUDACodeCache, orthogonal to this work, master==develop
so it merges clean).

## Branch

```
git checkout -b hybrid-cutlass origin/master     # 9471c654d
git merge origin/pr-910                          # NVRTC diagnostics fix (2 commits)
```
First commit: copy this plan into the repo as `CUTLASS-plan.md` (user asked plan stored
in a file; repo-root guide precedent: METAL-guide.md). Push to `mp` remote when green.
Commits: one-line messages, no attribution (per feedback_commit_messages).

## Architecture (mirror of cuDNN module pair)

### 1. `tornado-drivers/cutlass-jni` (native, CMake, cuda-backend profile)

- `pom.xml`: copy `tornado-drivers/cudnn-jni/pom.xml` (cmake-maven-plugin, `:jar:libs`
  classifier). Add `<module>cutlass-jni</module>` to the `cuda-backend` profile in
  `tornado-drivers/pom.xml` (lines ~70–82).
- `src/main/cpp/CMakeLists.txt`: based on cudnn-jni's, plus:
  - `enable_language(CUDA)`; sources are `.cu` compiled by **nvcc** (CUTLASS is device
    template code — unlike the other JNI libs which are host-only C++).
  - Prefer `/usr/local/cuda` toolkit (`set(CMAKE_CUDA_COMPILER /usr/local/cuda/bin/nvcc)`
    hint first, fall back to discovery) — same toolkit-mismatch guard as cuda-jni.
  - `set(CMAKE_CUDA_ARCHITECTURES 89)` with fallback `native`; `-O3 --expt-relaxed-constexpr`.
  - FetchContent CUTLASS headers, populate-only (same pattern as cudnn-frontend):
    `GIT_REPOSITORY https://github.com/NVIDIA/cutlass.git GIT_TAG v3.5.1 GIT_SHALLOW TRUE`;
    include `${cutlass_SOURCE_DIR}/include` + `${cutlass_SOURCE_DIR}/tools/util/include`.
    No add_subdirectory.
  - Link only `cudart` (CUTLASS is header-only).
- `src/main/cpp/source/tornado-cutlass.cu`: JNI entry points instantiating the CUTLASS
  **2.x device API** (`cutlass::gemm::device::GemmUniversal`) — sm90 CollectiveBuilder
  (3.x) doesn't target Ada; 2.x kernels with `arch::Sm80` tag run fine on sm_89 binaries.
  Instantiations (all **row-major** A,B,D — no cuBLAS column-major transposition dance):
  1. `sgemm_f32_simt`: float, `OpClassSimt`, alignment 1. Correctness baseline.
  2. `hgemm_f16_tensorop`: `half_t`, `OpClassTensorOp`, `Sm80`,
     **kAlignmentA = kAlignmentB = 4** (see alignment constraint below).
  3. `gemm_bias_relu_f16` / `gemm_bias_gelu_f16`: same tensorop config with epilogue
     `LinearCombinationRelu` / `LinearCombinationGELU(taylor)`; bias = C operand with
     `ldc = 0` (row-broadcast of a length-n bias vector), beta = 1.
  - Each JNI fn: build `GemmUniversal::Arguments`, `op.can_implement(args)` → status,
    `op.initialize(args, workspace, stream)`, `op.run(stream)`. `Gemm::get_workspace_size`
    exposed as a separate JNI fn for the `prepare()` hook (split-K = 1 → usually 0, but
    keep the capture-safe workspace path anyway).
  - Status decode helper (cutlass::Status → string) mirroring `decodeStatus` in
    `CuDnnNativeLib`.

**Alignment constraint (must be enforced):** Tornado device pointers are
`cuMemAlloc-base (≥256B aligned) + 24-byte header` → guaranteed alignment is only
**8 bytes**. Default CUTLASS tensorop configs use 128-bit (8-half) alignment and would
reject or misalign. Hence kAlignment 4 for half (8B loads) and alignment-1 SIMT for
float. Runtime check in dispatch: `ptr % 8 == 0` else descriptive error. Also implies
`k` and `n` must be multiples of 4 for the FP16 kernels — validate in the Java factory.

### 2. `tornado-cutlass` (Java module, always built)

Mirror `tornado-cudnn` file-for-file:
- `Cutlass.java`: `LIBRARY_NAME = "nvidia/cutlass"`; static `LibraryTaskDescriptor`
  factories (SAMs compatible with `LibraryTaskN`):
  - `cutlassSgemm(int m, int n, int k, float alpha, FloatArray a, FloatArray b, float beta, FloatArray c)`
    — c is READ_WRITE when beta != 0, WRITE_ONLY otherwise (beta lesson from cuBLAS).
  - `cutlassHgemm(int m, int n, int k, float alpha, HalfFloatArray a, HalfFloatArray b, float beta, HalfFloatArray d)`
  - `cutlassGemmBiasRelu(int m, int n, int k, HalfFloatArray a, HalfFloatArray b, HalfFloatArray bias, HalfFloatArray d)`
  - `cutlassGemmBiasGelu(...)` same shape.
  - Factories validate FP16 shape divisibility (k%4, n%4) early with clear messages.
- `provider/CutlassLibraryProvider.java`: private `CutlassContext implements LibraryContext`
  holding `long stream` (from `TornadoNativeStreamSupport.getNativeStream(planId)` in
  `createContext` — CUTLASS has no handle; the stream is passed per launch) + grow-only
  workspace (`allocateDeviceMemory`/`freeDeviceMemory` natives like cuDNN).
  `prepare()`: query workspace size per function+shape, grow workspace (idempotent,
  pre-capture). `dispatch()`: switch on function name, scalars via `getArg(i)`,
  device pointers via `getDevicePointer(i)`, alignment check, call native.
- `provider/CutlassNativeLib.java`: `System.loadLibrary("tornado-cutlass")` guarded
  load + natives + `checkStatus`.
- `module-info.java`: `open module tornado.cutlass`, requires tornado.api/runtime,
  `provides TornadoLibraryProvider with CutlassLibraryProvider`.
- `META-INF/services/uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider`.
- `tests/BenchmarkCutlassGemm.java` (main-runner, documented example — see Benchmarks).
- `README.md` (usage, row-major note, alignment/shape constraints, flags).

### 3. Wiring (one line each, additive after the cudnn lines)

- Root `pom.xml`: `<module>tornado-cutlass</module>` (~line 679).
- `tornado-assembly/pom.xml` + `assembly.xml`: tornado-cutlass jar (share/java/tornado)
  and `cutlass-jni:jar:libs` unpack into `lib/`.
- `tornado-assembly/src/bin/tornado.py`: `__CUTLASS_MODULE__ = "tornado.cutlass"`
  (~line 76) appended in both cuda-backend `--add-modules` branches (~lines 1304, 1321).
- `tornado-assembly/src/bin/tornado-test`: `TestEntry("uk.ac.manchester.tornado.unittests.cutlass.TestCutlass")` (~line 150).
- `HYBRID_API_BRANCHES.md`: rewrite the `hybrid-cutlass` row — library-task track
  (module pair), note the codegen/CuTe-under-NVRTC idea moved to "possible later track".

### 4. Tests (`tornado-unittests/.../unittests/cutlass/TestCutlass.java`)

`@Before` guard exactly like `TestCuDnn` (CUDA backend + `System.loadLibrary("tornado-cutlass")`).
1. `testSgemm` — FP32 vs Java reference (exact-ish, 1e-4 rel).
2. `testSgemmBeta` — beta=1 accumulate path (READ_WRITE access).
3. `testHgemm` — FP16 tensorop vs Java ref (FP16 tolerance).
4. `testGemmBiasRelu` / `testGemmBiasGelu` — vs unfused Java ref with abs slack ~0.05
   (FP16 pre-activation rounding, same as cuBLASLt tests).
5. `testSgemmWithJitPreAndPost` — **the mixed-graph requirement**: JIT `mutateData`
   task → `libraryTask(Cutlass::cutlassSgemm)` → JIT post task, 10 iterations,
   EVERY_EXECUTION transfers (pattern: `TestCuBlasSgemvWithTornadoVMTasksPOST`).
6. `testGemmWithCudaGraph` — same graph under `plan.withCUDAGraph()`, 5 iterations
   (exercises `prepare()` pre-capture workspace path).
7. `testHgemmBadAlignmentShape` — expects descriptive exception for k%4 != 0.

### 5. Benchmarks (`tornado-cutlass/tests/BenchmarkCutlassGemm.java`)

Same harness style as `BenchmarkSgemm` / `BenchmarkLtFusedMlp`
(per feedback_hybrid_testing: WoW vs Java + KernelContext):
- GEMM: naive `@Parallel` JIT vs tiled KernelContext (TS=32) vs CUTLASS FP32 vs CUTLASS
  FP16 vs cuBLAS FP16 (reference ceiling) at 1024/2048/4096.
- Fused MLP: CUTLASS gemm+bias+GELU vs unfused (CUTLASS gemm + JIT bias/gelu task) vs
  cuBLASLt fused epilogue — the flagship comparison.

## Build & verify loop

```
make BACKEND=cuda                       # only permitted build command (feedback_build_commands)
tornado-test -V uk.ac.manchester.tornado.unittests.cutlass.TestCutlass
# regression gate (no cross-suite breakage):
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlas
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlasLt
tornado-test -V uk.ac.manchester.tornado.unittests.cufft.TestCuFft
tornado-test -V uk.ac.manchester.tornado.unittests.cudnn.TestCuDnn
tornado --jvm="-Xmx8g" -m tornado.cutlass/uk.ac.manchester.tornado.cutlass.tests.BenchmarkCutlassGemm
```

### nsys verification (user-requested proof of what Tornado runs)

```
nsys profile -o <scratchpad>/cutlass-mixed --force-overwrite=true \
    tornado -m tornado.cutlass/...BenchmarkCutlassGemm   # or the mixed-graph runner
nsys stats --report cuda_gpu_kern_sum <scratchpad>/cutlass-mixed.nsys-rep
nsys stats --report cuda_gpu_trace    <scratchpad>/cutlass-mixed.nsys-rep | head -50
```
Expect: `cutlass::Kernel<...>` (or `Kernel2<...>`) entries interleaved with Tornado JIT
kernel names (e.g. `mutateData`), all on the **same stream**, correct order
pre → cutlass → post. Repeat under `withCUDAGraph` to confirm graph replay
(kernels attributed to graph launch).

## Risks / fallbacks

- **8-byte pointer alignment** is the main risk. If kAlignment-4 tensorop instantiation
  underperforms or `can_implement` rejects: fallback A = alignment-2; fallback B =
  document FP16 path needs shape multiples and keep SIMT FP32 as always-works path.
- CUTLASS v3.5.1 2.x-API deprecation warnings under nvcc 12.6: suppress with
  `-Wno-deprecated-declarations`; if v3.5.1 misbehaves, pin v3.4.1.
- nvcc compile time for 4 instantiations: expect ~1–3 min added to cutlass-jni build —
  acceptable, one-time per build.
- Bias via `ldc=0` broadcast: if `GemmUniversal` rejects zero stride, switch to
  `epilogue::thread::LinearCombinationBiasRelu`-style per-channel bias epilogue or
  `GemmUniversalWithBroadcast`.

## Execution order

0. `git checkout -b hybrid-cutlass origin/master && git merge origin/pr-910`; commit
   plan file as `CUTLASS-plan.md`.
1. Skeleton cutlass-jni (CMake + FetchContent + one sgemm) until `.so` builds.
2. tornado-cutlass Java module + wiring; `testSgemm` green end-to-end.
3. Mixed-graph + CUDA-graph tests green.
4. FP16 tensorop + fused epilogues + remaining tests.
5. Benchmarks + nsys verification.
6. Regression gate (4 existing suites) + tracker/README/docs update; commit per step, push.
