# TornadoVM Hybrid API — NCCL Collectives

NVIDIA [NCCL](https://github.com/NVIDIA/nccl) collectives as TornadoVM library tasks, for running one
task graph per GPU and combining their results without leaving the devices. NCCL works on the
TornadoVM-managed device buffers directly and runs on the execution plan's CUDA stream, so a
collective is ordered with the JIT-compiled kernels around it. The module binds `libnccl` through
`java.lang.foreign`; there is no native module to build.

```java
try (NcclCommunicator comm = NcclCommunicator.create(gpu0, gpu1)) {        // one rank per GPU
    // per rank r: kernel -> all-reduce -> kernel, all on device r
    TaskGraph graph = new TaskGraph("rank" + r)
        .task("grad", MyKernels::gradient, new KernelContext(), weights[r], grads[r])
        .libraryTask("sum", Nccl::allReduceInPlace, comm, grads[r], NcclRedOp.AVG)
        .task("step", MyKernels::update, new KernelContext(), weights[r], grads[r]);
    // ... one TornadoExecutionPlan per rank, withDevice(gpu r) and a GridScheduler ...
    try (NcclPlanGroup ranks = new NcclPlanGroup(plans)) {
        ranks.execute();                                                    // all ranks together
    }
}
```

## Supported operations

| Factory | NCCL call | Semantics |
|---|---|---|
| `allReduce(comm, send, recv, op)` | `ncclAllReduce` | `recv = op(send of every rank)` on every rank |
| `allReduceInPlace(comm, buffer, op)` | `ncclAllReduce` | the same, in place |
| `broadcast(comm, buffer, root)` | `ncclBroadcast` | `buffer` of rank `root` to every rank, in place |
| `reduce(comm, send, recv, op, root)` | `ncclReduce` | `recv = op(send of every rank)` on rank `root` only |
| `allGather(comm, send, recv)` | `ncclAllGather` | every rank receives all `send` buffers in rank order |
| `reduceScatter(comm, send, recv, op)` | `ncclReduceScatter` | reduce, then rank `r` keeps block `r` |

Element types: `FloatArray`, `DoubleArray`, `HalfFloatArray`, `BFloat16Array`, `IntArray`,
`LongArray`, `Int8Array`, `ByteArray`. Reduction ops (`NcclRedOp`): `SUM`, `PROD`, `MAX`, `MIN`,
`AVG`.

## How it fits together

- **`NcclCommunicator`** — created by the application over a list of CUDA devices (rank `i` is
  device `i`), closed by it. Task graphs refer to it by a `long` handle, because every non-primitive
  task argument is treated as data to place on the device; the factories take the communicator and
  do this for you.
- **One plan per rank.** Each rank's task graph adds the same collective and runs on its own device.
  The provider finds the rank that lives on the task's device and enqueues the collective on that
  plan's stream.
- **`NcclPlanGroup`** — a collective completes only once every rank has enqueued it, so the plans
  must execute together. The group gives every plan a thread of its own (always the same one, which
  also keeps it on the same TornadoVM command queue) and returns once all of them have finished.
  Executing the plans one after the other on one thread hangs at the first collective.

## Requirements

- CUDA backend (`make BACKEND=cuda`), one or more NVIDIA GPUs. A single GPU gives a one-rank
  communicator.
- NCCL 2.x: `apt install libnccl2`, or `pip install nvidia-nccl-cu13` and add its `nvidia/nccl/lib`
  directory to `LD_LIBRARY_PATH`. Without it the tests report `UNSUPPORTED`.

## Tests and benchmark

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNccl
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclAllReduce [elements,...] [iterations]
```

The benchmark runs one step of a data-parallel pipeline (kernel, sum across GPUs, kernel) with an
NCCL all-reduce and with the alternative TornadoVM offers without NCCL: copy every rank's buffer to
the host, add them up there, and copy the sum back.

## Not supported yet

- Capture in CUDA graphs.
- Point-to-point `ncclSend`/`ncclRecv` and group calls.
- Communicators across processes (`ncclCommInitRank` with a unique id exchanged by the application).
