# tornado-cutlass

NVIDIA **CUTLASS** GEMM kernels exposed as TornadoVM **library tasks** — native
GPU calls that ride the standard TornadoVM task-graph pipeline (device-buffer
reuse, CUDA-graph capture, the profiler) and interleave with JIT-compiled
`@Parallel`/`KernelContext` tasks on the same CUDA stream.

Provider id: **`nvidia/cutlass`**. This is the same hybrid-API mechanism as
`tornado-cublas`/`tornado-cudnn` (see `docs/source/hybrid-api.rst`); CUTLASS is a
header-only template library, so its kernels are compiled ahead of time by
`nvcc` in the `tornado-drivers/cutlass-jni` module.

## Layout convention

All operands are **row-major**: `A` is `m×k`, `B` is `k×n`, `C`/`D` are `m×n`,
and the kernel computes `C = alpha·A·B + beta·C`. Unlike cuBLAS there is no
column-major transposition to reason about — the TornadoVM native array layout
maps to CUTLASS directly.

## Operations

| Factory | Kernel | Notes |
|---|---|---|
| `Cutlass.cutlassSgemm(m,n,k,alpha,a,b,beta,c)` | FP32 SIMT GEMM | Correctness baseline, no shape constraints |
| `Cutlass.cutlassHgemm(m,n,k,alpha,a,b,beta,d)` | FP16 tensor-core GEMM, FP32 accumulate | Requires `k,n` multiples of 4 |
| `Cutlass.cutlassHgemmBatched(m,n,k,alpha,a,b,beta,c,batchCount)` | Strided-batched FP16 tensor-core GEMM | `batchCount` matrices packed contiguously; requires `k,n` multiples of 4 |
| `Cutlass.cutlassGemmBiasRelu(m,n,k,a,b,bias,d)` | Fused `relu(A·B + bias)` | `bias` is a length-`n` row vector |
| `Cutlass.cutlassGemmBiasGelu(m,n,k,a,b,bias,d)` | Fused `gelu(A·B + bias)` | `bias` is a length-`n` row vector |
| `Cutlass.cutlassGemmBiasSilu(m,n,k,a,b,bias,d)` | Fused `silu(A·B + bias)` (swish; SwiGLU gate) | `bias` is a length-`n` row vector |
| `Cutlass.cutlassGemmBiasSigmoid(m,n,k,a,b,bias,d)` | Fused `sigmoid(A·B + bias)` | `bias` is a length-`n` row vector |
| `Cutlass.cutlassGemmBiasTanh(m,n,k,a,b,bias,d)` | Fused `tanh(A·B + bias)` | `bias` is a length-`n` row vector |
| `Cutlass.cutlassGemmBiasHardSwish(m,n,k,a,b,bias,d)` | Fused `hardswish(A·B + bias)` | `bias` is a length-`n` row vector |

FP32 uses `FloatArray`; the FP16 kernels use `HalfFloatArray`. The fused epilogues
cover the activation functions CUTLASS ships as ready-made `LinearCombination*`
output operators (ReLU, GELU, SiLU/swish, Sigmoid, Tanh, HardSwish), computing
`act(A·B + bias)` in a single tensor-core kernel with no extra global-memory round
trip. SiLU in particular is the gate activation of the SwiGLU feed-forward block
used across the LLaMA/Qwen LLM families.

### Alignment / shape constraint

TornadoVM native arrays place data after a 24-byte header, so device pointers
are only guaranteed **8-byte aligned**. The FP16 tensor-core kernels therefore
load 4-half (8-byte) vectors (`kAlignment = 4`) and require **`k` and `n` to be
multiples of 4** — the factory rejects other shapes with a clear message. The
FP32 SIMT kernel (alignment 1) has no such constraint.

## Usage

```java
TaskGraph graph = new TaskGraph("mlp")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b, bias)
    .task("pre", MyKernels::preprocess, a)                              // JIT
    .libraryTask("gemm", Cutlass::cutlassGemmBiasGelu, m, n, k, a, b, bias, d)  // CUTLASS
    .task("post", MyKernels::postprocess, d)                           // JIT
    .transferToHost(DataTransferMode.EVERY_EXECUTION, d);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withCUDAGraph();   // optional: capture the whole pre→CUTLASS→post pipeline
    plan.execute();
}
```

The provider binds to the plan's CUstream in `createContext` and sizes its
device workspace in `prepare()` (before CUDA-graph capture begins), so
`libraryTask` calls are capture-safe.

## Build & run

CUTLASS requires the CUDA backend:

```bash
make BACKEND=cuda
```

`tornado-drivers/cutlass-jni` fetches header-only CUTLASS (v3.5.1) via CMake
`FetchContent` and compiles `tornado-cutlass.cu` with `nvcc` for SM
architecture **80** (`sm_80` SASS, binary-compatible with all Ampere/Ada 8.x
devices) plus `compute_80` PTX that JIT-compiles for Hopper. Override with the
`CUDA_ARCH` environment variable, e.g. `CUDA_ARCH=89`, to emit native SASS for a
specific device.

**CUTLASS 3.5.1 requires CUDA 12.0+.** On an older toolkit the native library is
skipped at build time (the build still succeeds) and the CUTLASS tasks report
`UNSUPPORTED` at runtime, since `System.loadLibrary("tornado-cutlass")` fails.

Tests and benchmark:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cutlass.TestCutlass
tornado -m tornado.cutlass/uk.ac.manchester.tornado.cutlass.tests.BenchmarkCutlassGemm --params="1024 100"
```

On an RTX 4090 (SM 8.9) at 1024×1024: CUTLASS FP16 tensor-core reaches ~39
TFLOP/s (≈7.7× the tiled `KernelContext` kernel), and the fused
GEMM+bias+GELU epilogue is ~1.28× faster than an unfused CUTLASS GEMM followed
by a JIT bias+GELU task.
