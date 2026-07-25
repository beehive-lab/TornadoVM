# Streams and concurrency on the CUDA backend

Reference for the `tornadovm-nvidia` skill. Everything here is CUDA-backend behaviour (the PTX backend
has the same options but its own implementation); OpenCL, Metal and SPIR-V ignore these options.

## Stream roles

A plan running with `withIntraPlanConcurrency()` owns up to 6 CUDA streams, all NVTX-named so they show
up as named rows in Nsight:

| label | carries |
|---|---|
| `DEFAULT` | markers, barriers, syncs, CUDA-graph capture/launch, library tasks (cuBLAS & co.) |
| `DATA_TRANSFER_H2D` | host-to-device copies, including per-launch kernel-argument writes |
| `COMPUTE`, `COMPUTE_1`, … | kernel launches, `-Dtornado.cuda.compute.streams` of them (default 4) |
| `DATA_TRANSFER_D2H` | device-to-host copies |

Without `withIntraPlanConcurrency()` everything goes to `DEFAULT` and runs in issue order.

Key files: `CUDADeviceContext` (routing, role-queue tables, COMPUTE pool, staging ring),
`CUDAStreamType`, `CUDAEventPool#serialiseEvents` (wait-list -> `cuStreamWaitEvent`, with same-stream
filtering), `CUDAInstalledCode#submitWithEvents` (argument write + launch), `CUDACommandQueue` (JNI).

## Which stream did an operation go to?

`--printBytecodes` appends the stream to every transfer and launch line:

```bash
tornado --printBytecodes -m tornado.examples/uk.ac.manchester.tornado.examples.streams.MultiStreamOverlap
```

```
bc:  TRANSFER_HOST_TO_DEVICE_ALWAYS  [0x2c5529ab] FloatArray@2c5529ab on [NVIDIA CUDA] -- RTX 4090,
     size=25165840, batchSize=0, offset=0 [event list=-1] [stream=DATA_TRANSFER_H2D]
bc:  LAUNCH  task overlap.u3 - compute on ..., numThreadBatch=0, offset=0 [event list=2] [stream=COMPUTE_3]
```

Backed by `TornadoDevice#getLastQueueLabel(long executionPlanId)`, which is also how tests assert
routing without parsing stdout. Empty string on backends that do not route per operation.

## Kernel-to-stream assignment

Dependency-aware: if every COMPUTE-pool producer in a kernel's dependency list is the same stream, the
kernel is issued on that stream (in-order execution already orders it, so the cross-stream wait is
dropped as same-stream); otherwise it takes the next stream round-robin. The cursor is reset at the
start of each execution, so a given task lands on the same stream run after run - profiles of the same
plan are comparable.

Consequence worth remembering: a chain stays on one stream, independent work spreads out, and a
fan-in kernel (two producers on different streams) goes round-robin and pays real cross-stream waits.

## Ordering: why the launch waits on only one event

A launch's wait list is just the kernel-argument-write event. The argument write carries the task's real
dependencies and shares the H2D stream with the transfers it depends on, so those waits are filtered out
as same-stream and the launch pays exactly one cross-stream wait. Handing the full producer list to the
launch instead costs one `cuStreamWaitEvent` per producer and measurably slows plans built of many short
kernels (measured: 1.30 ms -> 1.75 ms on `TestStreamsPerformance#testSmallKernelConcurrency`). If you
change this area, re-measure that test.

## Options and flags

```java
plan.withIntraPlanConcurrency();            // role streams + DAG ordering, off by default
plan.withIntraPlanConcurrency(8);           // ... with 8 COMPUTE streams
plan.withoutIntraPlanConcurrency();         // force single stream (also overrides the -D default)
plan.withStagedTransfers();                 // chunk large one-shot H2D through pinned slots
plan.withStagedTransfers(16 << 20, 16 << 20, 4);  // minTransferSize, chunkSize, ringDepth
plan.withCUDAGraph();                       // capture + replay (single stream, see below)
```

| flag | default | effect |
|---|---|---|
| `-Dtornado.cuda.compute.streams=N` | 4 | COMPUTE pool size; per-plan API overrides it |
| `-Dtornado.cuda.host.pinning=false` | on | kill switch for host pinning |
| `-Dtornado.staged.transfers=true` | off | staging ring process-wide |
| `-Dtornado.staged.min.size` / `.chunk.size` | 16MB | ring engagement threshold / chunk |
| `-Dtornado.staged.ring.depth` | 4 | pinned slots (>= 2) |
| `-Dtornado.staged.fill.threads` | cores/2, max 8 | host threads filling a slot |
| `-Dtornado.vm.deps=true` | off | dependency events without concurrency (concurrency implies it) |

## Traps

- **A task chain gets nothing.** `IntermediateTornadoGraph#isTaskChain` disables concurrency for graphs
  with no two independent tasks, so `withIntraPlanConcurrency()` silently stays single-stream. Any
  benchmark of this feature needs at least two independent units.
- **`withCUDAGraph()` wins over concurrency.** Capture records one stream, so `isMultiStreamEnabled`
  returns false while capturing and a captured plan runs single-stream. Captured operations also report
  no device-side time to the profiler.
- **Plan options are sticky per `ImmutableTaskGraph`.** A second `TornadoExecutionPlan` built from the
  same snapshot inherits the first plan's concurrency and staging settings; call
  `withoutIntraPlanConcurrency()` / `withoutStagedTransfers()` to be sure of a single-stream baseline.
- **Profiler on serializes the plan.** With `withProfiler(...)` the interpreter waits on each
  operation's event, so overlap disappears. Measure wall clock without the profiler.
- **Staged transfers can be slower.** For warm, pinnable host memory direct wins (16.6 vs 10.4 GB/s on
  an RTX 4090 for 1 GB); the ring pays a host memcpy per byte and exists for large cold mmap'd sources
  where the whole-segment page-lock dominates.
- **Kernels that saturate the device do not overlap.** Pool size only matters for many small kernels.

## Measuring

```bash
# correctness of every dependency shape (chains, diamonds, graph replay, staged ring, library ordering)
tornado-test --ea -V uk.ac.manchester.tornado.unittests.streams.TestCUDAStreams

# single-stream vs concurrent medians, printed not asserted
tornado-test --ea uk.ac.manchester.tornado.unittests.streams.TestStreamsPerformance

# examples: overlap, pool-size sweep, bulk copy + independent kernels, staged vs direct upload
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.streams.ConcurrentKernelsPoolSweep

# timeline + API counters
nsys profile --trace=cuda,nvtx -o run tornado -m tornado.examples/...MultiStreamOverlap
nsys stats --report cuda_gpu_trace --report cuda_api_sum --report nvtx_pushpop_sum \
           --format table,csv --output . run.nsys-rep
```

In `cuda_gpu_trace` the `Strm` column is the stream id; matching it against the NVTX stream names shows
which role each row is. `cuda_api_sum` is where to look for `cuStreamWaitEvent` / `cuCtxSynchronize` /
`cuStreamSynchronize` counts when judging whether a change added or removed synchronisation.

Recorded numbers for this box, and the two optimizations that were measured and *rejected*
(stream-ordered allocation, async staging-slot retirement), are in `docs/perf/cuda-streams/README.md`.
