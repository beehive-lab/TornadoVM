# tornado-nccl: Architecture and API

This document explains how the NCCL integration is built: the parts of the module, how one step of a
multi-GPU program runs through them, the CUDA backend features it depends on, and the full public
API. For a shorter, example-driven introduction see [README.md](README.md); for the hybrid
library-task API in general see `HYBRID_API_GUIDE.md` §3.8.

## 1. Goal and model

TornadoVM compiles one task graph for one device. NCCL combines data across devices. The module
connects the two without a new execution model:

- **One rank = one execution plan on one CUDA device.** A program with N GPUs builds N task graphs
  (usually from the same code), each with its own `TornadoExecutionPlan` pinned to its device.
- **A collective is a library task.** `libraryTask("sum", Nccl::allReduceInPlace, comm, grads, SUM)`
  sits between ordinary JIT kernels in each rank's graph. NCCL reads and writes the TornadoVM device
  buffers directly and is enqueued on the plan's CUDA stream, so it is ordered with the kernels and
  transfers around it. Data never goes through the host.
- **The ranks execute together.** A collective completes only once every rank has enqueued it, so
  the N plans run concurrently, each on a thread of its own (`NcclPlanGroup`).

```
        application thread
               │  NcclPlanGroup.execute()
     ┌─────────┼──────────────────────────┐
     ▼         ▼                          ▼
 rank thread 0  rank thread 1   ...   rank thread N-1     (fixed threads, spin-then-park)
     │              │                       │
 plan 0 on GPU 0  plan 1 on GPU 1        plan N-1 on GPU N-1
  kernel ─► NCCL ─► kernel   (all on the plan's CUDA stream)
              │
              ▼
   NcclLibraryProvider.dispatch ──► NcclNativeLib (FFM) ──► libnccl.so.2
```

## 2. Components

| Class | Package | Role |
|---|---|---|
| `Nccl` | `nccl` | Factories that describe one rank's call as a `LibraryTaskDescriptor`: collectives, point-to-point and `group`. Checks shapes, types and ranks when the task is added. |
| `NcclCommunicator` | `nccl` | Owns the `ncclComm_t` of this process's ranks. Created by the application, closed by it. Referred to in task graphs by a `long` handle. Can be aborted. |
| `NcclUniqueId` | `nccl` | The 128-byte NCCL id that joins the processes of a multi-process communicator; bytes and Base64 round-trips. |
| `NcclGroup` | `nccl` | Builder for several operations of one communicator, issued together by one `Nccl.group` task. |
| `NcclPlanGroup` | `nccl` | Runs the plans of the local ranks together, one fixed thread per plan. Aborts communicators on a failure or a step timeout. |
| `NcclRedOp` | `nccl` | `SUM`, `PROD`, `MAX`, `MIN`, `AVG`. |
| `NcclLibraryProvider` | `nccl.provider` | The `TornadoLibraryProvider` registered for `nvidia/nccl` (ServiceLoader). Resolves the rank and the stream, and calls NCCL. |
| `NcclNativeLib` | `nccl.provider` | `java.lang.foreign` bindings to `libnccl.so.2`, plus `cudaGetDevice` from `libcudart`. |
| `BenchmarkNcclAllReduce`, `BenchmarkNcclSendRecv`, `BenchmarkNcclGroup`, `NcclMultiProcessWorker` | `nccl.tests` | Runnable benchmarks and the multi-process example. |

The module has no native code to build. If `libnccl` cannot be loaded, `NcclLibraryProvider.isAvailable()`
returns `false`, and the tests report `UNSUPPORTED`.

## 3. How one step runs

### 3.1 Building the graphs

Each `Nccl` factory returns a descriptor holding a function name, the parameters and an access mode
for each parameter. For `allReduceInPlace(comm, buffer, SUM)` the parameters are:

```
[ comm.handle() (long), buffer (READ_WRITE), op code, ncclDataType, element count ]
```

The communicator is passed as its handle, a plain `long`. The reason is that TornadoVM treats every
non-primitive task argument as data to place on the device. The element type comes from the array
class (`FloatArray` → `ncclFloat32`, `ByteArray`/`Int8Array` → `ncclInt8`, and so on). The access
modes tell the TornadoVM runtime which buffers the task reads and writes. That is how it orders
transfers and kernels around the collective, and decides which buffers need copying back.

