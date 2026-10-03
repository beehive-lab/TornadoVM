---
name: tornadovm-nccl
description: Use NVIDIA NCCL from TornadoVM (tornado-nccl module) — multi-GPU collectives (all-reduce, broadcast, reduce, all-gather, reduce-scatter), send/recv, grouped operations, NcclPlanGroup, CUDA-graph capture, abort/step timeout, and communicators across several processes. Use when writing, testing, debugging or benchmarking multi-GPU TornadoVM code with NCCL, or when changing the tornado-nccl module. Requires the CUDA backend and libnccl. For CUDA backend basics see `tornadovm-nvidia`; for build/test/commit basics see `tornadovm`.
---

# NCCL with TornadoVM

`tornado-nccl` runs NCCL operations as **library tasks** on TornadoVM device buffers, so a multi-GPU
step (kernel → collective → kernel) stays on the GPUs. Read `tornado-nccl/ARCHITECTURE.md` for the
design and full API, and `tornado-nccl/README.md` for a short tour. This skill is the working recipe.

## The model in four rules

1. **One rank = one `TornadoExecutionPlan` on one GPU.** Build one task graph per local GPU (same
   code, rank-specific data), each plan `withDevice(gpu r)`.
2. **The collective is a `libraryTask`** in every rank's graph, in the same order on every rank:
   `.libraryTask("sum", Nccl::allReduceInPlace, comm, grads, NcclRedOp.SUM)`.
3. **Run the ranks together with `NcclPlanGroup`**. Never call `plan.execute()` rank by rank on
   one thread: the first collective waits for the others forever.
4. **Close in reverse order:** group → plans → communicator. Closing the communicator before plans
   that captured CUDA graphs hangs in `ncclCommDestroy`.

## Template (follow the user's kernel rules)

Kernels use `KernelContext` only, never `@Parallel`. Each kernel bounds-checks explicitly, and every
kernel task gets a `WorkerGrid` with an explicit local size in a `GridScheduler`.

```java
public static void scale(KernelContext ctx, FloatArray a, float f) {
    int id = ctx.globalIdx;
    if (id < a.getSize()) {
        a.set(id, a.get(id) * f);
    }
}

TornadoDevice[] gpus = { TornadoExecutionPlan.getDevice(0, 0), TornadoExecutionPlan.getDevice(0, 1) };
TornadoExecutionPlan[] plans = new TornadoExecutionPlan[gpus.length];
try (NcclCommunicator comm = NcclCommunicator.create(gpus)) {
    try {
        for (int r = 0; r < gpus.length; r++) {
            TaskGraph g = new TaskGraph("rank" + r)
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, data[r])
                    .task("pre", Kernels::scale, new KernelContext(), data[r], 2.0f)
                    .libraryTask("sum", Nccl::allReduceInPlace, comm, data[r], NcclRedOp.SUM)
                    .task("post", Kernels::scale, new KernelContext(), data[r], 0.5f)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, data[r]);
            WorkerGrid w = new WorkerGrid1D(n);
            w.setLocalWork(256, 1, 1);
            GridScheduler s = new GridScheduler();
            s.addWorkerGrid("rank" + r + ".pre", w);     // one entry per kernel task: count them
            s.addWorkerGrid("rank" + r + ".post", w);
            plans[r] = new TornadoExecutionPlan(g.snapshot()).withDevice(gpus[r]).withGridScheduler(s);
        }
        try (NcclPlanGroup ranks = new NcclPlanGroup(plans).withStepTimeout(Duration.ofSeconds(30))) {
            for (int step = 0; step < steps; step++) {
                ranks.execute();
            }
        }
    } finally {
        for (TornadoExecutionPlan p : plans) { if (p != null) p.close(); }   // plans before comm
    }
}
```

**Every kernel task needs its own `WorkerGrid` entry.** A kernel without one runs with a default
grid, which can look like an ordering race between NCCL and the kernels. Before suspecting the
runtime, grep the scheduler entries against the kernel tasks.

## Choosing the operation

| Need | Use |
|---|---|
| Sum/average gradients on every GPU | `allReduceInPlace(comm, buf, SUM/AVG)` |
| Result on one GPU only | `reduce(comm, send, recv, op, root)` |
| Same weights everywhere | `broadcast(comm, buf, root)` |
| Concatenate shards | `allGather(comm, shard, full)` (`full` = size() × `shard`) |
| Reduce then shard | `reduceScatter(comm, full, shard, op)` |
| One-way pipeline stage | `send` on one rank, `recv` on the other |
| Ring shift / a rank both sends and receives | `sendRecv(comm, out, toPeer, in, fromPeer)`. Separate `send` and `recv` tasks deadlock here. |
| All-to-all, multi-direction halo, several collectives in one launch | `NcclGroup.on(comm).send(..).recv(..)...` + `libraryTask("x", Nccl::group, group)` |

