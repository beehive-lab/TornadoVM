# tornado-cusparse

NVIDIA **cuSPARSE** sparse BLAS exposed as TornadoVM **library tasks** — native
GPU calls that ride the standard TornadoVM task-graph pipeline (device-buffer
reuse, CUDA-graph capture, the profiler) and interleave with JIT-compiled
`@Parallel`/`KernelContext` tasks on the same CUDA stream.

Provider id: **`nvidia/cusparse`**. Same hybrid-API mechanism as
`tornado-cublas`/`tornado-cudnn` (see `docs/source/hybrid-api.rst`).

## Operations (FP32, CSR)

The sparse matrix `A` is passed in **CSR** form (32-bit indices, zero-based):
`csrRowOffsets` has `rows + 1` entries, `csrColInd` and `csrValues` have `nnz`
entries. Dense operands are row-major.

| Factory | Operation |
|---|---|
| `Cusparse.cusparseSpMV(rows, cols, nnz, csrRowOffsets, csrColInd, csrValues, x, y)` | `y = A · x` |
| `Cusparse.cusparseSpMM(rows, k, n, nnz, csrRowOffsets, csrColInd, csrValues, B, C)` | `C = A · B` (dense `B` k×n, `C` rows×n) |

`csrRowOffsets` and `csrColInd` are `IntArray`; `csrValues`, `x`, `y`, `B`, `C`
are `FloatArray`.

## Usage

```java
// y = A · x, with A in CSR, then a JIT post-processing task on y
new TaskGraph("spmv")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, rowOffsets, colInd, values, x)
    .libraryTask("spmv", Cusparse::cusparseSpMV, rows, cols, nnz, rowOffsets, colInd, values, x, y)
    .task("scale", MyKernels::scale, y, 2.0f)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
```

The provider binds the cuSPARSE handle to the plan's CUstream and pre-allocates a
small device workspace in `prepare()` (the exact external-buffer size depends on
the sparse structure and is queried at dispatch); for typical CSR SpMV/SpMM the
buffer is bytes-to-KiB, so the default workspace keeps `libraryTask` calls
CUDA-graph-capture-safe.

## Build & run

cuSPARSE ships with the **CUDA toolkit** (no extra install); requires the CUDA
backend:

```bash
make BACKEND=cuda
tornado-test -V uk.ac.manchester.tornado.unittests.cusparse.TestCusparse
```

If `cusparse.h`/`libcusparse` cannot be located the native library is skipped
(the build still succeeds) and the cuSPARSE tasks report `UNSUPPORTED` at
runtime.

## Notes

- FP32 only, CSR only, non-transposed `A`, `alpha = 1`, `beta = 0` in this first
  version. Other data types, formats (COO/BSR), transpose modes, and `alpha`/
  `beta` are natural extensions on the same JNI surface.
