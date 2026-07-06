# Hybrid API — Branch Tracker

Global tracker of which branch contains which part of the TornadoVM hybrid API
(native NVIDIA library tasks + codegen integrations). All branches are pushed to the
`mp` remote (github.com/mikepapadim/TornadoVM). Feature branches are cut from the
`hybrid` tip current at the time; merge order below.

| Branch | Contains | Key modules | Tests / benchmarks | Status |
|---|---|---|---|---|
| `hybrid` (base) | Library-task runtime: ServiceLoader SPI (`TornadoLibraryProvider`, `prepare()` hook*), device-buffer reuse via the standard LAUNCH pipeline, CUDA stream exposure, CUDA-Graph capture safety, profiler integration. **cuBLAS + cuBLASLt**: sgemv/sgemm (+beta), TF32 math mode, GemmEx FP16, strided-batched, workspace/`CuBlasOptions` tuning, Lt fused BIAS/GELU_BIAS epilogues with plan caching. **cuFFT (base)**: 1D C2C fwd/inv. | `tornado-cublas`, `tornado-drivers/cublas-jni`, `tornado-cufft`, `tornado-drivers/cufft-jni`, runtime SPI in `tornado-runtime/.../library/` | `TestCuBlas` (11), `TestCuBlasLt` (5), `TestCuFft` (3); `BenchmarkSgemm` (FP16 160.6 TFLOP/s, 27x vs tiled KernelContext), `BenchmarkLtFusedMlp` (1.68x fusion), `MatrixVectorRowMajorWithCuBlas`, `BenchmarkFft` (793x vs JIT DFT) | active base |
| `hybrid-cufft` | cuFFT extensions on top of the base: R2C/C2R, Z2Z (FP64), 2D C2C, `prepare()`-based capture-safe plan creation, `FrequencyFilterExample` | `tornado-cufft` (extended) | `TestCuFft` (8); CUDA-graph FFT round trip | **merged into `hybrid`** (a0a42c3b8) |
| `hybrid-cudnn` | **cuDNN**: softmax/relu/sigmoid/tanh/maxpool/conv2d (FP32 NCHW legacy API, plan + workspace caching), **fused SDPA/flash attention FP16** via cudnn-frontend v1.25.0 (graph API), causal masks; `prepare()` hook re-applied; core fix: `isArgumentIgnorable` covers Boolean/HalfFloatArray/ByteArray | `tornado-cudnn`, `tornado-drivers/cudnn-jni` | `TestCuDnn` (9 incl. 2 SDPA); `BenchmarkConv2d` (3.1–5.3x vs JIT conv), `BenchmarkSdpa` (**339x/499x** vs JIT attention, 64–84 TFLOP/s) | **merged into `hybrid`** (6359af135) |
| `hybrid-cub` (in progress) | **CUB — codegen track, not a library task**: the CUDA-C backend `#include <cub/cub.cuh>` under NVRTC (header-only) so `@Reduce` can delegate to `cub::DeviceReduce` (+ scan/sort/histogram later), replacing TornadoVM's generated reduction trees. Touches the CUDA-C backend compiler (include paths, reduction lowering), not the SPI. Lowest-effort/highest-credibility given the backend work. | `tornado-drivers/cuda` (codegen), `cuda-jni` (NVRTC include paths) | reduction unittests vs existing `@Reduce` codegen; benchmark vs generated reduction trees | **implemented**: `@Reduce` ADD/MAX/MIN (int/long/float/double) delegate to a two-stage `cub::WarpReduce` via `CUDACubReduceNode` + snippet swap, flag `tornado.cuda.reduce.cub` (default ON); FIXES previously-failing `testComputePi`; 1.35x faster (211 vs 157 GB/s, 64M floats); MUL keeps the tree; umbrella `<cub/cub.cuh>` unusable under NVRTC — targeted `warp_reduce.cuh` include in the kernel preamble |
| `hybrid-cutlass` (planned) | **CUTLASS / CuTe — codegen track**: CUDA-C backend instantiates CUTLASS templates via NVRTC instead of emitting raw `mma.sync` (pairs with the PTX Tensor Core MMA intrinsics work). Known fiddly under NVRTC: needs `-default-device`, careful include pruning — but done in the wild. | `tornado-drivers/cuda` (codegen) | MMA matrix benchmarks vs current PTX/CUDA-C MMA paths | after CUB |

\* Resolved: the `prepare()` SPI hook landed on `hybrid` with the merges
(conflict resolved keeping the `hybrid-cudnn` javadoc, per plan). Merged tip tagged
`hybrid-libs-v1`.

## Merge plan

Ordered so each merge is small, the shared SPI change lands once, and every step has
a validation gate. Run after each merge: `make BACKEND=cuda`, then
`tornado-test -V` for `cublas.TestCuBlas`, `cublas.TestCuBlasLt`, `cufft.TestCuFft`,
`cudnn.TestCuDnn` (whichever exist at that point), and finally `make fast-tests`.

1. **`hybrid-cufft` → `hybrid`.** Brings the cuFFT extensions (R2C/C2R, Z2Z, 2D),
   the `prepare()` SPI hook (`TornadoLibraryProvider` + `TornadoVMInterpreter`), and
   the CUDA-graph FFT test. Expected conflicts: none — the tracker commit on `hybrid`
   and the cuFFT extension commit touch disjoint files.
   *Gate:* `TestCuFft` 8/8, cuBLAS suites unchanged.

2. **`hybrid-cudnn` → `hybrid`.** Brings the cuDNN module pair, SDPA, and the
   `isArgumentIgnorable` core fix. Expected conflicts: exactly one — the `prepare()`
   javadoc in `TornadoLibraryProvider.java` (both branches added the hook; wording
   differs). **Resolution: keep the `hybrid-cudnn` version** (it mentions both cuFFT
   work areas and cuDNN workspaces). The `TornadoVMInterpreter` hunk is byte-identical
   on both branches and merges clean; assembly/launcher/unittests wiring lines are
   additive after the cuFFT lines and merge clean.
   *Gate:* all four suites green (11/5/8/9) + `BenchmarkSdpa` sanity run.

3. **Tag the merged tip** (e.g. `hybrid-libs-v1`) — the last point where the hybrid
   API is pure "library tasks" before codegen-track work starts.

4. **Cut `hybrid-cub` from the merged tip.** Codegen track (CUDA-C backend compiler),
   so keeping it after the library-task merges avoids rebasing backend changes under
   the SPI work.

5. **`hybrid-cub` → `hybrid`** once the reduction suites (currently partially failing
   on the CUDA-C backend: `testComputePi`, `testReductionOneBlockWithLayer`, ...) are
   green with CUB delegation. Then **cut `hybrid-cutlass`** from that tip — it builds
   on the same NVRTC include/template machinery.

6. **Upstreaming (later):** the generic pieces split into separate PRs to
   beehive-lab: (a) runtime SPI + interpreter hooks + `LibraryTask`, (b) CUDA
   stream-exposure natives, (c) the CMake toolkit-preference fix (standalone bug fix,
   can go first), (d) one PR per library module.

Do not delete merged feature branches until the corresponding tracker row is updated —
this file is the source of truth for what lives where.

## Per-branch documentation

- `tornado-cublas/README.md` + `tornado-cublas/ROADMAP.md` (cuBLAS/Lt coverage tracker)
- `tornado-cufft/README.md`
- `tornado-cudnn/README.md` + `tornado-cudnn/ROADMAP.md` (incl. graph-API migration plan)
- `docs/source/hybrid-api.rst` (architecture + provider SPI guide)