### 3.2 Context per (device, plan)

The first time a plan reaches an NCCL task, the library registry calls `createContext(device, planId)`
with that device's CUDA context current. The provider asks the CUDA runtime which device that is
(`cudaGetDevice`) and records the ordinal. That is all the context holds. Communicators span
several devices, so they belong to the application, not to a context of one device. NCCL allocates
nothing per call, so there is no `prepare` step.

### 3.3 Dispatch

On each execution the provider:

1. Looks up the communicator from the handle (`NcclCommunicator.fromHandle`), then the `ncclComm_t`
   of the rank on this device (`commForOrdinal`). This fails clearly if the communicator was closed
   or aborted, or does not include the device.
2. Gets the plan's CUDA stream (`TornadoNativeStreamSupport.getNativeStream(planId)`). It does this
   on every call, never caching it, because TornadoVM command queues belong to the thread that runs
   the plan.
3. Passes the raw device pointers of the TornadoVM buffers to the NCCL call on that stream.

`sendRecv` and `group` wrap their calls in `ncclGroupStart`/`ncclGroupEnd`. `ncclGroupEnd` runs in a
`finally` block, so a failure part-way never leaves the thread in group mode.

### 3.4 Group encoding

`Nccl.group(group)` flattens the group into one parameter list:

```
[ handle, number of operations,
  kind, a, b, dataType, count, buffer[, buffer],     ← operation 1
  kind, a, b, dataType, count, buffer[, buffer],     ← operation 2
  ... ]
```

`a` is the reduction op, the root or the peer. `b` is the root of a reduce. `NcclGroup.Kind` says
how many buffers each kind takes, so the provider can walk the list. A buffer may appear only once
per group, because one parameter can carry only one access mode.

A design with "group start" and "group end" marker tasks around ordinary NCCL tasks was rejected:
a JIT kernel placed between the markers would run before the deferred NCCL operations it appears
to follow.

### 3.5 Running the ranks: `NcclPlanGroup`

Running the plans one after another on one thread would hang at the first collective. The group
therefore starts one daemon thread per plan and keeps it for the group's lifetime. A fixed thread
also keeps each plan on the same TornadoVM command queue from one step to the next.

`execute()` increments a step counter and unparks the rank threads. Each thread runs its plan once
and decrements a counter of ranks still running. The last one unparks the caller. Threads waiting
for the next step, and the caller waiting for the ranks, first spin with `Thread.onSpinWait()` for
up to `tornado.nccl.spinWaitMicros` (default 1000 µs) and then park.

An `ExecutorService` with futures cost two thread wake-ups in a row on every step, and the ranks
started the collective at different times. On a two-socket host that doubled the time of a 4 MiB
exchange (step 924 → 352 µs after the change).

## 4. Failure handling

### 4.1 A rank fails

If one rank's plan throws, the other ranks would wait for it inside NCCL forever. NcclPlanGroup
handles this as follows:

- **Tracking.** Every rank thread records the communicators its NCCL tasks use, in a thread-local
  set that `commForOrdinal` fills.
- **Signalling.** The first rank to fail records itself and wakes the caller.
- **Aborting.** The caller aborts every communicator that was used, then aborts again every
  millisecond until all ranks have returned. A rank that reaches a communicator late is caught by
  one of the later aborts.
- **Reporting.** `execute()` throws the first rank's failure as the cause.

`NcclCommunicator.abort()` is asynchronous. It marks the communicator aborted and runs
`ncclCommAbort` on a daemon thread, for two reasons:

- `ncclCommAbort` blocks until no instantiated CUDA graph refers to the communicator. That only
  happens when the plans that captured it are closed.
- It cannot run on a thread whose stream is still capturing. The failing rank thread may have
  failed in the middle of a capture, which is why the caller aborts rather than the failing thread.

An aborted communicator cannot be used again. The application creates a new communicator and new
plans; NCCL itself keeps working in the process.

### 4.2 A step hangs

A step can hang without any rank failing, for example on a `send` that no rank receives.
`withStepTimeout(Duration)` bounds each step:

