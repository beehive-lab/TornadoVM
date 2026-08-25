# Pattern catalogue and prior results

Where the time has actually been, which pattern removed it, and what the measured share was. Use it to avoid optimising a path that is already 0.1% of wall.

## Established shares (RTX 4090, CUDA)

| Workload | Cost split |
|---|---|
| `saxpy 512` (dispatch-bound) | ~5.2 µs host driver calls + ~3.0 µs terminal sync vs **2.5 µs** of device work |
| Java bytecode interpreter | **~1%** of wall on a dispatch-bound graph — the interpreter is not the problem |
| LLM decode, profiler off | **~90% `cuStreamSynchronize`** (real device wait); Java-side work is noise |
| LLM short run, whole process | start-up dominates: `cuMemHostRegister` alone ~3.4 s (63% of all CUDA API time) |
| Per-token activation graph (one 1.2 µs kernel) | **0.0% of GPU time**, ~0.1% of wall — structurally ugly, not worth optimising |

## Patterns that paid

| Pattern | Applied to | Result |
|---|---|---|
| **Dirty-bit memoization** — don't re-send state the device already has | per-launch kernel stack frame | 17.5 → 8.6 µs/exec (2.03x) |
| **Cache a monotone predicate** — don't ask the driver what you already know | stream sync when nothing was enqueued | 3 syncs/exec → 1 |
| **Deferred/lazy evaluation at a quiescent point** | profiler timestamp reads | profiler-on cost −34%; `cuEventSynchronize` 51.9% → 1.1% of wall |
| **Bound work by use, not capacity** (high-water mark) | wait-list row clearing (32768 ints/row) | deps-on decode 46 → 72 tok/s (1.57x) |
| **Request coalescing** — N round trips → 1 | on-demand copy-outs of several objects | 8 objects 36.3 → 14.0 µs (2.6x) |
| **Latency hiding + bounded in-flight queue** | deferred output materialisation | host-work overlap 1.42x; back-to-back 1.59x; `execute()` returns 11x sooner |
| **Batch the whole pipeline into one submission** | CUDA graphs (existing feature, under-used) | LLM decode 72 → 103 tok/s (1.42x) |
| **Capability negotiation over implicit contract** | `XPUBuffer.supportsAsyncRead()` | turned a silent wrong-result path into an explicit opt-in |
| **Intent-revealing operation** replacing a workaround | `transferToDevice()` instead of a dummy kernel run to force a copy-in | removes a full dummy forward pass; wall-neutral on the LLM, clearer API |

## Anti-patterns found in the runtime (look for their relatives)

- **A global predicate read as a per-object one.** "Is a buffer of this size in use?" answered "may *this* object reuse its buffer" → an unallocated output and a null-buffer crash on batched multi-output graphs.
- **Two methods that look like a blocking/async pair but are not substitutable.** `read()` handled partial copies, sub-regions and batches; `enqueueRead()` handled none of them and silently copied the wrong region for some buffer types.
- **A flag computed twice, inverted once.** `events == null` vs `events != null` for the same `useDeps` argument in different backends — dead code hid it until a change made the path reachable.
- **An operation applied to "the selected graph" when it names an object.** Driving a plan with `withGraph(i)` made on-demand transfers silently no-op for objects owned by other graphs.
- **Instrumentation that changes the measurement.** Inline timestamp reads serialised the host against the device and inflated the reported copy time ~20x.

Common shape: *an implicit contract that only some implementations honour, with no place to declare it and no test that checks it.* When you find one, prefer declaring the capability over adding another special case.

## Open leads (with measured ceilings)

1. **The terminal blocking read-back** is the largest remaining host cost on dispatch-bound graphs (~36% of the raw sync average, but only ~17% recoverable — much of that sync overlaps work already enqueued). Deferred outputs address it; pipelining needs no double buffering because a single in-order queue already orders copy N before kernel N+1.
2. **Residual profiler overhead ~2.2x** is now the timing events themselves (`cuEventRecord` 615 ns with timing vs 137 ns without), not the reads. Needs opt-in timing granularity.
3. **Start-up**: `cuMemHostRegister` and NVRTC dominate short runs. Untouched. Biggest absolute number left.
4. **Code installation vs data transfer are entangled**: a transfers-only pass leaves first-launch cost for the first real execution, so the win moves rather than appears. A warm-up that installs code without transferring would separate them.
5. **Transfer-API consolidation** (issue #1028): `read`/`enqueueRead`, `streamOut`/`streamOutBlocking` and ~96 near-identical device-context overloads differing by one boolean. Contract tests exist (`TestTransferContract`) to pin behaviour before the refactor.

## Campaign hygiene

- Keep an **umbrella issue** with the method, the build matrix (A = develop, B = A+PRs, C = B+more) and one comment per result. It is the only shared memory across PRs and sessions.
- Record **disproven hypotheses** in it too. Three of this campaign's dead ends (an existing "non-blocking stream-out" flag that was slower, an ordering-barrier theory, double-buffered outputs) would otherwise be retried by the next person.
