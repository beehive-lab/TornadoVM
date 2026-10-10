# Parity matrix: TC vs Graal (Phase 6)

Differential results of the whole `tornado-test` suite on CUDA (RTX 4090, device 1:0), the Graal path
as reference ([baseline/tests-run1.json](baseline/tests-run1.json), `tests-cuda4090-run1`) and the same
suite with `-Dtornado.compiler=tc` ([baseline/run-tests.sh](baseline/run-tests.sh) with
`EXTRA_JVM`). Compared per test: pass/fail. Generated source is not compared.

## Run 1 (2026-10-10, fallback on)

| | Graal | TC (fallback on), run 1 | TC after the fixes (run 1 + rerun) |
|---|---:|---:|---:|
| tests compared | 1662 | 1662 | 1662 |
| PASS | 1515 | 1437 | **1514** |
| FAILED | 7 | 85 | 8 (Graal's 7 + one accepted difference) |
| CUDA configuration unsupported | 140 | 140 | 140 |
| kernel compilations by TC | – | 3026 | |
| fell back to Graal | – | 636 | |

9 memory tests were not run under TC (`TestStressDeviceMemory` 8, `TestReclaimReleasedBuffers` 1): the run
was stopped at a blocked JVM (see the last row below); they are rerun separately.

The 78 tests that differed in run 1 were rerun after fixes (`run-classes.sh rerun1`): 107/109 pass.
The two left: `TestComputePi` fails on Graal as well (baseline), and the one difference below.

| Difference | Cause | Status |
|---|---|---|
| `reductions.*` (42), `atomics.TestAtomicsWithReduction`, `grid.TestIrregularGridSizes#testReductionAtPrimeSize` | `@Reduce` compiled as a plain parallel loop: threads race on the result | fixed for now: TC refuses `@Reduce` (TC-E3201, Graal compiles it); the reduction rewrite is the next family |
| `batches.TestBatches` (26), `api.TestMultipleCopyOuts` (2) | batched execution: each device buffer holds one chunk, TC folded the full length and indexed past it (CUDA status 700), which poisoned the context for the following tests | fixed for now: TC refuses batched compilations (TC-E3202); per-chunk lengths/offsets todo |
| `codegen.TestPrivateArrayZeroInit` (2) | `new int[n]` not zero-filled | fixed: private arrays are declared `= {}` |
| `profiler.TestProfiler` (3) | TC compile time not reported | fixed: reported as the task's compiler time |
| `fails.BitOpsUnsupportedFail#testReverseUnsupported` | expects the compiler to reject `Integer.reverse`; TC compiles its JDK bytecode | **accepted difference**: TC is more capable, result checked correct by the other `Integer.reverse` tests |
| memory stress tests (9, not run) | with `-Dtornado.tc.verbose=true` each compile prints a line; `tornado-test` does not drain the pipe until exit, so the hot loop blocked on stdout | harness issue, not TC: full runs use `tornado.tc.stats` only |

## Coverage: kernels TC compiles, per family (run 1)

`TC share` = kernel compilations by TC / all compilations of the family; the rest fell back to Graal
with a diagnostic. This is the Phase 6 work list.

| Family | Graal PASS (baseline) | TC kernels | fell back to Graal | TC share |
|---|---:|---:|---:|---:|
| tasks | 32 | 1533 | 14 | 99% |
| multithreaded | 5 | 420 | 0 | 100% |
| tile | 224 | 21 | 261 | 7% |
| kernelcontext | 203 | 97 | 160 | 37% |
| api | 135 | 224 | 0 | 100% |
| arrays | 129 | 138 | 0 | 100% |
| reductions | 55 | 125 | 1 | 99% |
| vectortypes | 71 | 12 | 79 | 13% |
| math | 75 | 76 | 0 | 100% |
| foundation | 40 | 42 | 5 | 89% |
| branching | 43 | 34 | 9 | 79% |
| streams | 13 | 38 | 0 | 100% |
| numpromotion | 46 | 36 | 0 | 100% |
| loops | 33 | 30 | 3 | 90% |
| codegen | 31 | 33 | 0 | 100% |
| compute | 31 | 30 | 3 | 90% |
| atomics | 27 | 2 | 27 | 6% |
| matrices | 35 | 0 | 27 | 0% |
| grid | 12 | 18 | 1 | 94% |
| logic | 19 | 18 | 1 | 94% |
| images | 19 | 0 | 18 | 0% |
| profiler | 11 | 16 | 0 | 100% |
| quantization | 7 | 12 | 4 | 75% |
| fields | 13 | 0 | 15 | 0% |
| executor | 7 | 10 | 0 | 100% |
| compiler | 7 | 9 | 0 | 100% |
| cublas | 34 | 7 | 0 | 100% |
| cutlass | 25 | 6 | 0 | 100% |
| fails | 11 | 2 | 4 | 33% |
| memory | 15 | 5 | 0 | 100% |
| dynsize | 2 | 5 | 0 | 100% |
| functional | 5 | 3 | 1 | 75% |
| pointers | 2 | 4 | 0 | 100% |
| prebuilt | 7 | 3 | 0 | 100% |
| cufft | 8 | 3 | 0 | 100% |
| nvtx | 4 | 2 | 0 | 100% |
| cusparse | 16 | 2 | 0 | 100% |
| instances | 2 | 0 | 2 | 0% |
| warmup | 2 | 2 | 0 | 100% |
| vm | 2 | 2 | 0 | 100% |
| cudnn | 11 | 1 | 0 | 100% |
| temporary | 1 | 1 | 0 | 100% |
| batches | 30 | 1 | 0 | 100% |
| lambdas | 1 | 1 | 0 | 100% |
| flatmap | 1 | 1 | 0 | 100% |
| bitsets | 1 | 0 | 1 | 0% |
| parameters | 3 | 1 | 0 | 100% |

(Reductions now fall back by design until the reduction rewrite lands.)

## Fallback reasons, ranked (run 1)

| Kernels | Reason | Family / plan item |
|---:|---|---|
| 245 + 4 | `TileContext.view` / `viewStrided` | tile (CUDA Tile mode last) |
| 44 + 10 + 6 | `KernelContext.mmaFragment` / `mmaFragmentInt` / `mmaLoadAInt8` | kernelcontext (tensor cores) |
| 27 | object allocation (`new`) other than HalfFloat | vectortypes / fields |
| 26 | instance-method tasks (TC-E1005) | lambdas / instances |
| 20 + 18 + 4 | `simdSum` / `simdShuffleDown` / `simdBallot` | kernelcontext (warp intrinsics) |
| 17 | static fields of test classes | fields |
| 13 + 13 + 5 | `AtomicInteger`, `KernelContext.atomicAdd` / `atomicMax` | atomics |
| 10 | `tile.DType` enum constants | tile |
| 8 | `KernelContext.asyncCopyToLocal` | kernelcontext |
| 7 + 6 + 5 + 4 + 4 | `Matrix2D*.get`, `getNumRows`, `Matrix3DFloat4` | matrices (object fields: `set` done) |
| 6 | `ImageFloat` | images |
| 6 + 5 + 4 + 4 + 4 | `VectorHalf`, `VectorInt2/3`, `VectorFloat3/4` | vectortypes |
| 5 | arrays of references | vectortypes |

## Order (plan): kernelcontext → reductions → vectortypes → atomics → fields/instances → lambdas → tile.