Types: `FloatArray`, `DoubleArray`, `HalfFloatArray`, `BFloat16Array`, `IntArray`, `LongArray`,
`Int8Array`, `ByteArray` (bytes are reduced as signed int8). A buffer may appear only once in an
`NcclGroup`.

## Variants

- **CUDA graphs:** call `withCUDAGraph()` on every rank's plan. The first `execute()` captures the
  graph, later steps replay it. This roughly halves the time of small steps; large ones are unchanged.
- **Failure:** if a rank throws, the group aborts the communicators it used and rethrows. An aborted
  communicator (`isAborted()`) is dead: create a new communicator and new plans.
- **Hang without failure** (unmatched send/recv): `withStepTimeout(Duration)` aborts the step and
  throws, naming the unfinished ranks.
- **Several processes:**
  - Rank 0 runs `NcclUniqueId.create()` and publishes `toBase64()`.
  - Every process calls `NcclCommunicator.create(id, worldSize, firstRank, localGpus...)` and runs
    an `NcclPlanGroup` over its own plans.
  - The id creator must stay alive until all ranks join; a helper that exits early gives
    "remote process exited".
  - To try it, use `NcclMultiProcessWorker`: run it with `new` for rank 0, which prints `NCCL-ID <id>`.

## Set-up

```bash
make BACKEND=cuda && source setvars.sh
pip install nvidia-nccl-cu13          # or: apt install libnccl2
export LD_LIBRARY_PATH=$(python -c 'import nvidia.nccl;print(nvidia.nccl.__path__[0])')/lib:$LD_LIBRARY_PATH
```

Without libnccl the tests report `UNSUPPORTED`; check with `NcclLibraryProvider.isAvailable()`.
A single GPU gives a one-rank communicator, which is enough for functional tests. Rank tests need 2+ GPUs.

## Test, benchmark, debug

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNccl
tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNcclMultiProcess
tornado-test -V uk.ac.manchester.tornado.unittests.vm.concurrency.TestConcurrentCudaGraphCapture
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclAllReduce [elements,...] [iterations]
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclSendRecv  [elements,...] [iterations]
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclGroup [buffers] [elements,...] [iterations]
```

- Add `--jvm="-Dtornado.nccl.benchmark.cudaGraph=true"` to a benchmark to run every plan as a graph.
- **Compare against nccl-tests** (`all_reduce_perf`, `sendrecv_perf`) on the same host as the
  control. At large sizes bus bandwidth should match it; if it does not, the host is the problem
  before TornadoVM is.
- **Small-step overhead is mostly the thread handoff.** Tune it with `-Dtornado.nccl.spinWaitMicros`
  (default 1000; 0 parks at once and saves CPU but adds latency). Use `nsys profile --trace=cuda,nvtx`;
  NCCL tasks show as `nvidia/nccl/<function>` NVTX ranges.
- `NCCL_DEBUG=INFO` shows the transport (SHM / P2P / NET) and the rings.
- **compute-sanitizer:** `memcheck` should report 0 memory errors. `synccheck` flags NCCL's own
  Simple and SendRecv kernels: nccl-tests alone reproduces this, so it is not a TornadoVM bug.
  API errors reported by memcheck come from NCCL's internal probing.
- **Run steps repeatedly** (10×) before claiming a fix works. Multi-GPU races and hangs are
  intermittent.

## Changing the module

- Bindings go in `provider/NcclNativeLib` (FFM through `FFMSupport`; the code must compile on JDK 21
  preview and JDK 22+, so no unnamed `_` variables). Dispatch goes in
  `provider/NcclLibraryProvider.dispatch`.
- Factories go in `Nccl`. The communicator is always parameter 0, passed as its `handle()`. Give
  every parameter an `Access` mode, because the runtime orders transfers from them.
- Never cache the stream in the provider: queues belong to the thread that runs the plan.
- Anything that can block in NCCL needs thought about abort: `ncclCommAbort` cannot run on a thread
  that is still capturing, and blocks while an instantiated graph uses the communicator.
- Register new tests in `tornado-assembly/src/bin/tornado-test`, and update `README.md`,
  `ARCHITECTURE.md` and `HYBRID_API_GUIDE.md` §3.8.