1. When the time runs out, the group records the ranks that have not finished and aborts as in §4.1.
2. If they return, `execute()` throws "did not finish within … ranks still running: [..]".
3. If they still have not returned after a second timeout period (stuck in something NCCL cannot
   interrupt), `execute()` throws anyway. The group is then marked unusable and refuses further
   steps.

## 5. CUDA graphs

Calling `withCUDAGraph()` on every rank's plan captures the NCCL calls together with the kernels.
The provider needs nothing special for this:

- NCCL relaxes the capture mode itself when it sets up connections lazily inside a capture.
- The calls take only device pointers and the stream, so a replay reads the new data of the same
  buffers.

Capturing small steps roughly halves their time; large transfers are unchanged.

Close the plans before the communicator. `ncclCommDestroy` waits for every instantiated graph that
still uses the communicator, so closing in the other order blocks.

## 6. Several processes

`NcclCommunicator.create(id, worldSize, firstRank, devices...)` creates this process's ranks of a
communicator that spans processes, and possibly machines:

- **Joining.** Each local device calls `ncclCommInitRank` with the 128-byte `ncclUniqueId` passed by
  value as a `StructLayout`. Several local devices are joined inside one NCCL group.
- **Blocking.** The call returns once all `worldSize` ranks have joined.
- **Rank numbering.** `size()` is the world size, so ranks and peers are checked against the whole
  job. `localRanks()` lists this process's ranks.
- **Plans.** Each process runs an `NcclPlanGroup` over the plans of its own ranks.

`NcclUniqueId.create()` starts NCCL's bootstrap listener in the creating process. That process must
stay alive until every rank has joined; this is natural when it is itself rank 0. A helper process
that creates the id and exits makes the others fail with "remote process exited".

## 7. What it needs from the CUDA backend

The module relies on these features of the CUDA backend. They are general CUDA backend behaviour,
not part of the module, and each one matters as soon as plans run on several GPUs at once:

| Feature | Why NCCL needs it |
|---|---|
| **One primary context per GPU.** Each device retains its own primary context (`cuDevicePrimaryCtxRetain`) and makes it current on the calling thread before using it. Before, all GPUs shared GPU 0's context. | NCCL identifies a rank's device through the current context. With a shared context, every rank looked like GPU 0. |
| **`TornadoNativeStreamSupport.makeNativeContextCurrent()`**, which the library registry calls before `createContext` | The provider and `NcclCommunicator` read the CUDA runtime ordinal of a TornadoVM device. |
| **Thread-local CUDA-graph capture** (`CU_STREAM_CAPTURE_MODE_THREAD_LOCAL`) | With global mode, one rank's capture rejected the other rank's `cuGraphInstantiate`/`cuMemAlloc` (`CUDA_ERROR_STREAM_CAPTURE_UNSUPPORTED`), and the group hung. |
| **Abandoning a capture that fails part-way.** The stream capture is ended, the partial graph dropped, and deferred kernel-argument writes are kept per plan. | After a rank failed during a capture, the next capture on that GPU broke on stale state. |

Any application that runs plans on several GPUs, or captures graphs from several threads, needs
them too.

## 8. API reference

### `Nccl` (library-task factories)

| Factory | NCCL call | Semantics | Shape rule |
|---|---|---|---|
| `allReduce(comm, send, recv, op)` | `ncclAllReduce` | `recv = op(send of every rank)` on every rank | same type and size |
| `allReduceInPlace(comm, buffer, op)` | `ncclAllReduce` | in place | — |
| `broadcast(comm, buffer, root)` | `ncclBroadcast` | rank `root`'s buffer to all, in place | — |
| `reduce(comm, send, recv, op, root)` | `ncclReduce` | result on `root` only | same type and size |
| `allGather(comm, send, recv)` | `ncclAllGather` | concatenation in rank order | `recv` = `size()` × `send` |
| `reduceScatter(comm, send, recv, op)` | `ncclReduceScatter` | rank `r` keeps block `r` | `send` = `size()` × `recv` |
| `send(comm, buffer, peer)` | `ncclSend` | to `peer`, which adds a matching `recv` | same type and size on both sides |
| `recv(comm, buffer, peer)` | `ncclRecv` | from `peer` | same type and size on both sides |
| `sendRecv(comm, send, toPeer, recv, fromPeer)` | send + recv in one group | ring shifts, halo exchanges | same type and size |
| `group(NcclGroup)` | any of the above in one group | multi-peer exchanges, fused collectives | per operation |

