# Hybrid API — Branch Tracker

Global tracker of which branch contains which part of the TornadoVM hybrid API
(native NVIDIA library tasks + codegen integrations). All branches are pushed to the
`mp` remote (github.com/mikepapadim/TornadoVM). Feature branches are cut from the
`hybrid` tip current at the time; merge order below.

| Branch | Contains | Key modules | Tests / benchmarks | Status |
|---|---|---|---|---|
| `hybrid` (base) | Library-task runtime: ServiceLoader SPI (`TornadoLibraryProvider`, `prepare()` hook*), device-buffer reuse via the standard LAUNCH pipeline, CUDA stream exposure, CUDA-Graph capture safety, profiler integration. **cuBLAS + cuBLASLt**: sgemv/sgemm (+beta), TF32 math mode, GemmEx FP16, strided-batched, workspace/`CuBlasOptions` tuning, Lt fused BIAS/GELU_BIAS epilogues with plan caching. **cuFFT (base)**: 1D C2C fwd/inv. | `tornado-cublas`, `tornado-drivers/cublas-jni`, `tornado-cufft`, `tornado-drivers/cufft-jni`, runtime SPI in `tornado-runtime/.../library/` | `TestCuBlas` (11), `TestCuBlasLt` (5), `TestCuFft` (3); `BenchmarkSgemm` (FP16 160.6 TFLOP/s, 27x vs tiled KernelContext), `BenchmarkLtFusedMlp` (1.68x fusion), `MatrixVectorRowMajorWithCuBlas`, `BenchmarkFft` (793x vs JIT DFT) | active base |
| `hybrid-cufft` | cuFFT extensions on top of the base: R2C/C2R, Z2Z (FP64), 2D C2C, `prepare()`-based capture-safe plan creation, `FrequencyFilterExample` | `tornado-cufft` (extended) | `TestCuFft` (8); CUDA-graph FFT round trip | done, pushed |
| `hybrid-cudnn` | **cuDNN**: softmax/relu/sigmoid/tanh/maxpool/conv2d (FP32 NCHW legacy API, plan + workspace caching), **fused SDPA/flash attention FP16** via cudnn-frontend v1.25.0 (graph API), causal masks; `prepare()` hook re-applied; core fix: `isArgumentIgnorable` covers Boolean/HalfFloatArray/ByteArray | `tornado-cudnn`, `tornado-drivers/cudnn-jni` | `TestCuDnn` (9 incl. 2 SDPA); `BenchmarkConv2d` (3.1–5.3x vs JIT conv), `BenchmarkSdpa` (**339x/499x** vs JIT attention, 64–84 TFLOP/s) | done, pushed |
| `hybrid-cub` (planned) | **CUB — codegen track, not a library task**: the CUDA-C backend `#include <cub/cub.cuh>` under NVRTC (header-only) so `@Reduce` can delegate to `cub::DeviceReduce` (+ scan/sort/histogram later), replacing TornadoVM's generated reduction trees. Touches the CUDA-C backend compiler (include paths, reduction lowering), not the SPI. Lowest-effort/highest-credibility given the backend work. | `tornado-drivers/cuda` (codegen), `cuda-jni` (NVRTC include paths) | reduction unittests vs existing `@Reduce` codegen; benchmark vs generated reduction trees | next |
| `hybrid-cutlass` (planned) | **CUTLASS / CuTe — codegen track**: CUDA-C backend instantiates CUTLASS templates via NVRTC instead of emitting raw `mma.sync` (pairs with the PTX Tensor Core MMA intrinsics work). Known fiddly under NVRTC: needs `-default-device`, careful include pruning — but done in the wild. | `tornado-drivers/cuda` (codegen) | MMA matrix benchmarks vs current PTX/CUDA-C MMA paths | after CUB |

\* The `prepare()` SPI hook currently exists on `hybrid-cufft` and `hybrid-cudnn`
(applied identically on both); it belongs on `hybrid` at the next merge.

## Merge order (suggested)

`hybrid-cufft` → `hybrid` (brings R2C/2D/prepare), then `hybrid-cudnn` → `hybrid`
(prepare applies cleanly — identical change), then cut `hybrid-cub` from the new tip.

## Per-branch documentation

- `tornado-cublas/README.md` + `tornado-cublas/ROADMAP.md` (cuBLAS/Lt coverage tracker)
- `tornado-cufft/README.md`
- `tornado-cudnn/README.md` + `tornado-cudnn/ROADMAP.md` (incl. graph-API migration plan)
- `docs/source/hybrid-api.rst` (architecture + provider SPI guide)
