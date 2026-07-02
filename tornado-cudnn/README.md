# TornadoVM Hybrid API — cuDNN Library Tasks

NVIDIA cuDNN as TornadoVM library tasks: deep-learning primitives share
TornadoVM-managed device buffers with JIT-compiled tasks and run on the same CUDA
stream. Third library provider after [tornado-cublas](../tornado-cublas/README.md) and
[tornado-cufft](../tornado-cufft/README.md), added as a self-contained module pair
(`tornado-cudnn` + `tornado-drivers/cudnn-jni`) with zero core-runtime changes.

```java
// CNN block on shared device buffers: conv -> JIT bias task -> ReLU -> max pool
taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, input, filter)
    .libraryTask("conv", CuDnn::cudnnConv2d, input, filter, convOut, n, c, h, w, k, r, s, pad, stride)
    .task("bias", MyClass::addBias, convOut)
    .libraryTask("relu", CuDnn::cudnnRelu, convOut, reluOut, n * k * h * w)
    .libraryTask("pool", CuDnn::cudnnMaxPool2d, reluOut, pooled, n, k, h, w, 2, 2)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, pooled);
```

## Requirements

CUDA toolkit + **cuDNN 9** (`libcudnn9-cuda-12` and `libcudnn9-dev-cuda-12` from the
NVIDIA apt repository).

## Supported operations (FP32, NCHW)

> Note: these v1 bindings use the cuDNN *legacy* compute functions, which are
> deprecated since cuDNN 9.0 (still fully functional — validated on 9.23). The
> [ROADMAP](ROADMAP.md) migrates them to graph-API patterns behind the same factories
> once the cudnn-frontend integration (C0) lands; the Java API is unaffected.

| Factory | cuDNN call | Semantics |
|---|---|---|
| `cudnnSoftmax(in, out, rows, cols)` | `cudnnSoftmaxForward` (ACCURATE, INSTANCE) | Numerically stable per-row softmax — the attention-score shape |
| `cudnnRelu` / `cudnnSigmoid` / `cudnnTanh` `(in, out, size)` | `cudnnActivationForward` | Element-wise activations |
| `cudnnMaxPool2d(in, out, n, c, h, w, window, stride)` | `cudnnPoolingForward` (MAX) | Square-window max pooling, no padding |
| `cudnnConv2d(in, filter, out, n, c, h, w, k, r, s, pad, stride)` | `cudnnConvolutionForward` (IMPLICIT_PRECOMP_GEMM) | 2D cross-correlation, input NCHW, filter KCRS, square pad/stride |

Convolution descriptors, the algorithm, and the (grow-only) device workspace are
created once per shape and cached in the per-(device, execution plan) context —
via the provider `prepare()` hook, **before CUDA graph capture starts**, so
convolutions are capturable and replayable (`TestCuDnn#testConv2dWithCudaGraph`).

[ROADMAP.md](ROADMAP.md) tracks full cuDNN coverage (legacy-API completion, FP16
Tensor Core convolution, and the graph-API flagship: fused SDPA/flash attention and
RMSNorm via cudnn-frontend), with the design and gating test for each feature.

Not bound yet: the cuDNN v9 **graph API** (fused scaled-dot-product attention /
flash attention, fused conv-bias-act, norms) — the flagship follow-up, which reuses
this module's plan-cache pattern; backward/training ops; FP16/BF16 tensor ops;
`cudnnFindConvolutionForwardAlgorithm` autotuning (the fixed IMPLICIT_PRECOMP_GEMM
algorithm is used).

## Build & run

```bash
make BACKEND=cuda
source setvars.sh

# Unit tests (skip automatically without CUDA backend / libtornado-cudnn)
tornado-test -V uk.ac.manchester.tornado.unittests.cudnn.TestCuDnn

# Benchmark: TornadoVM JIT direct convolution vs cuDNN (ResNet-style 3x3 layer)
tornado -m tornado.cudnn/uk.ac.manchester.tornado.cudnn.tests.BenchmarkConv2d 8 64 56 64 50
```

Reference numbers on an NVIDIA GeForce RTX 4090 (FP32, cuDNN 9.23):

| Layer (NCHW, 3x3, pad 1) | TornadoVM JIT direct conv | cuDNN library task | Speedup |
|---|---|---|---|
| 8x64x56x56, k=64 | 3.8 TFLOP/s | 11.6 TFLOP/s | 3.1x |
| 16x128x28x28, k=128 | 4.0 TFLOP/s | 21.1 TFLOP/s | 5.3x |

(FP32 with the fixed IMPLICIT_PRECOMP_GEMM algorithm; the FP16 Tensor Core path and
algorithm autotuning in [ROADMAP.md](ROADMAP.md) are the next big steps.)
