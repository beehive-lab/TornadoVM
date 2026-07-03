# Design: First-Class CUDA Stream Support in TornadoVM

**Status:** Proposal · **Scope:** PTX and CUDA (`cuda2`) backends; OpenCL command-queue analogue noted where relevant · **Supersedes:** the thread-keyed stream WIP (PR #800)

---

## 1. Motivation & goals

Today every device operation in TornadoVM is issued to a **single stream per (device, host-thread)** and the stream is *synchronised after each transfer*. As a result:

- Host→device (H2D) copies, kernel execution, and device→host (D2H) copies run **strictly serially**, even though the hardware has independent copy engines and compute SMs that can run concurrently.
- Two independent `TornadoExecutionPlan`s cannot make progress on the GPU at the same time unless the user manually drives them from separate host threads (and even then they collide on the thread-keyed table).

This document specifies how streams should be modelled so that the following become first-class, composable capabilities:

| # | Goal | Precise name used here | Meaning |
|---|---|---|---|
| 1 | **Inter-ExecutionPlan** | **Plan-level concurrent execution** | Run multiple independent `ExecutionPlan` executions concurrently on one GPU, automatically scheduled across a bounded stream pool. This includes request-level concurrency and the cuda-oxide-style "multiple independent batches/pipelines" case. |
| 2 | **Intra-ExecutionPlan** | **Within-plan pipelining** | Overlap eligible work *inside one* `ExecutionPlan`: independent bytecode/DAG branches, H2D/compute/D2H stages, and optional role-stream execution. |
| 3 | llama.cpp "ring of 4" | **Staged transfer pipelining** | Overlap *disk read* with *H2D* using a ring of pinned staging buffers. |

The crucial distinction, learned from cuda-oxide, is that there are **two useful stream-placement modes**:

1. A **dependent pipeline** (`H2D -> kernel -> D2H`) should usually stay on **one stream**. Stream order gives correct sequencing with no cross-stream event overhead.
2. **Independent pipelines** should be scheduled on **different streams**. This is the killer feature: four independent batches or four independent requests can overlap automatically if the GPU has spare copy engines/SMs.

TornadoVM therefore needs both:

- an **inter-plan scheduler** that treats each `ExecutionPlan` execution as a stream-agnostic operation and assigns it to a stream/stream-set from a pool;
- an **intra-plan scheduler** that can place independent work already visible in a plan's bytecode/DAG. The existing `withBatch()` implementation is useful as prior art for offset/chunk metadata, but it is not a target for refactoring in this design.

### Non-goals
- Multi-GPU scheduling (a stream belongs to exactly one device/context).
- Changing the kernel programming model or the task-graph IR.
- Replacing the existing event/dependency model — we **build on it** (see §3).

---

## 2. Background: how execution works today

### 2.1 The bytecode already encodes a dependency DAG
The interpreter (`TornadoVMInterpreter.execute`) walks a bytecode stream whose data-movement and launch ops **already carry explicit dependencies**:

```
TRANSFER_HOST_TO_DEVICE_ONCE   objectIndex, eventId, offset, sizeBatch   (+ waitList = events[eventId])
TRANSFER_HOST_TO_DEVICE_ALWAYS ...
LAUNCH                         callWrapperIndex, taskIndex, numArgs, eventId, ...
TRANSFER_DEVICE_TO_HOST_ALWAYS ...
ADD_DEPENDENCY / BARRIER       eventList
```

Each op (a) **produces** an event (`eventId`) and (b) **waits on** a `waitList` of upstream event ids, gated by the `useDependencies` flag. **This is exactly the information needed to drive multiple streams correctly** — the DAG already exists; today it is merely *flattened onto one ordered stream* so the dependencies are satisfied implicitly by stream order.

### 2.2 The current stream model (what PR #800 / `cuda2` do)
`CUDACommandQueueTable` (and the PTX `PTXStreamTable`) map:

```
device → ConcurrentHashMap<hostThreadId, single CUDACommandQueue/CUstream>
```

i.e. **one stream per host thread**. Transfers call `cuMemcpyHtoDAsync` then **synchronise the stream immediately** (`CUDACommandQueue.cpp`), so async is effectively negated.

### 2.3 Why the WIP design is insufficient
1. **Wrong axis of concurrency.** Binding a stream to a *host thread* cannot express automatic plan scheduling: a single Java thread should be able to submit several independent plan executions and have TornadoVM place them on different streams. The WIP only accidentally enables overlap if the user creates host threads, conflating "concurrency" with "host threading".
2. **Synchronise-after-every-copy** defeats async entirely.
3. **Pageable source.** H2D copies from the (mmap'd / heap) `MemorySegment` directly; without pinned memory the driver falls back to a synchronous staging copy → no true overlap and reduced bandwidth (this also blocks Goal 3).
4. **PTX-only**, with graph-capture/EventRegistry races already being patched ad-hoc.
5. **No isolation between plans** sharing a device context → Goal 1 is unsafe.

### 2.4 cuda-oxide lessons to import
cuda-oxide's async runtime provides the closest analogue for the desired TornadoVM behavior:

- `DeviceOperation` is **lazy and stream-agnostic**. The concrete stream is chosen only when the operation is scheduled.
- `StreamPoolRoundRobin` owns a fixed pool of non-blocking streams (four by default) and chooses the next stream with an atomic counter.
- A dependent pipeline built with `and_then` runs on **one chosen stream**, preserving stage order cheaply.
- Independent pipelines are spawned as independent futures; each is scheduled on a different stream by the policy, so batches overlap automatically.
- Completion does not occupy a host thread: a `cuLaunchHostFunc` callback wakes the future when the GPU reaches the callback point.
- Shared immutable device data is represented with shared ownership (`Arc<DeviceBox>`); TornadoVM needs the equivalent notion for `FIRST_EXECUTION` read-only buffers so concurrent plans share weights without false dependencies.

For TornadoVM, the target is not to copy Rust/Tokio, but to copy the runtime contract: **build work without binding it to a stream; bind it at submit time; keep dependent chains ordered on one stream; schedule independent chains across a pool; complete asynchronously without blocking Java worker threads**.

---

## 3. Design principles

1. **Stream is a first-class device resource, decoupled from host threads.** Concurrency is expressed by *which stream* an operation targets, never by *which thread* issued it.
2. **Reuse the existing event DAG.** Cross-stream correctness is realised by translating each op's `waitList` into device events (`cuEventRecord`/`cuStreamWaitEvent`), not by stream ordering alone.
3. **Use the cheapest correct placement.** Dependent `H2D -> compute -> D2H` chains run on one stream by default. Cross-stream events are used only when the DAG really forks/joins or when role-stream pipelining is explicitly enabled.
4. **Orthogonality by construction.** Inter-plan scheduling assigns independent plan executions to streams/stream-sets. Intra-plan scheduling assigns independent DAG branches or explicit runtime pipeline stages to streams. These two axes compose without special-casing.
5. **Async by default, synchronise only at well-defined boundaries** (plan completion, explicit barrier, or D2H result consumption).
6. **Backend-neutral abstraction.** The runtime speaks `Stream`/`Event`; PTX, CUDA, and OpenCL (queues+events) provide implementations.

---

## 4. Core abstraction

```
TornadoDeviceContext
 └── StreamPool (per device, per context)
       ├── schedule(operation)      -> Stream         // round-robin / policy chosen
       ├── acquire(role)            -> Stream         // role ∈ {H2D, COMPUTE, D2H, GENERIC}
       ├── acquire(n)               -> Stream[]       // for role streams / staged-transfer ring
       └── release(Stream)

Stream            // wraps CUstream / CUDA command queue / cl_command_queue
 ├── enqueueCopyH2D(dst, src, bytes, deps[]) -> Event
 ├── enqueueKernel(kernel, grid, deps[])     -> Event
 ├── enqueueCopyD2H(dst, src, bytes, deps[]) -> Event
 └── waitFor(Event)                          // cuStreamWaitEvent

Event             // wraps CUevent / cl_event ; carries producing-stream identity
```

The **`StreamPool`** replaces the thread-keyed table. Streams are pooled and reused; they are *assigned*, not *thread-local*. A `CUcontext` is made current on whichever worker thread issues to its streams (`cuCtxSetCurrent` is cheap and per-thread), removing the thread coupling.

A new per-execution object carries the binding:

```
ExecutionStreamSet {                 // created per execution
    Stream primary;                  // default stream for a dependent chain
    Optional<Stream> h2d, compute, d2h; // only for explicit role-stream mode
    EventRegistry events;            // plan-private; replaces the global EventRegistry
}
```

Making the `EventRegistry` **plan-private** is what makes Goal 1 safe (today a global registry is the source of the capture races PR #800 keeps patching).

A second object is needed at the runtime/API boundary:

```
PlanExecution {
    ImmutableTaskGraph graph;
    ExecutionPlanState state;
    Completion completion;      // Java Future/CompletableFuture style handle
    StreamAssignment assignment;
}
```

`PlanExecution` is TornadoVM's analogue of cuda-oxide's `DeviceOperation` + `DeviceFuture`: it is stream-agnostic until submitted, then bound to a stream or stream-set and completed by terminal device events/callbacks.

---

## 5. Goal 1 — Inter-ExecutionPlan concurrent execution

### 5.1 Mechanism
Each `ExecutionPlan` execution is submitted as a **lazy, stream-agnostic plan operation**. At submit time, a per-device scheduling policy assigns it to a stream or stream-set from a bounded pool:

```java
TornadoExecutionResult r0 = plan0.executeAsync();  // stream 0
TornadoExecutionResult r1 = plan1.executeAsync();  // stream 1
TornadoExecutionResult r2 = plan2.executeAsync();  // stream 2
TornadoExecutionResult r3 = plan3.executeAsync();  // stream 3
```

Inside each plan, the default placement is cuda-oxide's fast path: execute the dependent bytecode chain on that plan's **primary stream**. Stream order handles `H2D -> kernel -> D2H` dependencies without extra event traffic. Different plan executions use different primary streams, so their chains can overlap.

Completion should not require a Java thread to block in `cuStreamSynchronize`. Prefer one of:

- a terminal CUDA event plus a lightweight polling/completion service;
- a `cuLaunchHostFunc` callback that completes a `CompletableFuture`/`TornadoExecutionResult`;
- backend-specific equivalent for OpenCL events.

`execute()` remains blocking and backward-compatible: it submits the same plan operation, then awaits its terminal event. `executeAsync()` returns after submission.

### 5.2 Automatic batching across plans
The cuda-oxide "concurrent batches" feature is not kernel fusion; it is **automatic stream scheduling of independent batch pipelines**. TornadoVM should expose the same behavior at the `ExecutionPlan` level:

- multiple independent `ExecutionPlan` executions submitted from one Java thread are enough to get concurrency;
- no user-created host threads are required;
- no stream id appears in the public API;
- shared read-only device buffers (`FIRST_EXECUTION` weights) are reused across plan executions without inserting false dependencies;
- the pool cap controls oversubscription, e.g. default 4 streams, configurable by `tornado.cuda.streams`.

This is the highest-value serving path: independent requests or independent prompt/batch jobs can share one GPU automatically, subject to CUDA's real hardware limits.

### 5.3 Isolation requirements
- **Plan-private `EventRegistry`**: event ids in bytecode are local to one submitted plan execution.
- **Context currency**: any worker/callback issuing CUDA work calls `cuCtxSetCurrent(ctx)`.
- **Memory lifetime**: device buffers must remain alive until all terminal events that use them have completed. Read-only shared buffers need reference-counted ownership at the runtime level.
- **No false cross-plan ordering**: the stream pool must not serialize independent plans through global barriers.
- **Profiling**: timelines are keyed by `(executionPlanId, executionInstanceId, streamId)`.

### 5.4 Scheduling policy
Start with cuda-oxide's policy because it is simple and effective:

```
StreamPoolRoundRobin {
    streams: Stream[default=4],
    next: AtomicInteger
}

schedule(planExecution):
    stream = streams[next.fetchAndIncrement() % streams.length]
    bind planExecution.primary = stream
```

Add `SingleStream` as a debug policy. Later policies can use occupancy/profiling feedback, but round-robin should be the first implementation because it is deterministic enough to test and has negligible overhead.

---

## 6. Goal 2 — Intra-ExecutionPlan concurrency

### 6.1 Scope: keep `withBatch()` as prior art, not as the target
TornadoVM's existing **`withBatch("<size>")`** already contains useful mechanics: byte offsets, chunk sizes, per-chunk thread counts, and bytecode fields such as `sizeBatch`/`batchThreads`. That code is useful because it shows how TornadoVM can describe "the same work over a shifted range" without changing the user kernel.

However, this design does **not** aim to improve, refactor, or change `withBatch()` semantics. Treat it as a reference implementation for:

- how to represent a range/chunk in bytecode;
- how to compute host offsets and per-chunk sizes;
- how task metadata carries `batchThreads`/offset state into compilation and launch;
- what pitfalls to avoid when serial bytecode accidentally provides ordering.

The stream feature should introduce a new scheduling capability rather than make `withBatch()` the centerpiece.

### 6.2 Desired intra-plan capability
Within one `ExecutionPlan`, the runtime should be able to overlap work only when the plan exposes **real independent units**. Examples:

- independent task-graph branches that already have no bytecode dependency edge;
- explicit producer/consumer windows introduced by a future runtime pipeline API;
- H2D of data for a later independent stage while a current stage computes;
- D2H of an earlier independent result while another branch computes.

The common dependent chain remains on one stream:

```
primary stream: H2D | K0 | K1 | K2 | D2H
```

If the DAG forks, the runtime may use extra streams and explicit event edges:

```
primary:  H2D shared | fork -------------------- join | final D2H
stream 1:              K branch A | D2H A ------|
stream 2:              K branch B | D2H B ------|
```

This keeps the intra-plan goal intact without coupling it to the old `withBatch()` path.

### 6.3 Optional future: automatic pipeline units
The cuda-oxide batching lesson can still guide a future TornadoVM feature: if the runtime can identify or create independent **pipeline units**, each unit should stay internally ordered on one stream while units are scheduled across the pool:

```
unit 0 on stream 0: H2D u0 | compute u0 | D2H u0
unit 1 on stream 1:   H2D u1 | compute u1 | D2H u1
unit 2 on stream 2:     H2D u2 | compute u2 | D2H u2
```

`withBatch()` demonstrates one way to encode such units, but the implementation plan should not depend on rewriting it. A new scheduler can reuse small pieces of the logic, such as offset/chunk metadata, if that proves useful.

### 6.4 When role streams are useful
Role streams are **not** the default for every dependent chain. Enable them when the bytecode DAG has independent branches inside a single plan:

- `TRANSFER_HOST_TO_DEVICE_*` can go to an H2D role stream.
- `LAUNCH` can go to a compute role stream.
- `TRANSFER_DEVICE_TO_HOST_*` can go to a D2H role stream.
- Existing `waitList` entries become `cuStreamWaitEvent` edges.

This permits H2D/compute/D2H or independent-kernel overlap within one plan when the DAG exposes such independence. If the graph is a strict chain, role streams add overhead and no extra concurrency, so keep it on one stream.

### 6.5 What overlaps
With intra-plan scheduling enabled, the runtime can overlap:

- independent kernels from different DAG branches if the GPU has spare SMs;
- independent H2D transfers, bounded by copy engines and pinned/pageable memory behavior;
- explicit DAG forks inside one plan using role streams and event joins.

This preserves the existing `ExecutionPlan` programming model while adding a scheduler capable of exploiting independence when the graph exposes it.

### 6.6 Requirements
- **Pinned host memory** for the H2D/D2H endpoints (Goal 3 provides this). Without it, copies are synchronous and the overlap collapses.
- **Remove the synchronise-after-copy.** Replace per-copy `cuStreamSynchronize` with event records; synchronise only at §8 boundaries. (The graph-capture exception already in the code stays.)
- **Dependency audit.** Add explicit dependencies for any bytecode edge that previously relied on incidental single-stream ordering.

### 6.7 CUDA graph interaction
For explicit role-stream mode, CUDA graph capture must include stream fork/join events. The rule remains: **no host synchronization during capture**.

---

## 7. Goal 3 — llama.cpp's "ring of 4" staged transfer

### 7.1 What it is
`llama.cpp::load_all_data` overlaps **disk read** with **PCIe H2D** using a ring of **4 pinned host staging buffers (~1 MB)**: read chunk *i+1* from disk into buffer *(i+1)%4* while `cudaMemcpyAsync` uploads chunk *i* from buffer *i*, with a CUDA **event per buffer** gating reuse. Pinned memory is what makes the copies truly async and full-bandwidth.

### 7.2 Is it compatible with Goals 1 & 2? — Partly; it needs an extra component
- It is **conceptually a special case of intra-plan pipelining** (Goal 2) applied to the *load / copy-in* phase: multiple async copies overlapped via events.
- **But Goal 1/2 stream support alone does NOT provide it.** Two missing pieces:
  1. **Pinned staging-buffer pool.** TornadoVM copies a whole tensor from a *pageable* `MemorySegment` in one shot. The ring needs a pool of **pre-pinned** host buffers (`cuMemHostAlloc`) to copy *through*.
  2. **Chunked transfer + producer/consumer overlap with the data source.** The win is overlapping the *host-side data production* (disk read, or dequant/format-conversion) with the GPU upload. That requires splitting a large transfer into chunks and interleaving "fill staging buffer" with "async upload", event-gated — a transfer primitive TornadoVM does not have today.

### 7.3 Conclusion: add it as a third, composable mechanism
Introduce a **`StagedTransfer`** utility in the device context:

```
StagedTransfer(stream(s), pinnedBufferPool, chunkSize, ringDepth=4)
   .upload(deviceDst, Producer src)     // src.fill(pinnedBuf, range) called on a host worker
```

- Uses 1..K streams from the same `StreamPool` (so it nests under Goals 1 & 2).
- `Producer` abstracts the host source: a file read (true llama.cpp case), or an mmap page-in, or a **dequant/convert step** (directly relevant to GPULlama's Q4_K→Q8_0 and the F16 copy-in path).
- Event-per-buffer reuse exactly as llama.cpp.

This makes the ring-of-4 **orthogonal and composable**: a plan can use intra-plan role streams or future pipeline stages (Goal 2) while its read-only weights stream in via `StagedTransfer` (Goal 3), and several such plans run concurrently (Goal 1).

### 7.4 What actually changes in GPULlama (concrete)
It is worth separating two *different* copies that both currently use `MemorySegment.copy` / host transfers, because Goal 3 touches them differently:

**(a) Bulk read-only weights (the ~800 ms copy-in) — TornadoVM-internal, GPULlama unchanged.**
GPULlama already declares weights as `transferToDevice(DataTransferMode.FIRST_EXECUTION, …)` in the task-graph layout. The actual upload is performed *by TornadoVM's runtime* on first execution (`cuMemcpyHtoDAsync` from the shallow-wrapped, **pageable** mmap segment, then a stream sync). **This is the Goal-3 target, and the change is entirely inside the CUDA backend**: route `FIRST_EXECUTION` read-only transfers through `StagedTransfer` (pinned ring + chunked async + event-gated reuse). GPULlama's code does **not** change — it keeps marking weights `FIRST_EXECUTION`; it simply gets pinned, overlapped uploads for free. The `Producer` for these is "page-in the mmap chunk" (and, for Q4_K/Q5_K/Q6_K, "dequant the chunk to Q8_0" — moving today's whole-tensor `dequantizeToQ8_0TornadoTensor` into the streamed pipeline so dequant overlaps upload).

**(b) The per-token embedding gather in `InferenceCore.forwardTornadoVM` — a different, smaller copy.**
The `MemorySegment.copy(tokenEmbeddings, token*rowBytes, state.embeddingX, 0, rowBytes)` there is **not** the weights — it gathers **one embedding row** (≈ `dim×2` bytes for F16, one Q8_0 block-row otherwise) from the embedding table into the activation buffer, every token; `embeddingX`/`embeddingXBatch` is then uploaded `EVERY_EXECUTION`. It is tiny and on the per-token critical path, so the ring-of-4 is *not* the right tool here. Two independent improvements are possible, and both are **optional / orthogonal**:
  1. *Stream overlap*: issue the gather+`EVERY_EXECUTION` H2D of token *t+1* on the H2D role stream while token *t*'s compute runs (Goal 2 applied to the decode loop). Saves the small copy off the critical path.
  2. *Device-side gather*: skip the host copy entirely — upload the token id and index the embedding table on the GPU. This removes the host `MemorySegment.copy` altogether but is a GPULlama kernel change, independent of this design.

**Summary for the reader:** Goal 3 changes **TornadoVM's internal `FIRST_EXECUTION` transfer path** (transparent to GPULlama) and is what shrinks the ~800 ms copy-in; the `forwardTornadoVM` copy is a separate per-token micro-transfer best addressed by Goal 2 overlap or a device-side gather, not the ring.

---

## 8. Synchronisation, lifecycle & API

### 8.1 Execution boundaries (when we actually sync)
1. **`execute()`** (blocking, default, backward-compatible): issue on streams, then `await()` the plan's terminal events.
2. **`executeAsync()` → `TornadoExecutionResult`**: returns after issue; `await()`/`isDone()` later. Enables Goal 1.
3. **D2H result read**: consuming a host output array implies an `await` on its D2H event.
4. **Explicit barrier** bytecode maps to recording/awaiting events across role streams.

### 8.2 Proposed public API (additive, defaults preserve today's behaviour)
```java
// Goal 1: run asynchronously; independent executions are scheduled across the stream pool.
TornadoExecutionResult res = plan.executeAsync();

// Goal 2: permit intra-plan concurrency when DAG independence exists.
// Default for a dependent chain remains one primary stream.
plan.withIntraPlanConcurrency();

// Goal 3: hint staged/pinned transfer for large read-only inputs.
plan.withStagedTransfer(/*ringDepth*/ 4, /*chunkBytes*/ 1<<20);
```
These entry points are independent, so the orthogonality is visible at the API level.

### 8.3 Lifecycle
- `StreamPool` created with the device context; streams lazily created up to a cap, then reused.
- `ExecutionStreamSet` acquired on execute, returned on completion.
- Shutdown: drain → destroy events → destroy streams → free pinned pool.

---

## 9. Backend changes (concrete)

**JNI (`cuda-jni`):**
- Stream create/destroy: `cuStreamCreate`/`cuStreamDestroy` (already partly present).
- Events: `cuEventCreate`, `cuEventRecord`, `cuStreamWaitEvent`, `cuEventSynchronize`.
- Pinned buffers: `cuMemHostAlloc` / `cuMemFreeHost` for the staging pool.
- Async copies already use `cuMemcpyHtoDAsync`/`DtoHAsync`; **remove the unconditional post-copy `cuStreamSynchronize`** (keep the graph-capture guard).

**Java (`tornado-drivers/cuda`, `…/ptx`):**
- Replace `CUDACommandQueueTable`/`PTXStreamTable` (thread-keyed) with `StreamPool` (role/round-robin).
- `CUDADeviceContext`: expose `StreamPool`, `StagedTransfer`, plan-private `EventRegistry`.

**Runtime (`tornado-runtime`):**
- `TornadoVMInterpreter`: issue normal dependent chains on the assigned primary stream; route ops to role streams only for intra-plan concurrency; translate cross-stream `waitList` entries to `cuStreamWaitEvent`; thread the `ExecutionStreamSet` through `execute()`.
- `TornadoExecutionPlan`/`TornadoExecutor`: `executeAsync()` + `TornadoExecutionResult`.

**OpenCL parity:** `cl_command_queue` + `cl_event` map 1:1 (out-of-order queues or multiple in-order queues); the abstraction holds.

---

## 10. Correctness considerations
- **RAW/WAR/WAW across streams**: fully covered by translating the existing `waitList` into events — no new hazard analysis needed, provided every op records its event and waiters wait on it.
- **False dependencies**: the current single-stream model adds *implicit* ordering; moving to events **removes** ordering that the DAG did not require — verify the bytecode generator does not rely on incidental stream ordering (add explicit `ADD_DEPENDENCY` where it silently relied on it).
- **CUDA graphs + multi-stream**: use stream fork/join capture; keep "no host sync during capture".
- **GC/host-memory races**: pinned staging buffers (off-heap) remove the Java-array/GC race the current code comments about.

---

## 11. Phased implementation
1. **Foundation**: `Stream`/`Event`/`StreamPool` abstractions; plan-private `EventRegistry`; remove post-copy sync. *(No behaviour change: single primary stream.)*
2. **Goal 1**: `executeAsync()` + `PlanExecution`/`ExecutionStreamSet`; round-robin stream scheduling; multi-plan tests; pool capping.
3. **Goal 2**: role streams + `waitList`→event translation for real intra-plan DAG forks; benchmark copy/compute and independent-branch overlap.
4. **Goal 3**: pinned `StagedTransfer` ring; wire into read-only copy-in; benchmark vs pageable.
5. **CUDA graphs** multi-stream capture; **OpenCL** queue mapping.

## 11A. Relationship to TornadoVM's existing `withBatch()` code
There are several overloaded uses of "batching"; this design intentionally separates them:

- **cuda-oxide concurrent batches**: independent pipelines scheduled across a stream pool. This is the model TornadoVM should copy for inter-`ExecutionPlan` concurrency.
- **TornadoVM `withBatch("<size>")`**: existing out-of-core chunking (`WithBatch`, `BatchConfiguration`, interpreter `sizeBatch`/`currentBatch`). This is **not** the target of this design.
- **GPULlama batch-prefill** (`BatchPrefillActivation`, `--batch-prefill-size`): a model-level optimization and also not the target here.

Use `withBatch()` only as a reference for how TornadoVM already represents offsets, ranges, and per-range launch metadata. Do not plan a `withBatch()` cleanup, rewrite, or semantic change as part of CUDA stream support. If a future stream scheduler needs range metadata, it may reuse small, well-contained pieces such as `BatchConfiguration.computeChunkSizes`, but the success criterion for this design is automatic stream scheduling of independent `ExecutionPlan` executions and explicit intra-plan DAG concurrency, not a better out-of-core batching implementation.

## 12. How this serves the GPULlama / startup use-case
- **Goal 3** turns the ~800 ms copy-in into an overlapped, pinned, full-bandwidth upload (the validated llama.cpp pattern), and the `Producer` hook lets dequant/format-conversion overlap the upload.
- **Goal 1** lets independent requests (e.g. server concurrency) share the GPU without manual threading.
- **Goal 2** can overlap independent intra-plan branches or explicit future pipeline stages when the task graph exposes them.
These compose with the NVRTC cubin cache (which removes the compile cost) to bring CUDA-backend startup and throughput in line with AOT engines.

## 13. Open questions
- Stream-count cap & scheduling policy under heavy inter-plan load (fairness vs throughput).
- Whether `executeAsync()` should be explicit only, or whether blocking `execute()` may internally submit multiple independent plans when an executor owns them.
- Auto-enabling Goal 2 from bytecode DAG shape vs explicit opt-in.
- Whether `StagedTransfer` should be exposed to user task-graphs or kept runtime-internal for read-only data.

## 14. References consulted
- cuda-oxide docs: [Concurrent Execution](https://nvlabs.github.io/cuda-oxide/async-programming/concurrent-execution.html) and local `cuda-oxide-book/async-programming/concurrent-execution.md`.
- cuda-oxide docs/code: local `cuda-oxide-book/async-programming/scheduling-and-streams.md`, `crates/cuda-async/src/scheduling_policies.rs`, `device_operation.rs`, and `device_future.rs`.
- NVIDIA CUDA Programming Guide: streams, events, concurrent kernel execution, and page-locked host memory sections.
- TornadoVM local code: `CUDACommandQueueTable`, `PTXStreamTable`, `CUDACommandQueue.cpp`, `PTXStream.cpp`, `TornadoVMInterpreter`, `TornadoVMGraphCompiler`, and `BatchConfiguration`.
