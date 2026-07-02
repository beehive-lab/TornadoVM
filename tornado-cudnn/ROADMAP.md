# cuDNN Hybrid API — Coverage Roadmap & Progress

Living document tracking cuDNN coverage for TornadoVM library tasks, derived from the
[cuDNN documentation](https://docs.nvidia.com/deeplearning/cudnn/latest/) (legacy API +
[graph API](https://docs.nvidia.com/deeplearning/cudnn/latest/developer/graph-api.html)).
Each feature lists the design, the extension points it exercises, and the gating test.
Status: `[x]` done / `[~]` in progress / `[ ]` planned / `[-]` not planned.

Conventions follow `tornado-cublas/ROADMAP.md`: every feature ships with a JUnit test
in `tornado-unittests` validating against a Java reference, and perf features ship a
benchmark vs sequential Java and the best TornadoVM KernelContext/JIT kernel.

---

## Current status (2026-07-02, branch `hybrid-cudnn`)

- [x] Module pair `tornado-cudnn` + `tornado-drivers/cudnn-jni` (links `cudnn` +
      `cudart`), third provider `"nvidia/cudnn"` — zero core-runtime changes
- [x] FP32/NCHW legacy-API v1: `cudnnSoftmax` (per-row, ACCURATE/INSTANCE),
      `cudnnRelu`/`cudnnSigmoid`/`cudnnTanh`, `cudnnMaxPool2d`,
      `cudnnConv2d` (IMPLICIT_PRECOMP_GEMM, plan + grow-only workspace cached per shape)
- [x] Conv plans + workspace created in the `prepare()` SPI hook (pre-capture) →
      convolutions are CUDA-Graph capturable
- [x] `TestCuDnn` 7 JUnit tests (softmax/relu/tanh/maxpool/conv2d vs Java;
      conv→JIT-bias→relu→pool mixed pipeline; conv under `withCUDAGraph`);
      `BenchmarkConv2d` (JIT direct conv vs cuDNN, ResNet-style 3x3 layer)
- [x] C0+C1: fused SDPA/flash attention forward (FP16, causal option) via
      cudnn-frontend v1.25.0 — 339x/499x vs JIT attention kernel (`BenchmarkSdpa`).
      Core fix along the way: `isArgumentIgnorable` now covers Boolean/HalfFloatArray/
      ByteArray task args (Boolean args previously crashed `lockInPendingFieldsObjects`)
- [x] Validated on RTX 4090 / cuDNN 9.23: `TestCuDnn` 7/7 PASS (incl. mixed
      conv→JIT-bias→relu→pool pipeline and conv under CUDA graph capture);
      `BenchmarkConv2d`: 11.6 / 21.1 TFLOP/s = 3.1x / 5.3x vs JIT direct conv
      (8x64x56x56 k=64 and 16x128x28x28 k=128, FP32). Note: deb package installs
      headers under /usr/include/x86_64-linux-gnu (multiarch) — CMake hints cover it

---

## Design decision for the graph API (informs Track C/D)

The modern cuDNN interface is the **graph API**: operation graphs finalized into
execution plans via backend descriptors (`cudnnBackendCreateDescriptor` /
`SetAttribute` / `Finalize` / `Execute`). Binding the descriptor API generically to
Java would be a huge, brittle surface; NVIDIA explicitly recommends the header-only
C++ **[cudnn-frontend](https://github.com/NVIDIA/cudnn-frontend)** instead.

**Chosen approach:** keep the Java surface as *typed factories per fused pattern*
(exactly like `CuBlasLt.ltMatmulGeluBiasFP16`), and implement each pattern as a C++
"plan builder" in `cudnn-jni` using the cudnn-frontend headers (vendored or fetched at
build time). Each pattern = one opaque native plan (graph → heuristic engine →
execution plan + workspace size), created in `prepare()` (pre-capture), cached per
shape in the provider context, executed with a variant pack binding the TornadoVM
device pointers. This mirrors the proven `lt_plan_t` / `cudnn_conv_plan_t` pattern.

---

## Deprecation status (verified against the cudnn_ops / cudnn_cnn / cudnn_adv API pages)

The legacy compute functions used by v1 — `cudnnSoftmaxForward`,
`cudnnActivationForward`, `cudnnPoolingForward`, `cudnnConvolutionForward` — and most
Track-A candidates (`cudnnAddTensor`, `cudnnOpTensor`, `cudnnReduceTensor`,
`cudnnBatchNormalizationForwardInference`, LRN) are **deprecated since cuDNN 9.0**:
fully functional (validated on 9.23) but slated for eventual removal in favor of the
graph API. Descriptor/workspace/algorithm-query helpers are not deprecated. Practical
consequence: C0 (cudnn-frontend) is not only the gate for fusions — once it lands, the
v1 ops should be re-implemented as graph-API patterns behind the same Java factories
(tracked as F6 below). The Java API surface is unaffected either way.

## Track A — Legacy API completion (FP32 first)

Note: all compute functions in this track are deprecated-since-9.0 (see above);
prefer graph-API equivalents once C0 exists.

| # | Feature | cuDNN API | Design / Test | Status |
|---|---|---|---|---|
| A1 | Softmax variants | `cudnnSoftmaxForward` modes (LOG, MODE_CHANNEL) | `cudnnLogSoftmax`, channel-mode factory; `TestCuDnn#testLogSoftmax` vs Java | [ ] |
| A2 | More activations | `cudnnActivationForward` ELU, CLIPPED_RELU, SWISH (+ coef) | extend activation factory with coef param; tests vs Java | [ ] |
| A3 | Average pooling + padding | `cudnnPoolingForward` AVG (incl./excl. padding), asymmetric window/pad | extend `cudnnMaxPool2d` into `cudnnPool2d(mode, ...)`; `TestCuDnn#testAvgPool` | [ ] |
| A4 | Bias add | `cudnnAddTensor` (broadcast NCHW bias) | `cudnnAddBias(inout, bias, n,c,h,w)` — replaces the JIT bias task in CNN pipelines; test vs Java | [ ] |
| A5 | BatchNorm inference | `cudnnBatchNormalizationForwardInference` (SPATIAL) | `cudnnBatchNormInference(x, y, scale, bias, mean, var, eps, n,c,h,w)`; test vs Java formula | [ ] |
| A6 | Tensor ops / reductions | `cudnnOpTensor` (ADD/MUL/MIN/MAX), `cudnnReduceTensor` | generic elementwise/reduction tasks; note overlap with future CUB track | [ ] |
| A7 | Dropout (inference no-op, training w/ states) | `cudnnDropoutForward` | needs RNG state buffer in context; low priority (inference focus) | [ ] |
| A8 | LRN | `cudnnLRNCrossChannelForward` | legacy vision nets only | [-] |
| A9 | RNN API | `cudnnRNNForward` (v8) | NOT deprecated (v8 API actively maintained) — skipped for scope: transformer/LLM focus | [-] |

## Track B — Convolution depth

| # | Feature | Design / Test | Status |
|---|---|---|---|
| B1 | Algorithm autotuning | `cudnnFindConvolutionForwardAlgorithm` (or v7 heuristic get) at plan creation instead of fixed IMPLICIT_PRECOMP_GEMM; store per-plan; expose `CuDnnOptions.withAutotune()` via `withTuning` (pattern from `CuBlasOptions`). *Test:* result identical, log chosen algo | [ ] |
| B2 | FP16 convolution (Tensor Cores) | `HalfFloatArray` I/O, `CUDNN_DATA_HALF` + FP32 compute via `cudnnSetConvolutionMathType(CUDNN_TENSOR_OP_MATH)`. *Test:* vs FP32 conv, relaxed tol; benchmark row (expect large Tensor Core gain) | [ ] |
| B3 | NHWC layout | `CUDNN_TENSOR_NHWC` descriptors (preferred layout for Tensor Cores); factory variant. *Test:* NHWC vs NCHW results | [ ] |
| B4 | Grouped / depthwise conv | `cudnnSetConvolutionGroupCount`; mobile-net style. *Test:* groups=c depthwise vs Java | [ ] |
| B5 | Conv backward (data/filter) | `cudnnConvolutionBackwardData/Filter` — training story; same plan pattern. *Test:* finite-difference check | [ ] |
| B6 | 3D convolution | Nd descriptors (`cudnnSetConvolutionNdDescriptor`) | [ ] |

## Track C — Graph API: SDPA / flash attention (the flagship)

Premier LLM pattern: `BMM–softmax–BMM` fused, FP16/BF16 (Ampere+), FP8 (Hopper+),
head dim multiple of 8, ≤256 on Hopper/Ada; softmax stats FP32; last stride must be 1.

| # | Feature | Design / Test | Status |
|---|---|---|---|
| C0 | cudnn-frontend integration in `cudnn-jni` | FetchContent v1.25.0, populate-only (its own CMakeLists needs cmake>=3.23; header-only so only include/ is used); `sdpa_plan_t` builder | [x] |
| C1 | SDPA forward, FP16, inference | `CuDnn.sdpaForward(...)` on `HalfFloatArray` (BHSD packed); plan in `prepare()`, shared grow-only workspace. *Tests:* `TestCuDnn#testSdpaForward` + `#testSdpaForwardCausal` vs Java attention reference (2e-2 tol) — PASS. *Benchmark:* `BenchmarkSdpa`: 64.2 TFLOP/s = **339x** vs JIT attention at (4,16,1024,128); 84.3 TFLOP/s = **499x** at (1,32,2048,128) | [x] |
| C2 | Causal / padding masks, GQA/MQA head layout | Causal mask DONE (C1); padding masks + kv-heads != q-heads pending. *Test:* masked vs Java | [~] |
| C3 | Paged KV-cache attention | `CUDNN_BACKEND_OPERATION_PAGED_ATTENTION_*` — decode-time inference with block tables. *Test:* vs contiguous-cache C1 | [ ] |
| C4 | SDPA FP8 (Hopper) | Blocked on FP8 tornado-api types AND Hopper hardware (RTX 4090 is Ada: fprop FP8 requires Hopper) | [-] (hw) |
| C5 | SDPA backward | Training story; Hopper-leaning constraints | [ ] |

## Track D — Graph API: other fusions

| # | Feature | Pattern | Design / Test | Status |
|---|---|---|---|---|
| D1 | LayerNorm / RMSNorm forward | `NormalizationForward` (`CUDNN_LAYER_NORM`, `CUDNN_RMS_NORM`), inference phase; FP16/FP32 I/O, FP32 compute | RMSNorm is the Llama block companion to SDPA; `CuDnn.rmsNorm(x, y, scale, eps, rows, cols)`. *Test:* vs Java RMSNorm | [ ] |
| D2 | ConvBiasAct fused | Specialized pre-compiled engine | replaces conv + A4 bias + relu with one plan; benchmark vs the unfused `TestCuDnn#testConvReluPoolPipeline` chain | [ ] |
| D3 | Matmul + pointwise epilogue | Generic runtime fusion | overlaps cuBLASLt epilogues — only add if a pattern Lt can't do emerges (e.g. per-tensor FP8 scaling) | [-] (use cuBLASLt) |
| D4 | NormAddRelu (BN + residual-add + ReLU) | Specialized engine (CC≥8.0) | vision/resnet block. *Test:* vs Java | [ ] |
| D5 | ResampleFwd (pooling/upsampling via graph API) | supersedes A3 long-term | [ ] |

## Track E — Data types

| # | Feature | Constraint | Status |
|---|---|---|---|
| E1 | FP16 across ops (`HalfFloatArray`) | B2 conv, C1 SDPA, D1 norms; softmax/activations FP16 I/O | [ ] |
| E2 | BF16 | Blocked: no BF16 array type in tornado-api (shared blocker with cuBLAS C4) | [ ] |
| E3 | FP8 E4M3/E5M2 | Blocked: no FP8 type + most FP8 paths need Hopper (RTX 4090 = Ada) | [-] (hw) |
| E4 | INT8 inference conv | Legacy API `CUDNN_DATA_INT8x4`/NHWC constraints; niche vs FP16 on Ada | [ ] |

## Track F — Robustness / infra

| # | Item | Notes | Status |
|---|---|---|---|
| F1 | Version / capability gating | `cudnnGetVersion` bound; guard graph-API features by version (≥9.x) and compute capability (e.g. SDPA fprop needs Ampere+) with clear [UNSUPPORTED]-style errors | [~] (version bound) |
| F2 | `CuDnnOptions` via `withTuning` | math type (Tensor Core on/off), autotune toggle (B1), deterministic flag | [ ] |
| F3 | Plan serialization | graph-API execution-plan serialization to cut first-run JIT cost; pairs with heuristics Mode A/B choice | [ ] |
| F4 | CUDA graph capture for graph-API plans | Same `prepare()` mechanism; verify runtime-fusion engines are capture-safe (docs: supported; runtime compilation happens at finalize, not execute) | [ ] |
| F5 | Multi-GPU batch norm | specialized engines exist; needs TornadoVM multi-device orchestration first | [-] |
| F6 | Migrate v1 ops (softmax/activation/pooling/conv) to graph-API patterns | Behind the same `CuDnn` factories, once C0 lands — removes the dependency on deprecated-since-9.0 legacy functions. *Test:* existing `TestCuDnn` unchanged (pure backend swap gate) | [ ] |

---

## Suggested execution order

1. **Install + validate v1** (pending `libcudnn9` install): run `TestCuDnn`,
   `BenchmarkConv2d`, commit branch
2. **C0 + C1 — SDPA forward FP16**: the flagship; benchmark vs unfused
   cuBLAS+softmax chain and JIT attention (GPULlama3 headline)
3. **D1 — RMSNorm/LayerNorm**: completes the Llama block (SDPA + RMSNorm + Lt MLP)
4. **B2 + B1 — FP16 conv + autotuning**: the CNN story on Tensor Cores
5. **A4 + D2 — bias / ConvBiasAct**: fused CNN block benchmark vs the unfused pipeline
6. **A1–A3, B3–B4** as demand dictates; training (B5, C5) last
