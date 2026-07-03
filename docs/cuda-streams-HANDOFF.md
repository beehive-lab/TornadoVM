# CUDA Streams — Work Handoff

_Last updated: 2026-07-02. Purpose: let a fresh session pick up the PTX CUDA-streams work without re-deriving context._

---

## 1. Goal & scope

First-class **CUDA stream support in the TornadoVM PTX backend only** (`tornado-drivers/ptx` + `tornado-drivers/ptx-jni`). OpenCL / CUDA2 / SPIR-V / Metal are **out of scope**. API rule: axes are **composable and orthogonal, never a boolean**.

Three goals (user's numbering; see `docs/cuda-streams-design.md` for the full design):

| # | Name | Meaning | Status |
|---|------|---------|--------|
| **1** | Intra-plan concurrency | Overlap H2D ↔ compute ↔ D2H *within one plan* via role streams + a COMPUTE-stream pool (`withIntraPlanConcurrency()`). | **Done** (committed) |
| **2** | Inter-plan concurrency | Multiple `ExecutionPlan`s concurrent via `executeAsync` + `cuLaunchHostFunc`. | **Not started** |
| **3** | Staged / pipelined transfers | (a) device-buffer ring pipelining the `withBatch` chunk loop; (b) `§7` pinned host staging ring for the `FIRST_EXECUTION` weight copy-in. | **3(a) done (committed); 3(b) implemented + evaluated (uncommitted, 2026-07-02)** |

Motivating use case: **GPULlama3** (startup weight copy-in ~800 ms, prefill, concurrent-request serving).

---

## 2. Branch map (CONSOLIDATED 2026-07-02)

**`feat/cuda-streams` @ `d16982e40` is now the single working branch** (checked out, local only — the fork remote still has the old tip; PRs #800/#810 need a force-push to update). It was consolidated via option (A):

1. Fast-forwarded onto `feat/cuda-streams-perf` (`05bf318b9`) — brings all pool/perf commits **and** the merge of `origin/develop` (which was already fully up to date at consolidation time).
2. Merged `fix/ptx-recompile-batched-reexec` (**open PR #882** against develop; lives in worktree `/home/orion/TornadoVM-develop`) — brings the `PTXCodeCache` invalidated-module fix + `testBatchRepeatedExecution`. Merged (not cherry-picked) so history reconciles when #882 lands.
3. Restored the meaningful unstaged changes (see §4) as unstaged; the previously-unstaged `PTXCodeCache` diff was **dropped** (identical to the merged fix).

- `feat/cuda-streams-pool` and `feat/cuda-streams-perf` are now fully contained in `feat/cuda-streams` → **redundant, safe to delete**.
- Safety backup of the pre-uncommit state: branch **`backup/pre-uncommit`** @ `49f4b0ce4` (delete when confident).

---

## 3. Progress — DONE and committed (on `feat/cuda-streams-perf`, now synced to develop)

- **Phase-1 foundation:** plan-private `StreamPool` / `ExecutionStreamSet` / `EventRegistry`, `PTXStream` role streams (`DEFAULT` / `DATA_TRANSFER_H2D` / `COMPUTE` / `DATA_TRANSFER_D2H`), thread-decoupled (replaced the old thread-keyed `PTXStreamTable`).
- **Goal 1 — intra-plan concurrency:**
  - `withIntraPlanConcurrency()` per-plan API axis (deprecated `withCUDAStreams()` alias kept); `isMultiStreamEnabled(executionPlanId)` per-plan.
  - **COMPUTE stream pool** (default 4, `-Dtornado.ptx.compute.streams`): round-robin DAG-independent kernels; events addressed by `(role, streamIndex, localEventId)`.
  - Host-buffer **pinning** (`cuMemHostRegister`) for the kernel-context frame (`PTXByteBuffer` / `PTXKernelStackFrame`).
  - `useDependencies` correctness fix (recompute at `execute()` start, not latched in ctor).
- **Device-capability awareness:** `asyncEngineCount` (= **2** on this GPU → bidirectional copy overlap) and `concurrentKernels`; `PTXDeviceContext.maxConcurrentCopyStreams()` + gating so multi-stream falls back to single-stream on incapable HW.
- **CUDA-graph multi-stream capture** (fork/join, D2H primary) kept green throughout.
- **Goal 3(a) increment 2:** device-buffer ring + pipelined `withBatch` (flag `-Dtornado.batch.pipeline`), full ring.
- NVTX profiling annotations (named streams + issue ranges).

---

## 4. UNSTAGED work (in the working tree now — decide whether to commit)

`git status` on `feat/cuda-streams` shows:

| File | What it is |
|------|-----------|
| `TornadoVMInterpreter.java` (M) | **Goal 3(a) increment 3** — bounded ring `min(depth, chunks)` + per-`(bufferState, slot)` reuse dependency (`withSlotReuseDependency` / `recordSlotEvent`). |
| `TestBatches.java` (M) | adds `test256MBPipelined` (`testBatchRepeatedExecution` is already committed via the #882 merge). |
| `TestCUDAStreams.java` (M) | consolidated single correctness suite (streams + graph + Llama decode shapes). |
| `TestStreamsPerformance.java` (??) | new perf/overlap demonstration suite. |
| `docs/cuda-*.md` (??) | design docs (`cuda-streams-design.md`, this handoff, etc.). |

The tracked-file contents are identical to the previously validated state (verified against backup commit `2460b8b2d`; re-validated 2026-07-02: pipelined test passes at ring depth 3 and 2, full `TestBatches` 28/28 flag-off). Suggested commit grouping when ready: (1) Goal-3 inc 3 + pipeline test, (2) test-suite consolidation.

---

## 5. PENDING / next steps

1. ~~Consolidate branches~~ **done 2026-07-02** (§2). ~~Commit the unstaged work~~ **done** (ring, tests, docs committed; fix branch + develop merged). Remaining: force-push to update the open PR on `feat/cuda-streams`, delete the redundant `-pool`/`-perf`/backup branches.
1a. **Goal 3(b) — staged transfers: IMPLEMENTED + EVALUATED (2026-07-02, uncommitted).**
   - Mechanism: `PTXStream.enqueueStagedWrite` — ring of pinned slots (`cuMemAllocHost`, single block, lazy per stream, freed on destroy); per chunk: host-wait the slot's previous DMA event, multi-threaded `memcpyHostToHost` fill (new JNI), `cuMemcpyHtoDAsync` from the slot; returns the last chunk's event (dependency DAG unchanged); falls back to the direct path during graph capture. Wired in `PTXMemorySegmentWrapper.enqueueWrite` (non-batch, `>= tornado.ptx.staged.min.size`); large read-only segments skip whole-segment `cuMemHostRegister`. Flags: `tornado.ptx.staged.transfers` (off), `.chunk.size` (16MB), `.ring.depth` (4), `.min.size` (64MB), `.fill.threads` (cores/2 cap 8).
   - **Measured (1 GiB read-only, first execution):** cold mmap'd file (GPULlama GGUF shape; file written with O_DIRECT → page cache cold): **direct 420 ms (2.5 GB/s) vs staged 215 ms (5.0 GB/s) ≈ 1.96×** (staged reaches NVMe line rate). Warm resident pages: direct 57 ms (19 GB/s) vs staged 85 ms (12.7 GB/s) — staged loses (memcpy = 2× traffic vs register's page-table-only work; per-tensor register already pipelines with other tensors' DMAs). Conclusion: keep opt-in; enable for cold-mmap weight loading.
   - Tests: `TestCUDAStreams#testStagedFirstExecutionTransfer` (A/B, wrap + remainder + FIRST_EXECUTION residency across executes), `TestStreamsPerformance#testFirstExecutionUpload` (printed A/B, 8×128MB tensors, JVM-warm). Cold-file probe: scratchpad `ColdUploadProbe.java` (PRIVATE mmap + `FloatArray.fromSegmentShallow`; recreate the file with `dd oflag=direct` before each run; NOTE `/tmp` is tmpfs — keep the file on real disk).
   - Validated: TestCUDAStreams 10/10 (flag off AND on), TestBatches 31/31, pipeline+staged flags together green.
2. Optionally split the streams feature into review-sized PRs: **Goal 1 (concurrency)** vs **Goal 3 (batch pipeline)**.
3. Re-run perf profiling post-develop-merge to confirm overlap numbers still hold.
4. **Goal 2** — `executeAsync` + `cuLaunchHostFunc` (own PR).
5. **Goal 3(b) §7** — pinned host staging ring for the `FIRST_EXECUTION` copy-in (the ~800 ms weight load). `maxConcurrentCopyStreams()` is the planner input.

---

## 6. Key findings & gotchas (don't re-learn these)

- **SM saturation gates kernel overlap.** Large kernels (e.g. 6144 blocks × 1024 threads) fill the GPU → run strictly serially even on separate compute streams (`max-simultaneous = 1`). Overlap (`max-simultaneous = 4`, ~1.86×) only appears with **non-saturating** kernels. Correctness tests use big kernels (routing/ordering only); `TestStreamsPerformance` uses small kernels to show real overlap.
- **Same-direction transfer parallelism is useless.** H2D bandwidth is ~28.7 GB/s on 1/2/4 streams (PCIe/copy-engine bound). The win is **opposite-direction** overlap (H2D∥D2H ≈ 1.82×) + transfer∥compute — enabled by the separate H2D/D2H role streams. Do **not** build an H2D pool.
- **Overlap manifests in steady state**, not on the first (cold) execution (JIT + first-launch latency). Perf tests warm up / loop.
- **Batch pipeline (Goal 3a) measured impact:** ~1.44× on GPU-active transfer work (H2D∩D2H ≈ 8 ms genuine overlap); end-to-end wall on the micro-benchmark is diluted (~1.14×) by one-time compile + host per-chunk overhead. Bigger, transfer-bound out-of-core workloads should benefit more.
- **Repeated execution of a batched plan was broken upstream** (cuModuleGetFunction 400 → SIGSEGV → NPE). Root causes found & fixed:
  - PTX module cache returned an invalidated/unloaded module (`isCached`/`installSource` not validity-aware) — **our fix; in a separate develop PR** (upstream never touched `PTXCodeCache`).
  - persisted-object nested-list + empty cross-graph consume — **fixed upstream by PR #870** (`fix/persist-obj`), which our develop merge already includes. Our earlier local persisted fix was **superseded** and dropped.
- **`TestBatches` has an intermittent flake** post-merge (one run failed, passed on rerun) — likely allocation pressure across sequential large-batch tests, not our code. Worth confirming before a PR.
- **Pipelined repeated execution: FIXED (2026-07-02).** Two root causes, both in the working tree now:
  1. `TornadoVMInterpreter`: the per-object batch-chunk counters (`currentBatchNumberPerObject`) were never reset across `execute()` calls, so from the second execution on every per-chunk DEALLOC freed for real → per-chunk realloc churn → in pipelined mode the next chunk's H2D raced the previous chunk's D2H in the freed-and-reused buffer (stale results, no crash). Fix: reset the counters at the start of every batched execution.
  2. `PTXTornadoDevice.allocateObjects`: the batch buffer-reuse short-circuit skipped `allocate()`, which is what refreshes `setSizeSubRegion(batchSize)` — a buffer kept from a previous execution's *remainder* chunk silently copied too few bytes. Fix: set the sub-region on the reuse path too. (Same latent bug exists in the OCL/SPIRV/CUDA/Metal device classes — not touched, PTX-only branch.)
  - Test: `TestBatches#testBatchPipelinedRepeatedExecution` (A/B via `-Dtornado.batch.pipeline`; 40MB batch so the remainder chunk lands on ring slot 0). Validated: TestBatches 30/30 flag-off, pipelined tests pass at ring depth 3 and 2, TestCUDAStreams 9/9. The Goal-3a "single execute() only" limitation is **lifted**.
- **Pre-existing upstream limitation: ALSO FIXED (2026-07-02).** With `tornado.reuse.device.buffers=true` (the DEFAULT) any batched plan with a remainder chunk used to fail (`cuMemcpyHtoDAsync` INVALID_VALUE) — even serial, even on the FIRST execution: locked buffers (`isLockedBuffer`) make the pre-remainder dealloc a no-op, so the remainder chunk runs on the larger full-chunk buffer, and the batch H2D copied the full `bufferSize` past the end of the host segment. Fix: the batch H2D now honours the sub-region size like the blocking read already did (`PTXMemorySegmentWrapper.enqueueWrite` batch branch; `PTXArrayWrapper.enqueueWrite` gated on `batchSize > 0` so the read-only lazy-sync sub-region can't shrink non-batch copies). The sub-region is kept per-chunk-correct by `allocate()` + the `allocateObjects` reuse-path fix. Test: `TestBatches#testBatchWithRemainderAndBufferReuse` (flips the property to True, restores in finally; A/B pipelined; 3 executions). `TestBatches` still sets `tornado.reuse.device.buffers=False` in `before()` for the other tests. Validated: probe passes all 4 configs (32/40MB × serial/pipelined) under DEFAULT reuse; TestBatches 31/31 flag-off; pipelined tests green at depth 3 and 2; TestCUDAStreams 9/9. **The pre-existing halves of these fixes were ported CROSS-BACKEND (all 5 backends) on the separate fix branch `fix/batch-remainder-buffer-reuse`** (worktree `/home/orion/TornadoVM-fix-batch`, uncommitted, 17 files): validated 29/29 on PTX/OpenCL/CUDA backends and 24/24 non-lazy on SPIR-V (Intel iGPU; 3 Lazy tests SIGSEGV in the Level Zero driver on clean develop too — pre-existing, unrelated); Metal edits are identical-pattern but only compilable on macOS. On clean develop the remainder+reuse bug is a JVM SIGSEGV in libcuda (worse than the INVALID_VALUE our branch shows).

---

## 7. Environment, build, test, profile

- **JDK:** `sdk use java 21.0.2-open`. **Build:** `make BACKEND=ptx`. Then `source setvars.sh`.
- **GPU:** NVIDIA RTX 5090 Laptop (sm_120), CUDA 13.1 toolkit / driver 13.0. `asyncEngineCount=2`, `concurrentKernels=1`, 82 SMs.
- **Run a test:** `tornado-test --ea "FQCN#method"` (single method) or `tornado-test -V FQCN`. JVM props via `--jvm="-Dx=y"`.
- **Key suites:** `unittests.streams.TestCUDAStreams` (correctness), `unittests.streams.TestStreamsPerformance` (perf, printed not asserted), `unittests.batches.TestBatches`.
- **Relevant flags:** `-Dtornado.ptx.compute.streams=N`, `-Dtornado.batch.pipeline=true`, `-Dtornado.batch.pipeline.depth=N`.
- **Profile:** `nsys profile --trace=cuda,nvtx -o out tornado -ea [--jvm=...] -m tornado.unittests/uk.ac.manchester.tornado.unittests.tools.TornadoTestRunner --params "FQCN#method"`; analyze via `nsys export --type sqlite` + an overlap/`max-simultaneous` python pass. `compute-sanitizer` (`/usr/local/cuda-13.1/bin`) for device OOB; `-Xcheck:jni` for JNI misuse.

---

## 8. Related references

- Design: `docs/cuda-streams-design.md` (§5.4/§5.5 batch-chunk ring; §7 pinned host staging ring status note).
- Memory: `project_cuda_streams_design`, `project_cuda_graphs_streams`, `project_cuda_streams_perf`, `project_cuda_streams_goal3`, `reference_tornadovm_build_test_env`.
- Upstream: PR #870 (`fix/persist-obj`, merged to develop) supersedes the persisted-object half of the batch fixes.
- Separate open PR (develop): PTX module-recompile fix + `testBatchRepeatedExecution`.
