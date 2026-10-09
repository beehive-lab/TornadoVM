# Optimisations TC must deliver

The authoritative list of optimisations and kernel transformations TC owes, derived from what the
Graal path does for TornadoVM today ([phase-migration-matrix.md](phase-migration-matrix.md)).
Each row has a gate and a verification method. Status values: `todo`, `wip`, `done (gate)`.

Priorities:
- **P0**: required for parity (functional or performance); must be done by the gate shown.
- **P1**: opt-in or minor today; needed before Graal removal (C8) unless measured unnecessary.
- **P2**: beyond parity; only after C7 measurements justify it.

Verification keys: **G** golden IR test (FileCheck-style, before/after), **A** Alive2 proof on
exported `.ll` (scalar rules), **D** differential unittest family vs Graal, **P** performance
comparison (kernel time, `ncu`) vs Graal baseline, **V** verifier after the pass.

## 1. Generic optimisations (`tc-opt`)

| # | Optimisation | Replaces on the Graal path | Pri | Gate | Verify | Status |
|---|---|---|---|---|---|---|
| O1 | **Inline-all** for kernel-reachable methods, recursion check, size-cap diagnostic | `InliningPhase` + `TornadoFull/PartialInliningPolicy` | P0 | C5b | G V D | todo |
| O2 | **Constant folding** with Java semantics (int overflow, `div`/`rem`, shifts masked, `f2i.sat`, NaN) | `CanonicalizerPhase` (folding) | P0 | C4b | G A V | todo |
| O3 | **Algebraic simplification** (identities, strength reduction `x*2^k→x<<k`, double negation, compare canonical forms) | `CanonicalizerPhase` (simplification) | P0 | C4b | G A V | todo |
| O4 | **Branch folding / CFG simplification** (constant conditions, empty blocks, block merging, jump threading of trivial blocks) | `CanonicalizerPhase` (control flow) | P0 | C4b | G V | todo |
| O5 | **Dead-code elimination** (ops and blocks; unused allocations incl. value types) | `DeadCodeEliminationPhase`, `TornadoValueTypeCleanup` | P0 | C4b | G V | todo |
| O6 | **Global value numbering / CSE** (dominator-based, pure ops; loads only with memory-effect proof) | implicit Graal GVN | P0 | C4b | G V P | todo |
| O7 | **Conditional elimination** (conditions implied by dominating branches) | `IterativeConditionalEliminationPhase` | P0 | C5b | G V | todo |
| O8 | **Loop analysis**: loop forest (Havlak), induction variables, counted-loop and trip-count facts (affine subset) | `LoopsData`, `CountedLoopInfo` | P0 | C4b | G | todo |
| O9 | **Full loop unroll** of small constant-trip loops; Tornado policy (skip nested control flow, tile-aware: keep tile loops rolled except K-loop on sm_80+) | `LoopFullUnrollPhase`, `TornadoLoopUnroller`, `CUDATileLoopPolicies` | P0 | C6 | G D P | todo |
| O10 | **Load forwarding + loop-invariant code motion** over explicit memory effects (never hoist private-memory reads out of parallel loops) | `FloatingReadPhase` (Tornado's modified copy), `SchedulePhase` placement | P0 | C6 | G D P | todo |
| O11 | **FMA contraction** `a*b+c → fma` for f32/f64 when `tornado.enable.fma` (default on) | `CUDAFMAPhase` (+ OCL/Metal copies) | P0 | C5b | G D P | todo |
| O12 | **rsqrt peephole** `1/sqrt(x) → rsqrt(x)` when `tornado.enable.mathOptimizations` (default on) | `InverseSquareRootPhase` | P0 | C5b | G D | todo |
| O13 | **Integer reassociation** for address arithmetic only (never floating point) | `ReassociationPhase` | P1 | C7 | G A P | todo |
| O14 | **Partial loop unroll** by power-of-two factor (opt-in `tornado.experimental.partial.unroll`) | `TornadoPartialLoopUnrollPhase` | P1 | C8 | G D P | todo |

## 2. TornadoVM kernel transformations (`tc-tornado`)

Not optimisations in the classic sense, but required for correct and fast kernels; listed here so
that nothing the Graal path does is lost.

| # | Transformation | Replaces | Pri | Gate | Verify | Status |
|---|---|---|---|---|---|---|
| T1 | **Value / task specialisation** (required: device headers of write-only arrays are uninitialised, C3 finding): fold boxed-primitive arguments and array lengths into constants; under `GridScheduler` with dynamic bounds, read loop bounds from kernel-context slots instead | `TornadoTaskSpecialisation` | P0 | C5b | G D | wip: array lengths and scalar arguments folded (`ValueSpecialisation`), GPU-verified (`@Parallel` kernels); GridScheduler dynamic bounds todo |
| T2 | **Parallel loop scheduling**: `@Parallel` / auto loops → thread-id + grid-stride per `TargetCaps.schedule` | `TornadoParallelScheduler`, `TornadoApiReplacement` | P0 | C5b | G D | wip: `@Parallel` read from type annotations (frontend), grid-stride lowering with innermost = x (`ParallelLoopLowering`), 1D and 2D GPU-verified; CPU per-block schedule and auto-paralleliser todo |
| T3 | **Parallel domain (shape) analysis** for launch configuration | `TornadoShapeAnalysis` | P0 | C5b | D | todo |
| T4 | **Reductions**: `@Reduce` pattern recognition → local-memory tree + barriers + partials/atomics | `TornadoReduceReplacement`, `ReduceGPUSnippets`, `TornadoLocalMemoryAllocation` | P0 | C6 | G D P | todo |
| T5 | **KernelContext intrinsics** (ids, sizes, barriers, local arrays) | `TornadoKernelContextReplacement`, `TornadoCUDAIntrinsicsReplacements` | P0 | C5b | G D | wip: ids, sizes, barriers, shared local arrays bound in the frontend and GPU-verified; atomics, SIMD, MMA, async copies todo |
| T6 | **Access[] dataflow analysis** (READ/WRITE per parameter, merged across callees and launches) | `TornadoDataflowAnalysis`, sketcher merge | P0 | C4c | diff vs Graal for every unittest task | todo |
| T7 | **ABI lowering**: `array<T>` → `ptr<global>` + header; parameter-object field flattening; native-array length at header offset 0 | `TornadoFieldAccessFixup`, `TornadoPanamaSegmentsHeaderPhase`, `TornadoNativeTypeElimination` | P0 | C3 | G D | wip: arrays done and GPU-verified at C3; object/field flattening todo |
| T8 | **Private and shared memory**: private arrays as fixed-size locals; phi/branch-selected private arrays via pointer copy | `TornadoNewArrayDevirtualizationReplacement`, `TornadoPrivateArrayPiRemoval`, `TornadoPanamaPrivateMemory`, `TornadoFixedArrayCopyPhase` | P0 | C6 | G D | todo |
| T9 | **Half / bf16** values and vector offsets | `TornadoHalfFloatReplacement`, `TornadoHalfFloatVectorOffset`, `TornadoHalfFloatFixedGuardElimination` | P0 | C6 | G D | todo |
| T10 | **Value types** (`@Vector` objects) as SSA vectors from the frontend | `TornadoValueTypeReplacement`, `PartialEscapePhase` (subset) | P0 | C6 | G D | todo |
| T11 | **Atomics** parameter slots and placement | `TornadoAtomicsParametersPhase`, `TornadoAtomicsScheduling` | P0 | C6 | D | todo |
| T12 | **Batch index offset** for batched execution | `TornadoBatchGlobalIndexOffset`, `TornadoBatchFunctionAnalysis` | P0 | C6 | D | todo |
| T13 | **Numeric promotion** kept explicit (narrow/extend around arithmetic) | `TornadoNumericPromotionPhase` | P0 | C4b | G A | todo |
| T14 | **Target capability checks** (fp64, fp16, MMA, tile, CDP) → same exception types | `CUDA{FP64,FP16,TensorCore,Tile,DynamicParallelism}SupportPhase` | P0 | C6 | D | todo |
| T15 | **Auto-paralleliser** (opt-in `tornado.parallelise.auto`) | `TornadoAutoParalleliser` | P1 | C8 | D | todo |

## 3. Code-generation quality (`tc-lower`, `tc-codegen`)

| # | Optimisation | Replaces | Pri | Gate | Verify | Status |
|---|---|---|---|---|---|---|
| Q1 | **Copy coalescing in out-of-SSA** (avoid redundant edge copies; parallel-copy sequencing with minimal temporaries) | Graal LIR moves | P0 | C5a | G | wip: sequencing with one temporary per cycle done (`tc-lower/…/Structure/copies.tcir`); coalescing of non-interfering variables todo |
| Q2 | **Expression folding** of pure single-use values into C expression trees | — (TC-specific readability) | P0 | C5b | G | done (C5b pending): constants, allocas, pure single-use same-block values |
| Q3 | **Counted-loop recovery** to `for(init; cond; step)` | `TornadoLoopCanonicalization` + structured emitter | P0 | C5a | G | todo |
| Q4 | **Global-load batching**: issue independent global loads of a block together (memory-level parallelism) | `CUDAGlobalLoadBatching` | P0 | C7 | G P | todo |
| Q5 | **Infinity / NaN / special-constant spelling** per dialect | `InfinityReplacementPhase` | P0 | C5b | G | todo |
| Q6 | **Variable naming from the local-variable table**, built-in name clash prefixing | Graal backend naming | P0 | C3 | G | todo |

## 4. Beyond parity (P2; only after C7 data)

| # | Candidate | Motivation |
|---|---|---|
| X1 | Loop vectorisation / SLP into TornadoVM vector types | possible bandwidth gains; vector types are explicit today |
| X2 | Loop tiling for stencils and matrix multiply | possible locality gains |
| X3 | Device functions instead of inline-all | compile time and code size for large kernels |
| X4 | Constant-argument specialisation beyond T1 (ILGPU-style) | fewer instructions in hot kernels |

## 5. Explicitly not needed

Guard lowering, deopt-to-guard conversion, frame-state assignment, value proxies, floating-read
re-pinning, address lowering by node, LIR pre/post-allocation stages, null/bounds-check
elimination, exception suppression: these exist only because of Graal's IR model. TC's frontend
emits no guards, deopts, proxies or floating nodes. See
[phase-migration-matrix.md](phase-migration-matrix.md) §C for the per-phase reasoning.
