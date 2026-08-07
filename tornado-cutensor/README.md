# tornado-cutensor

NVIDIA **cuTENSOR** tensor contractions exposed as TornadoVM **library tasks** —
native GPU calls that ride the standard TornadoVM task-graph pipeline
(device-buffer reuse, CUDA-graph capture, the profiler) and interleave with
JIT-compiled `@Parallel`/`KernelContext` tasks on the same CUDA stream.

Provider id: **`nvidia/cutensor`**. Same hybrid-API mechanism as
`tornado-cublas`/`tornado-cudnn` (see `docs/source/hybrid-api.rst`).

## What is a contraction?

A contraction is an einsum-style tensor product: each operand's dimensions carry
integer **mode** labels, and modes shared by `A` and `B` but absent from `C` are
summed over. All tensors are **row-major** (last index contiguous), matching the
TornadoVM native array layout.

| Factory | Operation |
|---|---|
| `Cutensor.cutensorContraction(m,n,k,a,b,c)` | `C[m,n] = sum_k A[m,k] * B[k,n]` (matmul) |
| `Cutensor.cutensorContraction2(i,j,k,l,a,b,c)` | `C[i,j] = sum_{k,l} A[i,k,l] * B[k,l,j]` |

The two-mode contraction is the differentiator: a genuine tensor contraction
over several shared modes, which the cuBLAS GEMM API cannot express in a single
call. All operands are `FloatArray` (FP32).

## Usage

```java
TaskGraph graph = new TaskGraph("net")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
    .task("pre", MyKernels::preprocess, a)                                  // JIT
    .libraryTask("contract", Cutensor::cutensorContraction2, i, j, k, l, a, b, c)  // cuTENSOR
    .task("post", MyKernels::postprocess, c)                               // JIT
    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withCUDAGraph();   // optional: capture the whole pipeline
    plan.execute();
}
```

The provider binds the cuTENSOR handle to the plan's CUstream and sizes its
device workspace (typically ~32 MiB) in `prepare()`, before CUDA-graph capture
begins, so `libraryTask` calls are capture-safe.

## Build & run

cuTENSOR is a **separate NVIDIA library** (not part of the CUDA toolkit) and
requires **CUDA 12+**. Install it and point the build at it with `CUTENSOR_ROOT`:

```bash
# one option: the pip wheel ships the headers + libcutensor.so
python3 -m pip install cutensor-cu12       # or download from developer.nvidia.com/cutensor
export CUTENSOR_ROOT=/path/to/cutensor     # dir with include/ and lib/
make BACKEND=cuda
```

`tornado-drivers/cutensor-jni` locates cuTENSOR via `CUTENSOR_ROOT`,
`~/.local/cutensor`, or `/usr/local/cutensor`, and embeds its library directory
as an rpath so `libcutensor.so` resolves at load time. If cuTENSOR is not found,
the native library is skipped (the build still succeeds) and the CUTENSOR tasks
report `UNSUPPORTED` at runtime.

Tests and benchmark:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.cutensor.TestCutensor
tornado -m tornado.cutensor/uk.ac.manchester.tornado.cutensor.tests.BenchmarkCutensor --params="1024 100"
```

## Note

`cutensorCreatePlan` performs heavy kernel-selection work that overflows the
default JVM thread stack; the JNI runs it on a dedicated large-stack thread, so
no `-Xss`/`ulimit` tuning is required.
