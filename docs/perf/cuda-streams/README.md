# CUDA stream measurements

Data behind the stream-usage changes in this branch. Everything here was collected on one box, so
treat the absolute numbers as box-specific and the before/after pairs as the actual result.

```
GPU            NVIDIA GeForce RTX 4090
Driver         565.57.01
CUDA toolkit   11.5 (nvcc V11.5.119)
Nsight Systems 2024.5.1.113
JDK            21.0.2
baseline       develop @ 06616ddd4
```

Reproduce:

```bash
make BACKEND=cuda && source setvars.sh
tornado-test --ea uk.ac.manchester.tornado.unittests.streams.TestStreamsPerformance
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.MultiStreamOverlap
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.ConcurrentKernelsPoolSweep
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.StagedWeightUpload

nsys profile --trace=cuda,nvtx -o run <the command above>
nsys stats --report cuda_gpu_trace --report cuda_api_sum --format csv --output . run.nsys-rep
```

## 1. TestStreamsPerformance, develop vs this branch

Median of 10 timed executions, three repetitions of the whole test per column. The concurrent column
is what changes; the single-stream column is a control.

| scenario | develop, single | develop, concurrent | branch, single | branch, concurrent |
|---|---|---|---|---|
| transfer/compute overlap | 32.33 ms | 28.89 ms | 32.45 ms | 28.96 ms |
| compute concurrency, large kernels | 43.55 ms | 37.45 ms | 43.73 ms | 37.07 ms |
| compute concurrency, small kernels | 4.28–4.30 ms | 1.27 / 1.30 / 1.29 ms | 4.27–4.29 ms | 1.32 / 1.33 / 1.34 ms |

Read this as parity: the stream-selection change is not a throughput win on these microbenchmarks, and
the ~3% on the small-kernel row is at the edge of this box's run-to-run spread (develop itself produced
1.16 ms once and 1.27–1.30 ms on three later repetitions).

An earlier version of the change *did* regress this row to 1.68–1.79 ms, and was discarded. It moved
kernel-argument writes to their own stream and handed the launch its full producer list. That costs one
`cuStreamWaitEvent` per producer, because the argument write no longer shares a stream with the
transfers it depends on, so `CUDAEventPool#serialiseEvents` can no longer drop those waits as
same-stream. The shipped version keeps the cheap ordering (one cross-stream wait per launch) and uses
the dependency list only to choose which COMPUTE stream a kernel goes to.

## 2. Stream placement (the change that is actually observable)

`chains_after_cuda_gpu_trace.csv` / `chains_after_cuda_api_sum.csv` -
`TestCUDAStreams#testIndependentChainsUnderConcurrency`, two independent two-kernel chains:

```
  start      0.0 us  dur 1.2 us  stream 16  scale     <- chain a, kernel 1
  start    199.6 us  dur 1.2 us  stream 16  scale     <- chain a, kernel 2 (same stream)
  start    395.0 us  dur 1.2 us  stream 17  scale     <- chain b, kernel 1
  start    590.5 us  dur 1.2 us  stream 17  scale     <- chain b, kernel 2 (same stream)
```

The same thing from `--printBytecodes`, stable across all three executions:

```
task chains.a1 - scale [stream=COMPUTE]
task chains.a2 - scale [stream=COMPUTE]
task chains.b1 - scale [stream=COMPUTE_1]
task chains.b2 - scale [stream=COMPUTE_1]
```

Before this branch the assignment was `cursor.getAndIncrement() % poolSize` with no per-execution
reset, so a chain was spread across streams and the mapping rotated with the number of kernels issued
so far - two profiles of the same plan did not agree.

`manyunits_after_cuda_gpu_trace_window.csv` shows the role split for 8 independent units: bulk H2D on
one stream, four COMPUTE streams, D2H on its own stream.

## 3. Where the time actually goes

`perf_after_cuda_api_sum.csv`, whole `TestStreamsPerformance` run:

| API | calls | total | note |
|---|---|---|---|
| `cuStreamSynchronize` | 654 | 1760.84 ms | waiting for real GPU work at plan end |
| `cuMemHostRegister_v2` | 202 | 128.51 ms | page-locking transfer sources |
| `cuMemHostUnregister` | 202 | 63.83 ms | driver page-unlock work |
| `cuMemFree_v2` | 202 | 61.47 ms | driver unmap work |
| `cuCtxSynchronize` | 404 | **0.20 ms** | the explicit syncs before free/unregister |
| `cuStreamWaitEvent` | 2652 | 0.59 ms | cross-stream ordering |
| `cuEventCreate` + `cuEventRecord` + `cuEventDestroy_v2` | 15460 | 2.86 ms | event bookkeeping |

This is why stream-ordered allocation (`cuMemAllocAsync` + `cuMemPool*`) is *not* part of this branch.
The stated motivation was removing the device-wide `cuCtxSynchronize` that `cuMemFree` and
`cuMemHostUnregister` are guarded by - and those calls total 0.2 ms, 0.5 µs each, because the device is
already idle when buffers are freed. The 125 ms in free plus unregister is the driver's own unmap and
page-unlock work at plan teardown, not the guard. A memory pool would target that teardown cost, which
is a plan-churn cost rather than a steady-state one, at the price of taking on stream-ordered
allocation lifetimes on the critical path. See the PR description for the design if it is wanted.

## 4. Staged versus direct upload

1 GB of weights (8 tensors x 128 MB), `FIRST_EXECUTION`, warm host memory, upload time attributed as
`first execute - steady-state execute`:

| path | upload | effective |
|---|---|---|
| direct (whole-segment pin) | 63.2–64.7 ms | 16.6–17.0 GB/s |
| staged, 16 MB x 4 pinned slots | 103.6 ms | 10.4 GB/s |

Direct wins here, and that is expected: for host memory that is already faulted in, the staging ring
adds a host memcpy of every byte, while direct pins once and lets the DMA engine read in place. The
ring exists for the opposite case - a large, cold, mmap'd source, where the whole-segment page-lock has
to fault in every page before the first byte moves (the case PR #936 measured). This is also why making
the ring's slot recycling asynchronous is not in this branch: the ring's cost here is the host copy,
not the `cuEventSynchronize` between slots.

## 5. Regression check

`tornado-test --quickPass`, same box, same JDK:

| | classes with failures | failing tests |
|---|---|---|
| develop @ 06616ddd4 | 22 | 86 |
| this branch | 22 | 87 |

The single difference is `compute.MMwithBytes`, which is flaky on both: it failed 3 of 10 runs on
develop and 3 of 12 on the branch (`Output contains NaN`). The pre-existing 86 failures are unrelated
CUDA-backend gaps (vector types, prebuilt kernels, virtual layer, Metal-only simdgroup tests).
