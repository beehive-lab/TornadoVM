# tornado-mlx: Apple MLX kernels in place

This branch (`feature/mlx-integration-7.1.0`) runs [Apple MLX](https://github.com/ml-explore/mlx)
operations as TornadoVM library tasks on the Metal backend. Every task launches MLX's own Metal
kernels from `mlx.metallib` on TornadoVM's command queue, bound to TornadoVM's buffers. There is no
MLX array, no result allocation and no copy-back. The task runs like a JIT-compiled kernel.

```java
TaskGraph graph = new TaskGraph("s0")
        .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
        .libraryTask("add", Mlx::add, a, b, c)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
```

## Two MLX branches

| Branch | MLX path | Operations |
|---|---|---|
| `feature/mlx-integration-7.1.0` (this one) | In-place Metal kernels only | 246 |
| [`feature/mlx-c`](https://github.com/kotselidis/TornadoVM/tree/feature/mlx-c) | mlx-c only: MLX allocates each result, then the provider copies it into the output | 292, including CPU-only linear algebra |

Commit `c3a21345f` is the last one with both paths. The benchmarks that compare them live in
[TornadoMLXBenchmarks](https://github.com/kotselidis/TornadoMLXBenchmarks).

## Requirements

- macOS on Apple silicon, TornadoVM built with `make BACKEND=metal`
- MLX's kernel library: `brew install mlx` installs `/opt/homebrew/opt/mlx/lib/mlx.metallib`.
  Use `-Dtornado.mlx.metallib=<path>` to load it from somewhere else. mlx-c is not needed.

## What is supported

The factories are in `uk.ac.manchester.tornado.mlx`: `Mlx` (the operations LLM inference uses),
`MlxMath`, `MlxLogic`, `MlxReduce`, `MlxShape`, `MlxCreate`, `MlxIndex`, `MlxSort`, `MlxFft`, `MlxConv`,
`MlxLinalg`, `MlxProducts` and `MlxRandom`. Each factory carries `@MlxOp` with the MLX operation it binds.

The branch has only operations with an in-place route. These are **not** available:

- linear-algebra decompositions, inverses and solves (MLX runs them on its CPU stream only)
- take, gather, scatter, `put_along_axis`, `masked_scatter`, dynamic slices, and `slice_update` max/min
- `median`, 3D convolutions, `block_masked_mm`, the Hadamard transform, fp8 conversion,
  `random_permutation` (of an array) and `random_multivariate_normal`
- int32 element-wise arithmetic (`add`, `subtract`, `multiply`, `divide`, `maximum`, `minimum`,
  `negative`, `square`, `abs`, `sign`, `remainder`)

Some routes also limit argument forms. Axis reductions need `inner == 1`, so the reduced axes must
be trailing. `matmul` rejects `m == n == 1`. Transposed and input-dilated convolutions need input and output
channel counts that are multiples of 16, or at least 256 output pixels. A call that no kernel covers fails with the operation name and its
argument types and sizes:

```
[ERROR] MLX add: no in-place MLX kernel takes these arguments (IntArray[1027], IntArray[1027], IntArray[1027])
```

## Layout

| Path | Contents |
|---|---|
| `src/main/java/.../mlx/` | Factory classes, `MlxOp` |
| `src/main/java/.../mlx/provider/` | `MlxLibraryProvider` (the SPI provider), `MlxKernelRoutes` (operation-to-kernel mapping), `MlxMetalKernels` (Objective-C bridge to Metal), `MlxTypes` |
| `src/main/java/.../mlx/jit/` | `KernelContext` JIT counterparts of each operation, annotated `@JitBaseline` |
| `coverage.json` | Operation coverage manifest; the build fails if a bound operation has no test or no tested JIT baseline |
| `mlx-c-api.json`, `coverage-overrides.json` | The MLX operation catalog and hand-kept coverage decisions |
| `scripts/update_coverage.py` | Regenerates `coverage.json`; Maven runs it with `--check` |

## Tests

```bash
tornado-test --mlx
```

The command runs the MLX unit tests (`tornado-unittests/.../unittests/mlx`). Each test checks the MLX
task and its JIT counterpart against a Java reference. After you add a factory or a test, run
`python3 tornado-mlx/scripts/update_coverage.py`.
