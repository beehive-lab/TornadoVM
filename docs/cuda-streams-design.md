# Design: First-Class CUDA Stream Support in TornadoVM

**Status:** Proposal · **Scope:** PTX and CUDA (`cuda2`) backends; OpenCL command-queue analogue noted where relevant · **Supersedes:** the thread-keyed stream WIP (PR #800)

---

## 1. Motivation & goals

Today every device operation in TornadoVM is issued to a **single stream per (device, host-thread)** and the stream is *synchronised after each transfer*. As a result:

- Host→device (H2D) copies, kernel execution, and device→host (D2H) copies run **strictly serially**, even though the hardware has independent copy engines and compute SMs that can run concurrently.
- Two independent `TornadoExecutionPlan`s cannot make progress on the GPU at the same time unless the user manually drives them from separate host threads (and even then they collide on the thread-keyed table).

This document specifies how streams should be modelled so that the following become first-class, composable capabilities:

| # | Goal (user's numbering) | Precise name used here | Meaning |
|---|---|---|---|
| 1 | "inter ExecutionPlan" | **Intra-plan pipelining** | Overlap **H2D ↔ compute ↔ D2H** *within a single* `ExecutionPlan`. |
| 2 | "intra ExecutionPlan" | **Inter-plan concurrency** | Run **multiple** `ExecutionPlan`s concurrently on different streams. Must be **orthogonal** to (1). |
| 3 | llama.cpp "ring of 4" | **Staged transfer pipelining** | Overlap *disk read* with *H2D* using a ring of pinned staging buffers. |

> **Terminology note.** The user's labels "inter/intra" are inverted relative to common usage; this document fixes the naming (intra = inside one plan, inter = across plans) but preserves the goal numbering (1/2/3).

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
1. **Wrong axis of concurrency.** Binding a stream to a *host thread* cannot express intra-plan overlap (Goal 1): a single plan runs on one thread → one stream → no copy/compute overlap. It only accidentally enables Goal 2 *if* the user spawns threads, conflating "concurrency" with "host threading".
2. **Synchronise-after-every-copy** defeats async entirely.
3. **Pageable source.** H2D copies from the (mmap'd / heap) `MemorySegment` directly; without pinned memory the driver falls back to a synchronous staging copy → no true overlap and reduced bandwidth (this also blocks Goal 3).
4. **PTX-only**, with graph-capture/EventRegistry races already being patched ad-hoc.
5. **No isolation between plans** sharing a device context → Goal 2 is unsafe.

---

## 3. Design principles

1. **Stream is a first-class device resource, decoupled from host threads.** Concurrency is expressed by *which stream* an operation targets, never by *which thread* issued it.
2. **Reuse the existing event DAG.** Cross-stream correctness is realised by translating each op's `waitList` into device events (`cuEventRecord`/`cuStreamWaitEvent`), not by stream ordering alone.
3. **Orthogonality by construction.** Intra-plan pipelining partitions work across a *small set of role streams owned by a plan*; inter-plan concurrency gives *each plan its own set*. N plans × K role-streams compose without special-casing.
4. **Async by default, synchronise only at well-defined boundaries** (plan completion, explicit barrier, or D2H result consumption).
5. **Backend-neutral abstraction.** The runtime speaks `Stream`/`Event`; PTX, CUDA, and OpenCL (queues+events) provide implementations. *(First implementation targets the **PTX** backend; CUDA/OpenCL follow.)*
6. **CUDA-graph compliance is mandatory and continuous.** Every change must remain compatible with graph capture/replay: multi-stream issue happens via fork/join *inside* capture, the `EventRegistry` is plan-private during capture, and the host stream is never synchronised while capturing. Graph capture/replay must be verified at every phase, not retrofitted at the end.

---

## 4. Core abstraction

```
TornadoDeviceContext
 └── StreamPool (per device, per context)
       ├── acquire(role)            -> Stream         // role ∈ {H2D, COMPUTE, D2H, GENERIC}
       ├── acquire(n)               -> Stream[]       // for staged-transfer ring
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
ExecutionStreamSet {              // created per ExecutionPlan execution
    Stream h2d, compute, d2h;     // role streams (may alias for simple plans)
    EventRegistry events;         // plan-private; replaces the global EventRegistry
}
```

Making the `EventRegistry` **plan-private** is what makes Goal 2 safe (today a global registry is the source of the capture races PR #800 keeps patching).

---

## 5. Goal 1 — Intra-plan pipelining (H2D ↔ compute ↔ D2H ↔ compute)

### 5.0 What overlaps — *not just transfers*
The API in an earlier draft was called `withConcurrentTransfers()`, which is misleading. The mechanism (route ops to streams + translate `waitList` into events) makes **any pair of ops that are independent in the DAG eligible to overlap**, not only transfers:

- **H2D ↔ compute** and **compute ↔ D2H** — copy/compute overlap (copy engine ∥ SMs). *The primary win.*
- **compute ↔ compute** — two **independent** kernels run concurrently (CUDA concurrent kernels) **iff** they are unordered in the DAG *and* the GPU has spare SM occupancy. In practice a single LLM forward pass is a dependency chain (layer *n* needs layer *n‑1*), so same-token kernel/kernel overlap is limited; the real kernel/kernel concurrency appears across **independent plans** (Goal 2) or across **batch chunks** (§5.4).
- **H2D ↔ H2D** — multiple input copies in flight (and the staged ring of §7).

So the capability is *intra-plan concurrency bounded by the dependency DAG and hardware*, of which copy/compute overlap is the most reliably available case. The flag is renamed accordingly:

```java
plan.withIntraPlanConcurrency();   // (was withConcurrentTransfers)
```

It does **not** force anything to overlap; it permits the runtime to issue independent ops to different role streams. If the DAG is fully serial, behaviour is identical to today (one effective stream).

### 5.1 Mechanism
The interpreter, when issuing the bytecode of one plan, routes ops to **role streams** instead of one stream:

- `TRANSFER_HOST_TO_DEVICE_*` → `h2d` stream
- `LAUNCH` → `compute` stream
- `TRANSFER_DEVICE_TO_HOST_*` → `d2h` stream

Correctness is preserved by converting the existing `waitList` into cross-stream waits:

```
for each op:
    deps = waitList(op).map(eventId -> events.get(eventId))   // CUevents
    targetStream.waitFor(deps)        // cuStreamWaitEvent for each cross-stream dep
    Event e = targetStream.enqueue(op)
    events.put(op.eventId, e)         // cuEventRecord on targetStream
```

Because the DAG is already explicit, a kernel that consumes buffer X simply waits on the H2D event that produced X — but a kernel that is *independent* of an in-flight copy is **not** serialised behind it. This yields the classic 3-stage overlap:

```
time ─────────────────────────────────────────────────►
H2D    [copy b0][copy b1][copy b2] ...
COMP            [k(b0) ][k(b1) ][k(b2)]
D2H                     [out0   ][out1  ]
```

### 5.2 Requirements
- **Pinned host memory** for the H2D/D2H endpoints (Goal 3 provides this). Without it, copies are synchronous and the overlap collapses.
- **Remove the synchronise-after-copy.** Replace per-copy `cuStreamSynchronize` with event records; synchronise only at §8 boundaries. (The graph-capture exception already in the code stays.)
- **Batched / tiled buffers.** Overlap only materialises when a plan has multiple independent transfer+compute units (e.g. TornadoVM *batch* execution, or per-layer task graphs as in GPULlama). For a monolithic single-shot graph there is nothing to pipeline — the design degrades gracefully to one stream.

### 5.3 Interaction with CUDA graphs
When a plan is captured into a CUDA graph, the multi-stream issue must happen **inside the capture** (CUDA supports multi-stream capture via forked streams joined with events). The plan-private `EventRegistry` and "do not synchronise during capture" rule (already present) make this sound; the role streams become the captured fork/join structure, replayed cheaply per token.

### 5.4 The natural unit of pipelining is the *batch chunk* — and TornadoVM already has one
TornadoVM's existing **`withBatch("<size>")`** (out-of-core execution) splits one oversized input array into `totalChunks` chunks and runs, per chunk, a **H2D → compute → D2H** sequence (`BatchConfiguration.computeChunkSizes`; the interpreter's `sizeBatch`/`batchThreads`/`currentBatch`). **Today those chunks run strictly serially on one stream** — precisely the shape that benefits most from pipelining:

```
serial today:   H2D c0 | K c0 | D2H c0 | H2D c1 | K c1 | D2H c1 | ...
pipelined:      H2D c0 | H2D c1 | H2D c2
                         K c0   | K c1   | K c2
                                  D2H c0 | D2H c1 | D2H c2
```

So the batch-chunk loop is the **canonical intra-plan pipeline unit**: assign chunk *i* to role streams and gate chunk *i*'s kernel on chunk *i*'s H2D event, letting chunk *i+1*'s H2D run on the copy engine while chunk *i*'s kernel runs. See §11A for how this reuses (and cleans up) the existing batching code.

**Where chunk-pipelining helps — and where it does not.** It pays off for **data-parallel bulk** work where there is a real per-chunk H2D to overlap: out-of-core arrays, and prompt **prefill** over many tokens. It does **not** help **single-token decode**, for three compounding reasons, so `Batch.AUTO` must **no-op gracefully** when there is nothing chunkable:
1. **No transfer to hide behind** — decode weights are already resident (`FIRST_EXECUTION`); there is no per-token weight H2D, so there is no copy for compute to overlap.
2. **Bandwidth-bound math** — batch-1 matrix-*vector* products read each weight once (~1 FLOP/byte); runtime ≈ `weight_bytes / HBM_bandwidth`. Splitting one matvec across streams reads the same bytes and **shares the same bandwidth** → no speedup. Concurrent kernels only help with idle bandwidth/SMs or compute-bound kernels.
3. **Serial layer DAG** — QKV→attn→proj→FFN and layer→layer are a dependency chain, so the *independent* kernels concurrent execution needs don't exist within one token's pass.

Decode's idle GPU is instead filled by **Goal 2** (concurrent independent requests fill spare SMs) and by **model-level batching** (process B tokens together so matrix-vector becomes matrix-*matrix*, raising arithmetic intensity until it is compute-bound) — neither of which is intra-plan chunking. This is *why* concurrency and chunking are kept as separate axes (§8.2): they target different workloads.

### 5.5 Implementation outcome — batch-chunk pipelining landed (prototype, flag-gated)

The §5.4 batch-chunk pipeline is **implemented and validated** for the PTX backend as a flag-gated prototype (this is what we referred to as "Goal 3(a)" in working notes; it is the *device-buffer ring for batch chunks*, distinct from §7's *pinned host staging ring* which remains future work).

**Activation.** `plan.withBatch("<size>").withIntraPlanConcurrency()` **and** `-Dtornado.batch.pipeline=true`. All three are required; with the flag off (default) batching is byte-for-byte the old serial path. Ring depth is `-Dtornado.batch.pipeline.depth` (default 3), clamped to `[2, totalChunks]`.

**Mechanism (device-buffer ring).**
- Each batched object gets a **ring of N device buffers** instead of one (`XPUDeviceBufferState.setBufferRing/selectSlot`; `selectSlot` repoints `getXPUBuffer()` so the kernel-arg pointer, H2D target and D2H source all follow the slot). Chunk *i* is routed to slot `i % N` host-side at issue time (`TornadoVMInterpreter.selectPipelineSlot`), so different-slot chunks touch physically distinct buffers and can overlap.
- **Bounded ring correctness (slot reuse).** When `N < totalChunks`, chunk *i* and *i+N* share a slot. A **per-`(bufferState, slot)` last-event** is tracked (`withSlotReuseDependency` / `recordSlotEvent`): every H2D / kernel / D2H touching a slot appends that slot's previous event to its wait-list and then records itself. This serialises each slot's timeline cross-stream via the existing event DAG, while different slots run concurrently — i.e. correctness with **bounded memory** (`N` buffers, not `totalChunks`).
- Transfers already route to H2D/D2H role streams and kernels round-robin the COMPUTE pool, so the ring is the only missing piece; the existing dependency translation does the rest.

**Why the ring is necessary.** Without it, `withBatch + withIntraPlanConcurrency` shares one device buffer per object across all chunks and **races** (chunk *i+1*'s H2D clobbers the buffer chunk *i* is still reading). Verified: flag-off (no ring) fails; flag-on (ring) passes.

**Validation** (RTX 5090 Laptop, `asyncEngineCount=2`; test `TestBatches#test256MBPipelined`, self-skips unless the flag is set):
- Correct at ring depth 3 (5 chunks → slots wrap) and depth 2 (max wrap); full `TestBatches` suite **28/28** with the flag off (no regression).
- Profiled overlap: chunks on 4 compute streams, H2D on one copy engine, D2H on the other, **H2D∩D2H ≈ 8 ms** genuine bidirectional overlap; transfer region ≈13.8 ms vs ~21 ms serial (~1.5×). The win is modest on this small (compile-dominated) test and is expected to be larger on big, transfer-bound out-of-core workloads.

**Known limitations.**
- **Single `execute()` only.** Re-executing a *batched* plan in a loop hits a **pre-existing** bug (`cuModuleGetFunction` → `CUDA_ERROR_INVALID_HANDLE 400`) that also affects plain batches; it is orthogonal to this work and must be fixed separately before pipelined batches can be re-run per-plan.
- PTX backend only; other backends ignore the flag.

**Key files.** `runtime/.../interpreter/TornadoVMInterpreter.java` (ring alloc/teardown, slot routing, reuse dependency), `runtime/.../common/XPUDeviceBufferState.java` (ring), `runtime/.../common/TornadoOptions.java` (`ENABLE_BATCH_PIPELINE`, `BATCH_PIPELINE_RING_DEPTH`), `unittests/.../batches/TestBatches.java` (`test256MBPipelined`).

---

## 6. Goal 2 — Inter-plan concurrency (multiple ExecutionPlans)

### 6.1 Mechanism
Each `ExecutionPlan` execution acquires its **own `ExecutionStreamSet`** from the pool and uses its **own plan-private `EventRegistry`**. Two plans therefore:

- issue to **disjoint streams** → the GPU scheduler overlaps their kernels/copies subject only to resource availability;
- never share event ids → no cross-plan interference.

Execution becomes asynchronous and returns a handle:

```java
TornadoExecutionResult r1 = plan1.executeAsync();   // returns immediately
TornadoExecutionResult r2 = plan2.executeAsync();
r1.await(); r2.await();                              // join points
```

### 6.2 Orthogonality to Goal 1 (the key property)
The two mechanisms compose because they act on **different axes**:

- Goal 1 partitions *one plan's* ops across **K role streams** (intra-set).
- Goal 2 gives *each plan* its **own set** of role streams (inter-set).

Running **N** plans each pipelined over **K** role streams simply draws **N×K** streams from the pool; nothing in the issue logic special-cases the combination. The pool caps total streams and round-robins/reuses to avoid unbounded growth.

### 6.3 Isolation requirements
- **Plan-private `EventRegistry`** (mandatory; removes the global-registry races).
- **Context currency**: a worker issuing a plan calls `cuCtxSetCurrent(ctx)` for that device; safe across threads.
- **Memory**: distinct plans own distinct device buffers. Shared read-only buffers (e.g. weights) are fine concurrently; the pool must *not* insert false dependencies between plans.
- **Determinism / profiling**: per-plan profiler timelines keyed by stream id.

### 6.4 Async completion via host callbacks (the cuda-oxide model)
NVIDIA's **cuda-oxide** validates this exact axis (its *batch concurrent execution*: N independent forward-pass pipelines, each on a round-robin stream from a pool — see its [concurrent-execution chapter](https://nvlabs.github.io/cuda-oxide/async-programming/concurrent-execution.html)). The one mechanism worth importing wholesale is **how it signals completion**:

> "When a `DeviceOperation` is scheduled, `DeviceFuture` enqueues the work on a CUDA stream, then registers a host callback via `cuLaunchHostFunc` that wakes the async task when the GPU finishes."

`executeAsync()` should therefore **not** block a host thread per plan. Instead, after issuing a plan's ops, enqueue **`cuLaunchHostFunc` on the plan's terminal stream** to complete a `CompletableFuture`:

```java
CompletableFuture<TornadoExecutionResult> f = plan.executeAsync();
// internally: issue ops on streams; cuLaunchHostFunc(terminalStream, () -> f.complete(result));
```

This is what makes inter-plan concurrency **scale to many plans** — e.g. an LLM server with one `ExecutionPlan` per in-flight request: dozens of plans overlap on the stream pool while the host stays free, completions arriving via callback. (`cuLaunchHostFunc` is not currently used in the JNI; it must be added — see §9.) Two further cuda-oxide practices map directly onto §6.3: **shared read-only weights** (`Arc<DeviceBox>` uploaded once, cloned cheaply) = one set of read-only device buffers referenced by all plans with no false deps; and **per-pipeline ownership** = the per-plan `ExecutionStreamSet`/buffers.

> **Note on what cuda-oxide does *not* do:** within a pipeline it uses `and_then` to keep stages **strictly ordered on one stream** — it has *no* intra-plan copy/compute overlap. Its throughput comes entirely from running many pipelines concurrently (Goal 2) when "the GPU has spare SMs". TornadoVM's Goal 1 (§5) is therefore **additive** on top of the cuda-oxide model, not a substitute.

---

## 7. Goal 3 — llama.cpp's "ring of 4" staged transfer

### 7.1 What it is
`llama.cpp::load_all_data` overlaps **disk read** with **PCIe H2D** using a ring of **4 pinned host staging buffers (~1 MB)**: read chunk *i+1* from disk into buffer *(i+1)%4* while `cudaMemcpyAsync` uploads chunk *i* from buffer *i*, with a CUDA **event per buffer** gating reuse. Pinned memory is what makes the copies truly async and full-bandwidth.

### 7.2 Is it compatible with Goals 1 & 2? — Partly; it needs an extra component
- It is **conceptually a special case of intra-plan pipelining** (Goal 1) applied to the *load / copy-in* phase: multiple async copies overlapped via events.
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

This makes the ring-of-4 **orthogonal and composable**: a plan can pipeline compute (Goal 1) while its read-only weights stream in via `StagedTransfer` (Goal 3), and several such plans run concurrently (Goal 2).

### 7.4 What actually changes in GPULlama (concrete)
It is worth separating two *different* copies that both currently use `MemorySegment.copy` / host transfers, because Goal 3 touches them differently:

**(a) Bulk read-only weights (the ~800 ms copy-in) — TornadoVM-internal, GPULlama unchanged.**
GPULlama already declares weights as `transferToDevice(DataTransferMode.FIRST_EXECUTION, …)` in the task-graph layout. The actual upload is performed *by TornadoVM's runtime* on first execution (`cuMemcpyHtoDAsync` from the shallow-wrapped, **pageable** mmap segment, then a stream sync). **This is the Goal-3 target, and the change is entirely inside the CUDA backend**: route `FIRST_EXECUTION` read-only transfers through `StagedTransfer` (pinned ring + chunked async + event-gated reuse). GPULlama's code does **not** change — it keeps marking weights `FIRST_EXECUTION`; it simply gets pinned, overlapped uploads for free. The `Producer` for these is "page-in the mmap chunk" (and, for Q4_K/Q5_K/Q6_K, "dequant the chunk to Q8_0" — moving today's whole-tensor `dequantizeToQ8_0TornadoTensor` into the streamed pipeline so dequant overlaps upload).

**(b) The per-token embedding gather in `InferenceCore.forwardTornadoVM` — a different, smaller copy.**
The `MemorySegment.copy(tokenEmbeddings, token*rowBytes, state.embeddingX, 0, rowBytes)` there is **not** the weights — it gathers **one embedding row** (≈ `dim×2` bytes for F16, one Q8_0 block-row otherwise) from the embedding table into the activation buffer, every token; `embeddingX`/`embeddingXBatch` is then uploaded `EVERY_EXECUTION`. It is tiny and on the per-token critical path, so the ring-of-4 is *not* the right tool here. Two independent improvements are possible, and both are **optional / orthogonal**:
  1. *Stream overlap*: issue the gather+`EVERY_EXECUTION` H2D of token *t+1* on the H2D role stream while token *t*'s compute runs (Goal 1 applied to the decode loop). Saves the small copy off the critical path.
  2. *Device-side gather*: skip the host copy entirely — upload the token id and index the embedding table on the GPU. This removes the host `MemorySegment.copy` altogether but is a GPULlama kernel change, independent of this design.

**Summary for the reader:** Goal 3 changes **TornadoVM's internal `FIRST_EXECUTION` transfer path** (transparent to GPULlama) and is what shrinks the ~800 ms copy-in; the `forwardTornadoVM` copy is a separate per-token micro-transfer best addressed by Goal 1 overlap or a device-side gather, not the ring.

### 7.5 The ring-of-4 and batch-concurrent are the *same machinery* at different granularities
Goals 1–3 are not three separate subsystems — they are three **configurations of one substrate**: a **`StreamPool`** plus an **async-completion mechanism** (CUDA *events* for inter-op deps, `cuLaunchHostFunc` *host callbacks* for plan-level completion). The work unit differs:

| Capability | Work unit | Streams | Completion | Layer |
|---|---|---|---|---|
| Goal 3 — ring-of-4 | transfer **chunk** | K (ring) | per-buffer **event** | intra-plan (transfer) |
| Goal 1 — pipelining | bytecode **op** | role streams | **event** (`waitList`→`cuStreamWaitEvent`) | intra-plan |
| Goal 2 — batch concurrent (cuda-oxide) | whole **ExecutionPlan** | one set per plan | **`cuLaunchHostFunc`** | inter-plan |

Because they share the pool and completion primitives, they **compose by stacking**: an LLM server runs **N concurrent plans** (Goal 2), each **streaming its read-only weights through the ring** (Goal 3), each **overlapping its own copy/compute** (Goal 1) — all drawing streams from a single pool and capped together. Build the substrate once (§4 + `cuLaunchHostFunc`), and all three are configurations of it. This is the convergence point: the "ring of 4" and cuda-oxide's "batch concurrent" are the *same* stream-pool-plus-async-completion pattern applied to a transfer chunk vs. a whole pipeline.

> **Status (updated).** Both rings are now implemented (flag-gated, PTX):
> - The **device-buffer ring for batch chunks** (§5.4/§5.5) — validated, `-Dtornado.batch.pipeline`.
> - The **pinned host staging ring** of this section — implemented as `-Dtornado.ptx.staged.transfers`
>   (chunk `-Dtornado.ptx.staged.chunk.size`, depth `-Dtornado.ptx.staged.ring.depth`, threshold
>   `-Dtornado.ptx.staged.min.size`, fill parallelism `-Dtornado.ptx.staged.fill.threads`). Large
>   non-batch H2D transfers of `MemorySegment`-backed objects are split into chunks, each memcpy'd
>   (multi-threaded) into a pinned slot and uploaded with `cuMemcpyHtoDAsync`, the fill of chunk
>   *i+1* overlapping the DMA of chunk *i*; large read-only segments skip the whole-segment
>   `cuMemHostRegister`.
>
> **Measured (RTX 5090 Laptop, 1 GiB read-only weights, first execution):**
> - **Cold mmap'd file** (the GPULlama GGUF case, O_DIRECT-written so the page cache is cold):
>   direct ≈ 420 ms (2.5 GB/s) vs staged ≈ 215 ms (5.0 GB/s) — **~1.96×**; the staged path reaches
>   NVMe line rate because page-in is parallelised and overlapped with the DMA.
> - **Warm resident pages**: direct ≈ 57 ms (19 GB/s) vs staged ≈ 85 ms (12.7 GB/s) — staged
>   *loses* (~0.67×): a memcpy moves 2× the bytes while `cuMemHostRegister` moves none, and the
>   register/DMA of successive tensors already pipeline. Hence the feature stays **opt-in**: enable
>   it when the source is a cold mmap (weight loading), keep the direct path for resident data.
> - The `Producer` hook (overlapping dequant/conversion with upload) remains future work; the ring
>   is the substrate it plugs into.

---

## 8. Synchronisation, lifecycle & API

### 8.1 Execution boundaries (when we actually sync)
1. **`execute()`** (blocking, default, backward-compatible): issue on streams, then `await()` the plan's terminal events.
2. **`executeAsync()` → `TornadoExecutionResult`**: returns after issue; `await()`/`isDone()` later. Enables Goal 2.
3. **D2H result read**: consuming a host output array implies an `await` on its D2H event.
4. **Explicit barrier** bytecode maps to recording/awaiting events across role streams.

### 8.2 Proposed public API (additive, defaults preserve today's behaviour)
The API exposes **composable, self-documenting capabilities on orthogonal axes** — deliberately *not* a single `withIntraPlanConcurrency(boolean batched)` (a boolean trap that conflates two axes and hides the chunk-count policy):

```java
// AXIS 1 — concurrency (Goal 1): permit overlap of ops that are independent in the
// DAG (copy/compute, and independent compute/compute). If the plan is chunked
// (see axis 2), it pipelines across chunks; if not, it overlaps only pre-existing
// DAG independence. Default off → single stream, behaviour identical to today.
plan.withIntraPlanConcurrency();

// AXIS 2 — chunking (independent of concurrency): the existing out-of-core knob,
// plus an AUTO heuristic. Chunking alone (no concurrency) still serves out-of-core.
plan.withBatch("256MB");        // existing: explicit size
plan.withBatch(Batch.AUTO);     // NEW: runtime derives chunk count (≈ pool depth)

// Composed: concurrency + auto-chunking = the pipelined batch executor (§5.4).
plan.withIntraPlanConcurrency().withBatch(Batch.AUTO);

// Goal 2 — inter-plan: run asynchronously on an independent stream set; completion
// via cuLaunchHostFunc (§6.4), so many plans overlap without a thread each.
CompletableFuture<TornadoExecutionResult> res = plan.executeAsync();

// Goal 3 — staged/pinned ring transfer for large read-only inputs (runtime may
// also enable this internally for FIRST_EXECUTION weights without an API call).
plan.withStagedTransfer(/*ringDepth*/ 4, /*chunkBytes*/ 1<<20);
```
Each axis is independent and self-named → the orthogonality is visible at the call site. (If a single entry point is ever preferred over two methods, use an **enum** — `withIntraPlanConcurrency(Pipelining.OPS_ONLY | Pipelining.AUTO_BATCH)` — never a boolean.)

### 8.3 Lifecycle
- `StreamPool` created with the device context; streams lazily created up to a cap, then reused.
- `ExecutionStreamSet` acquired on execute, returned on completion.
- Shutdown: drain → destroy events → destroy streams → free pinned pool.

---

## 9. Backend changes (concrete)

**JNI (`cuda-jni`):**
- Stream create/destroy: `cuStreamCreate`/`cuStreamDestroy` (already partly present).
- Events: `cuEventCreate`, `cuEventRecord`, `cuStreamWaitEvent`, `cuEventSynchronize`.
- **Host completion callback: `cuLaunchHostFunc`** (not present today) — enqueued on a plan's terminal stream to complete the `executeAsync()` future without blocking a host thread (§6.4). The callback runs on a driver thread, so it must only hand off to a Java `CompletableFuture`/queue, never call back into CUDA.
- Pinned buffers: `cuMemHostAlloc` / `cuMemFreeHost` for the staging pool.
- Async copies already use `cuMemcpyHtoDAsync`/`DtoHAsync`; **remove the unconditional post-copy `cuStreamSynchronize`** (keep the graph-capture guard).

**Java (`tornado-drivers/cuda`, `…/ptx`):**
- Replace `CUDACommandQueueTable`/`PTXStreamTable` (thread-keyed) with `StreamPool` (role/round-robin).
- `CUDADeviceContext`: expose `StreamPool`, `StagedTransfer`, plan-private `EventRegistry`.

**Runtime (`tornado-runtime`):**
- `TornadoVMInterpreter`: route ops to role streams; translate `waitList` → `cuStreamWaitEvent`; thread the `ExecutionStreamSet` through `execute()`.
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
1. **Foundation**: `Stream`/`Event`/`StreamPool` abstractions; plan-private `EventRegistry`; remove post-copy sync. *(No behaviour change: single role stream.)*
2. **Goal 1**: role streams + `waitList`→event translation; enable for batched plans; benchmark copy/compute overlap.
3. **Goal 3**: pinned `StagedTransfer` ring; wire into read-only copy-in; benchmark vs pageable.
4. **Goal 2**: `executeAsync()` + `ExecutionStreamSet` + **`cuLaunchHostFunc` completion** (cuda-oxide model); shared read-only weights across plans; multi-plan + LLM-server tests; pool capping.
5. **CUDA graphs** multi-stream capture; **OpenCL** queue mapping.

## 11A. Relationship to TornadoVM's existing batching WIP
There are **two unrelated things called "batching"**; the design touches one and ignores the other:

- **TornadoVM `withBatch("<size>")`** (out-of-core chunking; `WithBatch`, `BatchConfiguration`, interpreter `sizeBatch`/`currentBatch`): splits a too-large array into chunks executed as a serial H2D→compute→D2H loop. It is **directly relevant** — it is the existing implementation of the exact chunked pipeline §5.4 wants. It is, as noted, **old and barely used/tested**.
- **GPULlama "batch-prefill"** (`BatchPrefillActivation`, `--batch-prefill-size`): a *model-level* optimisation that processes several prompt tokens per pass. It is **not** TornadoVM batching and is **out of scope** here (it benefits indirectly, like any plan, from Goals 1–3).

**Recommendation for the TornadoVM batching WIP:** treat it as the substrate for §5.4 and **redesign/clean it** rather than bolt streams beside it.
- *Reuse*: the chunk-splitting math (`BatchConfiguration.computeChunkSizes`) and the per-chunk bytecode are sound; keep them.
- *Redesign*: replace the **serial** chunk loop with a **pipelined** one driven by the `StreamPool` + per-chunk events (chunk *i+1* H2D overlaps chunk *i* compute). The chunk becomes the pipeline stage of §5.4.
- *Clean/deprecate*: the current synchronise-per-chunk path and any thread-keyed assumptions are removed; if the old single-stream semantics must remain reachable, gate them behind the default (concurrency off) rather than as separate code. Given its low usage, we are free to change its internals (no stable behaviour to preserve beyond "results are correct").
- *Test*: the WIP has minimal coverage; add chunked-pipeline correctness + overlap benchmarks as part of Phase 2.

This both delivers Goal 1 for the most impactful case (chunked/out-of-core and, by extension, per-layer LLM graphs) and pays down the existing batching debt instead of duplicating it.

## 12. How this serves the GPULlama / startup use-case
- **Goal 3** turns the ~800 ms copy-in into an overlapped, pinned, full-bandwidth upload (the validated llama.cpp pattern), and the `Producer` hook lets dequant/format-conversion overlap the upload.
- **Goal 1** overlaps per-layer H2D with the previous layer's compute during prefill.
- **Goal 2** lets independent requests (e.g. server concurrency) share the GPU without manual threading.
These compose with the NVRTC cubin cache (which removes the compile cost) to bring CUDA-backend startup and throughput in line with AOT engines.

## 13. Open questions
- Stream-count cap & scheduling policy under heavy inter-plan load (fairness vs throughput).
- Auto-enabling Goal 1 (heuristic on batch/graph shape) vs explicit opt-in.
- Whether `StagedTransfer` should be exposed to user task-graphs or kept runtime-internal for read-only data.
