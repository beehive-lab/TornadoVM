# TornadoVM Hybrid API — NCCL Collectives and Point-to-Point

NVIDIA [NCCL](https://github.com/NVIDIA/nccl) collectives and point-to-point transfers as TornadoVM
library tasks, for running one task graph per GPU and combining or exchanging their data without
leaving the devices. NCCL works on the TornadoVM-managed device buffers directly and runs on the
execution plan's CUDA stream, so every transfer is ordered with the JIT-compiled kernels around it.
The module binds `libnccl` through `java.lang.foreign`; there is no native module to build.

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
| `send(comm, buffer, peer)` | `ncclSend` | send `buffer` to rank `peer`, which adds a matching `recv` |
| `recv(comm, buffer, peer)` | `ncclRecv` | receive `buffer` from rank `peer`, which adds a matching `send` |
| `sendRecv(comm, send, toPeer, recv, fromPeer)` | `ncclSend` + `ncclRecv` in one group | send and receive in one step |

Element types: `FloatArray`, `DoubleArray`, `HalfFloatArray`, `BFloat16Array`, `IntArray`,
`LongArray`, `Int8Array`, `ByteArray`. Reduction ops (`NcclRedOp`): `SUM`, `PROD`, `MAX`, `MIN`,
`AVG`.

For point-to-point, sender and receiver use the same type and number of elements. `send`/`recv`
suit a one-way pipeline; a rank that both sends and receives in the same step (ring shift, halo
exchange) uses `sendRecv`, because two ranks that each send to the other first, as separate tasks,
would both wait in their send forever. A rank may send to and receive from itself with `sendRecv`.

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

## Several processes

A communicator can span several processes, on one or more machines:

```java
// rank 0: create the id and hand it to the other processes (socket, MPI, file, environment...)
NcclUniqueId id = NcclUniqueId.create();
publish(id.toBase64());
NcclCommunicator comm = NcclCommunicator.create(id, worldSize, 0, TornadoExecutionPlan.getDevice(0, 0));

// rank r, in its own process
NcclUniqueId id = NcclUniqueId.fromBase64(received);
NcclCommunicator comm = NcclCommunicator.create(id, worldSize, r, TornadoExecutionPlan.getDevice(0, localGpu));
```

`create` blocks until every rank has joined. A process may own several ranks:
`create(id, worldSize, firstRank, gpu0, gpu1)` gives its GPUs ranks `firstRank` and `firstRank + 1`.
Each process runs an `NcclPlanGroup` over the plans of its own ranks; `size()` is the world size,
so collectives and peers are checked against the whole job. The process that created the id must
keep running until all ranks have joined, because NCCL's bootstrap listener lives in it.

`NcclMultiProcessWorker` is a runnable example: start rank 0 with `new` (it prints the id), then
every other rank with that id.

```bash
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker new 2 0 0      # prints NCCL-ID <id>
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.NcclMultiProcessWorker <id> 2 1 1
```

## When a rank fails

If the plan of one rank throws, the other ranks would wait for it inside NCCL forever. `NcclPlanGroup`
aborts the communicators its ranks have used (`NcclCommunicator.abort()`): the waiting ranks return,
and `execute()` throws the failure of the rank that failed first. An aborted communicator cannot be
used again (`isAborted()` tells), so create a new communicator and new plans to carry on.

A step can also hang without any rank failing, for example on a `send` that no rank receives.
`withStepTimeout(Duration)` bounds every step of the group:

```java
try (NcclPlanGroup ranks = new NcclPlanGroup(plans).withStepTimeout(Duration.ofSeconds(30))) {
    ranks.execute();   // throws, naming the ranks still running, if the step takes longer
}
```

When the timeout runs out, the communicators are aborted as for a failed rank. If a rank does not
return even then (stuck in something NCCL cannot interrupt), `execute()` still throws after a second
timeout period and the group refuses further steps.

## CUDA graphs

NCCL tasks can be captured into CUDA graphs. Call `withCUDAGraph()` on the plan of every rank; the
first `NcclPlanGroup.execute()` captures each rank's graph and runs it, and later steps replay it
with the new host input. Close the plans before closing the communicator, because the captured
graphs refer to it.

## Requirements

- CUDA backend (`make BACKEND=cuda`), one or more NVIDIA GPUs. A single GPU gives a one-rank
  communicator.
- NCCL 2.x: `apt install libnccl2`, or `pip install nvidia-nccl-cu13` and add its `nvidia/nccl/lib`
  directory to `LD_LIBRARY_PATH`. Without it the tests report `UNSUPPORTED`.

## Tests and benchmark

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.nccl.TestNccl
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclAllReduce [elements,...] [iterations]
tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNcclSendRecv [elements,...] [iterations]
```

Add `--jvm="-Dtornado.nccl.benchmark.cudaGraph=true"` to run every plan of a benchmark as a CUDA graph.

The benchmarks run one step of a multi-GPU pipeline (kernel, communication, kernel) with NCCL and
with the alternative TornadoVM offers without it: copy every rank's buffer to the host, combine or
hand them on there, and copy the result back. `BenchmarkNcclAllReduce` sums across the GPUs;
`BenchmarkNcclSendRecv` passes every buffer to the next GPU in a ring.

## Not supported yet

- Group calls spanning several tasks (only `sendRecv` groups its send and receive).
