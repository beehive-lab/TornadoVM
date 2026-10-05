# TornadoVM Hybrid API — Apple MLX Library Tasks

This module lets a TornadoVM `TaskGraph` mix JIT-compiled Java tasks with
[Apple MLX](https://github.com/ml-explore/mlx) operations on the Metal backend. An MLX library
task launches MLX's own Metal kernels from `mlx.metallib` on TornadoVM's command queue, bound to
TornadoVM's buffers. There is no MLX array, no result allocation and no copy: a JIT kernel can
produce data for an MLX operation, and consume its output, as if both were JIT-compiled.

```java
TaskGraph taskGraph = new TaskGraph("mlx")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, weight, w)
    .task("embed", MyClass::embed, x)                                   // JIT-compiled kernel
    .libraryTask("norm", MlxNeuralNetwork::rmsNorm, x, weight, xn, rows, dim, 1e-5f) // MLX kernel
    .libraryTask("proj", MlxLinearAlgebra::matmul, xn, w, y, rows, dim, hidden)      // MLX kernel
    .task("activate", MyClass::silu, y)                                 // JIT-compiled kernel
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
    plan.execute();
}
```

## Requirements

- macOS on Apple silicon
- MLX's kernel library: `brew install mlx` installs `/opt/homebrew/opt/mlx/lib/mlx.metallib`.
- JDK 21

## Build

From the repository root:

```bash
make BACKEND=metal
source setvars.sh
```

This builds the Metal backend and the `tornado-mlx` Java module. The module talks to Metal through
the Objective-C runtime with `java.lang.foreign`, so there is no native module to build. It is
registered as a `TornadoLibraryProvider` (`apple/mlx`) and found through `ServiceLoader`. Use
`-Dtornado.mlx.metallib=<path>` to load `mlx.metallib` from somewhere other than Homebrew's prefix.

## Supported operations

All factories are static methods used as the second argument of
`taskGraph.libraryTask(id, factory, args...)`, one class per category, as in the coverage manifest of
[TornadoMLXBenchmarks](https://github.com/kotselidis/TornadoMLXBenchmarks). Each task's function name
is the mlx-c name of the operation it runs (`mlx_add`, `mlx_sum_axis`, ...).

| Category | Factory class | Operations |
|---|---|---|
| Arithmetic (51) | `MlxArithmetic` | add, subtract, multiply, divide, maximum, minimum, negative, square, sqrt, rsqrt, exp, expm1, log, log1p, log2, log10, logaddexp, power, remainder, floor_divide, divmod, reciprocal, abs, sign, ceil, floor, round, clip, where, sin, cos, tan, arcsin, arccos, arctan, arctan2, sinh, cosh, tanh, arcsinh, arccosh, arctanh, degrees, radians, erf, erfinv, sigmoid, nan_to_num, real, imag, conjugate |
| Logic (23) | `MlxLogic` | equal, not_equal, greater, greater_equal, less, less_equal, isfinite, isinf, isnan, isneginf, isposinf, bitwise_and/or/xor/invert, left_shift, right_shift, logical_and/or/not, isclose, allclose, array_equal |
| Reductions (37) | `MlxReductions` | sum, prod, max, min, mean, var, std, logsumexp, all, any (whole, `_axis`, `_axes`), argmin, argmax, softmax |
| Scans (5) | `MlxScans` | cumsum, cumprod, cummax, cummin, logcumsumexp |
| Sorting (10) | `MlxSorting` | sort, argsort, partition, argpartition (whole and `_axis`), topk |
| Indexing (4) | `MlxIndexing` | slice, slice_update, slice_update_add, slice_update_prod |
| LinearAlgebra (14) | `MlxLinearAlgebra` | matmul, addmm, einsum, tensordot, inner, outer, kron, segmented_mm, gather_mm, linalg_cross, linalg_norm, linalg_norm_l2, linalg_norm_matrix |
| Quantization (5) | `MlxQuantization` | quantize, dequantize (affine and mxfp8), quantized_matmul, gather_qmm, qqmm |
| NeuralNetwork (5) | `MlxNeuralNetwork` | fast_rms_norm, fast_layer_norm, fast_rope, fast_rope_dynamic, fast_scaled_dot_product_attention |
| Fft (16) | `MlxFft` | fft, ifft, rfft, irfft (1D, 2D, nD), fftshift, ifftshift, fftfreq, rfftfreq |
| Convolution (5) | `MlxConvolution` | conv1d, conv2d, conv_general, conv_transpose1d, conv_transpose2d |
| Creation (21) | `MlxCreation` | arange, linspace, eye, identity, tri, tril, triu, diag, diagonal, trace, full, zeros, ones (and `_like`), bartlett, blackman, hamming, hanning, meshgrid |
| Shape (37) | `MlxShape` | reshape, flatten, unflatten, squeeze, expand_dims, atleast_1d/2d/3d, transpose, swapaxes, moveaxis, broadcast_to, broadcast_arrays, as_strided, contiguous, copy, astype, view, number_of_elements, concatenate, stack, split, repeat, tile, roll, pad |
| Random (13) | `MlxRandom` | bits, uniform, normal, randint, bernoulli, truncated_normal, gumbel, laplace, categorical, permutation (of `arange`) |

### Not supported

These MLX operations have no TornadoVM/MLX route, so the module does not expose them:

- linear-algebra decompositions, inverses and solves (MLX runs them on its CPU stream only)
- take, gather, scatter, `put_along_axis`, `masked_scatter`, dynamic slices, and `slice_update` max/min
- `median`, 3D convolutions, `block_masked_mm`, the Hadamard transform, fp8 conversion,
  `random_permutation` of an array and `random_multivariate_normal`
- int32 element-wise arithmetic (`add`, `subtract`, `multiply`, `divide`, `maximum`, `minimum`,
  `negative`, `square`, `abs`, `sign`, `remainder`)

### Conventions

- **Layout.** MLX and TornadoVM are both row-major, so arrays need no transposes and dimensions are
  passed in their natural order (`matmul(a, b, c, m, k, n)` for `c[m, n] = a[m, k] @ b[k, n]`).
- **Types.** Most operations come in `FloatArray`, `HalfFloatArray` and `BFloat16Array` forms; logic,
  bitwise and index operations also take `IntArray`. Boolean results are written as 0 or 1 into a
  `ByteArray`. Complex values are interleaved `(re, im)` pairs in a `FloatArray`.
- **Axes.** Reductions and scans view their input as `[outer, len, inner]` (one axis) or
  `[outer, len1, len2, inner]` (two adjacent axes) and reduce the middle.
- **Argument forms.** A route reproduces MLX's kernel launch exactly, so some shapes are not covered:
  axis reductions need `inner == 1`, `matmul` rejects `m == n == 1`, and transposed and input-dilated
  convolutions need input and output channel counts that are multiples of 16, or at least 256 output
  pixels. A call no kernel covers fails rather than falling back:

  ```
  [ERROR] MLX add: no TornadoVM/MLX kernel takes these arguments (IntArray[1027], IntArray[1027], IntArray[1027])
  ```

## Usage

### Mixing with TornadoVM tasks

MLX library tasks are scheduled like any other task. Dependencies come from the standard
`Access[]`-driven data-flow graph: each factory marks its output arguments `WRITE_ONLY` and the rest
`READ_ONLY`. The Metal backend batches JIT kernels into shared command buffers and drains the batch
before a library task takes the queue or a buffer address, so an MLX kernel always sees the writes of
earlier JIT kernels. Each MLX call commits its own command buffer on the same queue and waits for it,
so the next JIT task sees its output.

### Debugging

| Flag | Effect |
|---|---|
| `--devices` | Check that the Metal device is visible |
| `-Dtornado.mlx.metallib=<path>` | Load `mlx.metallib` from `<path>` |
| `--jvm "-Dtornado.unittests.device=0:0"` | Select backend:device for the unit tests |

If `mlx.metallib` cannot be found, the provider declines the device and an MLX task fails with
``Library `apple/mlx` is not supported on device``; install MLX or set `-Dtornado.mlx.metallib`.

## Tests

The JUnit suite lives in `tornado-unittests` (`uk.ac.manchester.tornado.unittests.mlx`), one class
per category (`TestMlxArithmetic`, `TestMlxReductions`, `TestMlxLinearAlgebra`, ...) plus `TestMlx`,
which covers how MLX tasks behave in a task graph: mixed with JIT tasks, buffers shared across task
graphs, inputs changing between executions, and the error for unsupported arguments. Each test runs
one factory and checks it against a sequential Java reference. The tests report `UNSUPPORTED` when
the default device is not Metal or `mlx.metallib` is missing.

```bash
tornado-test --mlx                                                      # all MLX tests
tornado-test -V uk.ac.manchester.tornado.unittests.mlx.TestMlxLinearAlgebra
```

Every bound operation has a test. [TornadoMLXBenchmarks](https://github.com/kotselidis/TornadoMLXBenchmarks) keeps the
coverage manifest that checks it.

## Benchmarks

The benchmarks live in [TornadoMLXBenchmarks](https://github.com/kotselidis/TornadoMLXBenchmarks); its
README shows how to build and run them. Reference numbers: Apple M1 Pro (16-core GPU, 200 GB/s),
MLX 0.32.1, measured at commit `c26ee040b`. Times are marginal: the cost of one more task in a chain
of 8.

Selected operations (µs):

| Operation | Shape | TornadoVM/MLX | JIT | Java |
|---|---|---:|---:|---:|
| add (f32) | 16,777,216 | 1,408 | 1,387 | 13,370 |
| rmsNorm (f32) | 512×4096 | 306 | 402 | 3,749 |
| softmax (f32) | 151,936 | 273 | 466 | 1,577 |
| matmul (f32) | 512×4096×4096 | 4,721 | 11,248 | 32,715,721 |
| quantizedMatmul q4 | 16×4096×4096 | 578 | 3,148 | – |
| sortAxis (f32) | 512×2048 | 436 | 1,586 | 38,330 |
| conv2d (f32) | 1×128×128×64, k3 | 586 | 61,533 | – |

These numbers predate Metal command-buffer batching (`d786a9518`), which lowers per-task cost, so a
fresh run gives lower absolute times, mostly for decode-sized cases.

## Layout

| Path | Contents |
|---|---|
| `src/main/java/.../mlx/` | `Mlx` (library name and task helpers) and one factory class per category |
| `src/main/java/.../mlx/provider/` | `MlxLibraryProvider` (the SPI provider), `MlxKernelRoutes` (operation-to-kernel mapping), `MlxMetalKernels` (Objective-C bridge to Metal, MLX element types) |

## Adding an operation

1. Add a factory to the category's class that calls `Mlx.task("mlx_<op>", outputIndex, args...)`, with
   the mlx-c name of the operation.
2. Add a route for `mlx_<op>` in `MlxKernelRoutes`: it checks the arguments and encodes MLX's kernels
   into a `Program`, following MLX's own `ops.cpp` and launch code.
3. Add a test to the category's class in `tornado-unittests/.../unittests/mlx`.

For a new native library, see `HYBRID_API_GUIDE.md`; `MlxLibraryProvider` is a provider with no
native module.