Element types: `FloatArray`, `DoubleArray`, `HalfFloatArray`, `BFloat16Array`, `IntArray`,
`LongArray`, `Int8Array`, `ByteArray`. Every rank adds the same collective, in the same order.

### `NcclCommunicator`

| Member | Description |
|---|---|
| `static create(TornadoDevice... devices)` | All ranks in this process; rank `i` is `devices[i]` (`ncclCommInitAll`). The devices must be distinct CUDA GPUs. |
| `static create(NcclUniqueId id, int worldSize, int firstRank, TornadoDevice... devices)` | This process's ranks `firstRank…` of a multi-process communicator; blocks until all ranks join. |
| `long handle()` | The value task graphs use. |
| `int size()` | World size. |
| `int[] localRanks()` | Global ranks owned by this process. |
| `static fromHandle(long)` | Looks up a live communicator. |
| `void abort()` | Asynchronous `ncclCommAbort`; the communicator cannot be used afterwards. |
| `boolean isAborted()` | |
| `void close()` | `ncclCommDestroy`. Close plans that captured CUDA graphs first. |

### `NcclUniqueId`

| Member | Description |
|---|---|
| `static create()` | `ncclGetUniqueId`; do it in one process, which must stay alive until all ranks join. |
| `byte[] toBytes()` / `static fromBytes(byte[])` | 128-byte round-trip. |
| `String toBase64()` / `static fromBase64(String)` | Text round-trip for command lines and environment variables. |

### `NcclGroup`

`NcclGroup.on(comm)` followed by any of `allReduce`, `allReduceInPlace`, `broadcast`, `reduce`,
`allGather`, `reduceScatter`, `send` and `recv` (the same arguments as the `Nccl` factories, minus
the communicator), chained. `size()` returns the number of operations. The group's contents are read
when the task is added to a task graph.

### `NcclPlanGroup`

| Member | Description |
|---|---|
| `new NcclPlanGroup(TornadoExecutionPlan... plans)` | One fixed thread per plan (one plan per local rank). |
| `withStepTimeout(Duration)` | Bounds every step; opt-in. |
| `TornadoExecutionResult[] execute()` | Runs every plan once, concurrently. Throws after aborting the used communicators on a failure or timeout. Do not call it from several threads at once. |
| `TornadoExecutionResult[] execute(RankStep step)` | As `execute()`, with each rank running `step.run(rank, plan)` on its own thread instead of the whole plan: for instance `plan.withGraph(i).execute()` over the graphs of one program in a plan that holds several. |
| `close()` | Stops the threads; does not close the plans. |

### Properties

| Property | Default | Effect |
|---|---|---|
| `tornado.nccl.spinWaitMicros` | `1000` | How long rank threads and the caller spin before parking. `0` parks at once. |
| `tornado.nccl.benchmark.cudaGraph` | `false` | Benchmarks only: run every plan as a CUDA graph. |

NCCL's own environment variables (`NCCL_DEBUG`, `NCCL_P2P_DISABLE`, `NCCL_SOCKET_IFNAME`, …) apply
unchanged.

## 9. Lifecycle and ordering rules

1. Create the communicator, which needs the devices.
2. Build one task graph and one plan per local rank, using `withDevice` and a `GridScheduler` for
   the kernels.
3. Create an `NcclPlanGroup` over those plans and call `execute()` once per step.
4. Shut down in this order: close the group, then the plans, then the communicator.

Never run a rank's plan outside the group while other ranks wait in the same collective.

## 10. Limits

- CUDA backend only, Linux only (the module loads `libnccl.so.2`).
- One process may own several ranks, but one rank per GPU: two ranks on the same device are
  rejected.
- Tested on one host with two GPUs (shared-memory transport, no P2P), in one and in two processes.
  Multi-machine and NVLink set-ups use the same code but have not been measured.
