# Gate results

Each gate is go/no-go. Evidence and the profiling decision review are recorded per gate.

| Gate | Status | Date | Evidence |
|---|---|---|---|
| C0 repo + builds + devices | **passed** | 2026-10-09 | private repo (`gh repo view` → PRIVATE); `make BACKEND=opencl,cuda` on JDK 21.0.2 and `make jdk25 BACKEND=opencl,cuda` on JDK 25.0.2: BUILD SUCCESS; `tornado --devices` (both): 0:0 OpenCL RTX 4090, 0:1 OpenCL UHD 770, 1:0 CUDA RTX 4090 |
| C1 baseline reproducible | CUDA passed; OpenCL rerun pending | 2026-10-09 | CUDA run 1 = run 2 (1671 tests, 7 deterministic pre-existing failures); perf + jitLLM baseline in [baseline-performance.md](baseline-performance.md); OpenCL run 2 cut short by a `tornado-test` bug |
| C2 ⚑ dependency + phase matrix | **ready for review** | 2026-10-09 | static map ([graal-dependency-map.md](graal-dependency-map.md)), pipeline ([compiler-pipeline.md](compiler-pipeline.md)), matrix with measured hits for 42 phase rows ([phase-migration-matrix.md](phase-migration-matrix.md)), dynamic trace of the full CUDA suite ([phase-trace.md](phase-trace.md)): 1,242 sketches, 96,121 backend compilations, 70 top-level phases, 719 node classes |
| C3 ⚑ end-to-end design test | **passed, approved by the user 2026-10-09**: design test 3/3 on GPU; `TcCompiler` with `-Dtornado.tc.dump/dumpDir/verify/only/report`, JFR events `tc.Compilation`/`tc.Stage`, HTML report per compilation ([c3/*.report.html](c3/)) | 2026-10-09 | 3/3 kernels (grid-stride vector add, if/else diamond with block-argument merge and `sqrt`, KernelContext shared-memory tree reduction) compiled by TC only and run on the RTX 4090 via the prebuilt-task path, results equal to Java references; TC compile 4–21 ms per kernel; outputs in [c3/](c3/) |
| C4a/b/c frontend, passes, Access[] | **C4a in progress** | 2026-10-09 | `tc-frontend`: JVMS class-file reader, bytecode decoder, Braun SSA translator, API bindings; 11 Java test kernels translate to verified TC-SSA (lit goldens); the 3 C3 kernels run **from Java** on the RTX 4090 (3/3 PASS, see [examples.md](examples.md)); round-trip of all `tornado-unittests` kernels pending |
| C5a/b ⚑ structurizer, vertical slice | pending | | |
| C6 differential parity | pending | | |
| C7 performance + stability | pending | | |
| C8 Graal removed | pending | | |
| C10 Babylon adapter | pending | | |

## C3 notes (2026-10-09)

Run: `tornado -cp <tc classes> uk.ac.manchester.tornado.tc.gpu.DesignTest --params "1:0"` (module
`tornado-compiler/tc-gpu`). Pipeline per kernel: parse → verify (pre-ABI) → ABI lowering → verify
(post-ABI) → structure → emit CUDA → NVRTC through TornadoVM's prebuilt path.

| Kernel | Result | TC compile | First run (NVRTC + launch) |
|---|---|---:|---:|
| vector-add (grid-stride, 2 iterations/thread) | PASS | 21.4 ms (includes TC class warm-up) | 81.3 ms |
| branchy (diamond, block argument, `sqrtf`) | PASS | 4.0 ms | 13.8 ms |
| block-reduce (`__shared__`, `__syncthreads`, tree loop) | PASS | 4.7 ms | 14.6 ms |

**Finding (ABI):** the first vector-add run read the loop bound from the header of the write-only
output array and got 0: on the device, the header of an array that is not copied to the device is not
initialised. The Graal path never reads it because it folds array lengths into constants during task
specialisation. Consequence for TC: T1 (value specialisation of lengths) is required before the
frontend path goes live, and until then TC must not read the header of an array with no
host-to-device transfer. Recorded in `compatibility-contract.md` §1.

**Design decisions confirmed by C3:** block-argument SSA lowers to clean C (`while` loops, merges as
plain assignments); `array<T>` until ABI lowering keeps the frontend-facing IR readable and the ABI in
one pass; the four-region kernel signature and 16-byte header match the runtime.

### Decision review 1 (C3, profiling)

Measured with JFR (`jfr print --events tc.Stage`) on the GPU run and a warm headless benchmark
(`tc-runtime/src/test/bench/CompileTimeBench.java`, 2000 warm-up + 500 measured compiles per kernel).

| Measurement | Value |
|---|---|
| Cold first compile (TC classes loading and warming) | 21–24 ms |
| Next kernels in the same JVM, per stage | 0.3–1.3 ms each; verification ~25% |
| Warm compile per kernel, stage text rendered | 111 µs |
| Warm compile per kernel, text only when dumped or reported | **76 µs** |

Decisions:
1. **Changed:** stage text (IR, TC-S printing) is rendered only when a dump or report asks for it;
   it cost ~30% of a warm compile.
2. **Kept:** verification after every IR stage by default (~1 ms cold, a fraction warm) is small
   next to NVRTC (6–40 ms per kernel on first run). Revisit at C7 against the Graal compile time from
   the Phase 1 profiler data.
3. **Noted for Phase 9 (Leyden):** cold start dominates TC's own cost; CDS/AOT caching of TC classes
   is the lever, not pass tuning.
4. **Kept:** sealed records + pattern `switch` dispatch: no hot spot in dispatch showed up.

## C4a frontend coverage (2026-10-09)

`tc-coverage <tornado-unittests.jar> uk.ac.manchester.tornado.unittests --tasks` translates exactly the
methods registered as tasks (found statically from the `invokedynamic` sites that create
`TornadoFunctions.Task*` objects; `TaskSites`).

| | Kernels |
|---|---:|
| Task kernels in `tornado-unittests` | 1,317 |
| Translate to verified TC-SSA, no unresolved calls | 623 (47.3%) |
| … of which complete today (no `@Parallel`) | 190 |
| … of which use `@Parallel` (need T2 parallel-loop lowering) | 433 |
| Verified but call helper methods (need O1 inlining) | 95 |
| Invalid IR (HalfFloat objects, T9) | 8 |
| Rejected with a diagnostic | 591 |

Rejections by cause: unsupported calls 441 (TileContext views 170, KernelContext atomics/SIMD/MMA/
async copies, HalfFloat, vector/matrix/image collections, `AtomicInteger`), `new` 80 (value
objects), field access 42 (object parameters), non-constant local-array sizes 11 (need T1), arrays of
references 10, strings 3. Next by impact: T2 `@Parallel` (433), O1 inlining (95), then the API
families in Phase 6 order.
