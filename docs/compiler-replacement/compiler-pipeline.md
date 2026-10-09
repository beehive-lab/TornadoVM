# Compiler pipeline: CUDA backend end to end (Phase 2, static)

Recorded 2026-10-09 on `compiler/main`. Path abbreviations (`RT`, `DC`, `CU`, `OCL`, `MTL`) are
the ones defined in `graal-dependency-map.md`. Behaviour is described in our own words; no code
is reproduced. The "OpenCL / Metal" column only records how those backends differ.

## 0. One-paragraph summary
Building a task graph triggers a **sketch** per (method, backend, device). The bytecode is parsed
with the *backend's* graph-builder plugins (so the sketch already contains CUDA-specific nodes),
and a backend-neutral **sketch tier** then runs. The sketch tier also computes the parameter
`Access[]`. On first execution the device copies the sketch and specialises it to the actual
arguments (scalars become constants). It then runs the CUDA **high, mid and low tiers**, builds
**LIR**, and prints **CUDA C++ text** through a structurizer and an "assembler" that writes
strings. Non-inlined callees and device-launched kernels are compiled into the same translation
unit. The text goes to **NVRTC** (or `nvcc -tilecubin` for CUDA Tile) through the code cache.

## 1. Step-by-step flow

| # | Step | Class / method (path) | What happens | OpenCL / Metal |
|---|---|---|---|---|
| 1 | Task added to graph | `RT/tasks/TornadoTaskGraph.java` `addInner` (`:853`), also `updateInner` (`:835`) | For a `CompilableTask`, resolves the `Method` to a JVMCI `ResolvedJavaMethod`, takes the backend's `Providers` and `TornadoSuitesProvider`, and fires a `SketchRequest`. Then it **blocks** on `TornadoSketcher.lookup` and keeps the sketch graph (`compilationGraph`, later handed to `ReduceTaskGraph`) and `accesses` (`:869`). Those accesses feed the TornadoVM bytecode (`insertVariable(arg, accesses[i])`, `:896`). Prebuilt/library tasks bring explicit accesses and skip this step. | same code; the provider/suites come from the OCL/Metal backend |
| 2 | Sketch request | `RT/sketcher/SketchRequest.java` (`run` → `TornadoSketcher.buildSketch`) | Value object holding method, providers, graph-builder suite, sketch tier, backend/device index and the chain of device-launch ancestors. | same |
| 3 | Sketch scheduling + cache | `RT/sketcher/TornadoSketcher.java` `buildSketch(SketchRequest)` (`:121`), `lookup` (`:87`) | Cache = method → list of futures, one per (backend, device). The first request submits a callable to `TornadoCoreRuntime.getTornadoExecutor()` (the build is **asynchronous and concurrent**), and `lookup` waits on the future and re-throws inliner/bailout errors. | same cache; keyed by backend index, so each backend has its own sketch |
| 4 | Graph build (bytecode parse) | `TornadoSketcher.buildSketch(...)` (`:137`) applies `graphBuilderSuite`, which was built by `CU/graal/CUDASuitesProvider.java` `createGraphBuilderSuite` (Graal `GraphBuilderPhase`, snippet-default config, eager resolving). Then it runs a dead-code pass. | Plugins (`CU/graal/CUDAHotSpotBackendFactory.java` `createGraphBuilderPlugins`, `:129-146`), in this order: parameter plugins (vector-typed params), new-instance node plugins (vector types, `AtomicInteger`), Graal's standard Java intrinsics, then `CU/graal/compiler/plugins/CUDAGraphBuilderPlugins.registerInvocationPlugins` (`:159`). That last one covers optional inline-during-parse, FP16/FP8/BF16 conversions, native-array access, TornadoVM intrinsics, **KernelContext** (thread ids, barriers, local arrays, atomics, SIMD/warp ops, MMA, cp.async, swizzled loads), **TileContext** (`CUDATileGraphBuilderPlugins`), `TornadoMath` (`CUDAMathPlugins`), OpenCL-style builtins, vector types, `TornadoAtomicInteger`, `HalfFloat`, memory-access and quantization helpers. **Plugins turn API calls directly into CUDA nodes during parsing.** The kernel name is checked against OpenCL reserved tokens (also for CUDA). | Plugin classes `OCL/graal/compiler/plugins/*`, `MTL/graal/compiler/plugins/*` produce OCL/Metal node classes |
| 5 | Sketch tier | `RT/graal/compiler/TornadoSketchTier.java` (shared by all backends, created via `CU/graal/compiler/CUDACompilerConfiguration.createSketchTier`) | Phases in order: `TornadoNumericPromotionPhase` → canonicalizer → [if Graal `Inline`] `InliningPhase` (partial or full policy) → DCE → [if `ConditionalElimination`] canonicalizer + `IterativeConditionalEliminationPhase` → `TornadoStampResolver` → `TornadoHalfFloatFixedGuardElimination` → `TornadoNativeTypeElimination` → `TornadoReduceReplacement` → `TornadoApiReplacement` (`@Parallel`) → `TornadoKernelContextReplacement` → `TornadoAutoParalleliser` (off by default) → **`TornadoDataflowAnalysis` (Access[])** → `TornadoPanamaPrivateMemory` → `TornadoBatchFunctionAnalysis`. | identical tier (same class) |
| 6 | Callee / child sketches | `TornadoSketcher.buildSketch(...)` (`:164-228`) | Every remaining invoke target that has bytecode is sketched recursively, and so is every `DeviceKernelLaunch` node's target (CUDA dynamic parallelism; cycles are detected). Callee/child accesses are **merged into the caller's `Access[]`** by bitwise OR (`mergeAccesses`, `:261`, `:291`), and a cyclic child is assumed READ_WRITE on all its object params. The result is a `Sketch(graph copy, Access[], batchWriteThreadIndex)` (`RT/sketcher/Sketch.java`). | same |
| 7 | Device compile entry | `CU/runtime/CUDATornadoDevice.java` `installCode` (`:412`) → `compileTask` (`:255`); prebuilt → `compilePreBuiltTask` (`:315`) | Looks up the sketch, returns cached code when `!task.shouldCompile()`, and **copies the sketch's `Access[]` into the task metadata**. Under profiler `TASK_COMPILE_GRAAL_TIME` it calls `CUDACompiler.compileSketchForDevice`, then remaps atomics parameters through `TornadoAtomicIntegerNode.globalAtomicsParameters` (static map) and, under `TASK_COMPILE_DRIVER_TIME`, calls `deviceContext.installCode(planId, result)`. | `OCL/runtime/OCLTornadoDevice.java:269,291`; `MTL/runtime/MetalTornadoDevice.java:280,302` (no atomics remap difference) |
| 8 | Kernel compile driver | `CU/graal/compiler/CUDACompiler.java` `compileSketchForDevice` (`:368`, **`static synchronized`**: one CUDA compile at a time per JVM) | Copies the sketch graph and builds `BatchCompilationConfig` (threads, batch number/size). It calls `Request.execute` → `compile` (`:129`) → `emitFrontEnd` (`:163`) → `emitBackEnd` (`:200`). Then a **work-list** compiles each non-inlined callee as a `__device__` function and each device-launched kernel as a `__global__`, each from its own sketch (args = null, so no specialisation). Their text is prepended so definitions precede uses, and forward declarations are added for device kernels (`deviceKernelDeclarations`, `:520`). | `OCL/graal/compiler/OCLCompiler.java`, `MTL/graal/compiler/MetalCompiler.java` follow the same shape (no device-launch handling) |
| 9 | Front end: specialisation context | `CUDACompiler.emitFrontEnd` | Installs (method, args, meta) into the **mutable shared** `CUDACanonicalizer` custom simplification (currently a no-op), builds a `TornadoHighTierContext` carrying the **actual argument values**, and runs high → mid → low tiers with their respective contexts. The graph is not re-parsed (`buildGraph=false`). | same |
| 10 | High tier | `CU/graal/compiler/CUDAHighTier.java` | Order in §2. Key Tornado work: parameter specialisation, `@Parallel` → thread-id scheduling, intrinsics/local memory, guard suppression, high lowering (which also expands reductions through Graal snippets `CU/graal/snippets/ReduceGPUSnippets.java`). | OCL/Metal high tiers are the same sequence minus `CUDAKernelContextNullCheckElimination`, with `Tornado{OpenCL,Metal}IntrinsicsReplacements` |
| 11 | Mid tier | `CU/graal/compiler/CUDAMidTier.java` | Order in §2: Panama header offset, exception/bounds-check removal, Tornado floating reads, guard lowering, optional partial unroll, mid lowering, frame-state assignment, reassociation. | identical sequence |
| 12 | Low tier | `CU/graal/compiler/CUDALowTier.java` | Order in §2: device-capability gates (tensor core, dynamic parallelism, tile, FP64, FP16), low lowering, address lowering (`CU/graal/lir/CUDAAddressLowering.java`), loop canonicalisation, FMA, rsqrt, infinity rewrite, atomics placement, compressed-oops field addresses, final schedule (latest, out of loops). | no TensorCore/DynamicParallelism/Tile gates; `OCLFMAPhase`/`MetalFMAPhase` etc. |
| 13 | LIR build | `CUDACompiler.emitLIR0` (`:236`): Graal `LinearScanOrder`, `CUDABackend.newLIRGenerator`/`newNodeLIRBuilder`; `CU/graal/compiler/CUDALIRGenerationPhase.java` walks blocks (predecessors first, skipping back-edges) and calls `CUDANodeLIRBuilder.doBlock` | `CU/graal/compiler/CUDANodeLIRBuilder.java` (941 LOC) turns each scheduled node into LIR. It handles ifs/loops/switch/short-circuit logic as LIR control ops, phi moves at merges/loop ends, direct calls, and recognises the KernelContext parameter. `CU/graal/compiler/CUDALIRGenerator.java` supplies arithmetic/compare/atomic/switch emission through `CU/graal/lir/CUDAGenTool`, `CUDAArithmeticTool` and `CUDABuiltinTool`; LIR ops live in `CU/graal/lir/*` (CUDALIRStmt, CUDAControlFlow, CUDABinary/Unary/Ternary, CUDATileStmt, ...). `emitLowLevel` (`:279`) runs Graal's **pre-allocation LIR stage** and the **empty** `TornadoAllocationStage`. The post-allocation stage is constructed but never applied. **Values stay virtual registers, which become C locals.** | `OCLNodeLIRBuilder`/`MetalNodeLIRBuilder` analogues |
| 14 | Text emission | `CUDACompiler.emitCode` (`:288`) → `CU/graal/backend/CUDABackend.java` `emitCode` (`:205`): `emitPrologue` (`:439`) → `crb.emit(lir)` → epilogue (closing brace) | Prologue prints the signature (§3), then **declares every LIR variable up front, grouped by kind** (`emitVariableDefs`, `:281`). `CU/graal/compiler/CUDACompilationResultBuilder.java` `emit` (`:242`) hands the CFG to `CU/graal/compiler/CUDAStructuredControlFlow.java`. That class builds a nested region tree from the dominator tree, loop tree and `DC/common/compiler/cfg/PostDominators.java`, and prints braces/else/case scaffolding, while each block's straight-line LIR ops print their own statements via `CU/graal/asm/CUDAAssembler.java` (an `Assembler` subclass that appends text). Per block, `CUDAGlobalLoadBatching.reorder` may reorder LIR instructions (`:340`). `finish` (`:261`) **prepends headers on demand**: FP8, BF16 and fp16 preambles plus the tile preamble (`CU/graal/backend/CUDAPreamble.java`). | `OCLStructuredControlFlow`, `MetalStructuredControlFlow` (3 × ~428 LOC, same algorithm); OCL/MSL preambles (`#pragma OPENCL EXTENSION`, `metal_stdlib`) |
| 15 | Result | `CU/graal/compiler/CUDACompilationResult.java` (extends Graal `CompilationResult`) | Holds id, entry name, meta, target-code bytes, the inlined/non-inlined method lists, device launches and the device-launch tree. | `OCLCompilationResult`, `MetalCompilationResult` |
| 16 | Install | `CU/CUDADeviceContext.java` `installCode` (`:982`/`:987`) → `CU/CUDACodeCache.java` `installSource` (`:236`) | Checks the on-disk cubin cache (key = hash of source + flags + toolchain, `moduleCacheKey`, `:122`). A **tile source (contains `__tile_global__`)** goes to `CU/CUDATileCompiler.compile` (nvcc → cubin → `createProgramWithBinary`). SPIR-V input goes to `createProgramWithIL`. Otherwise `createProgramWithSource` (`:280`) → `program.build(flags)` (`:299`) → `CU/CUDAProgram.java:98` → `CU/ffm/CUDACompiler.build` (`:82`, `nvrtcCompileProgram` at `:473`, then cubin/PTX fetch) → `clCreateKernel(entry)` (`:317`) → a new `CU/graal/CUDAInstalledCode.java` (extends JVMCI `InstalledCode`). | OpenCL: `OCL/OCLCodeCache.java` → `clBuildProgram`; Metal: `MTL/MetalCodeCache.java` → `MTL/ffm/MetalAPI.newLibraryWithSource` (`:228`) |
| 17 | Launch | `CU/graal/CUDAInstalledCode.java` `setKernelArgs` (`:149`), `submitWithEvents`; scheduler `CUDAScheduler` or `CU/scheduler/CUDATileScheduler.java` | Marshals the ABI slots and the arguments (§3), writes the kernel-context frame (skipped when unchanged), and enqueues. | OCL/Metal analogues |

TC hook (plan): at step 7, a `-Dtornado.compiler=tc` branch calls `TcCompiler` and installs
through the source-install entry (`CUDADeviceContext.installCode(planId, meta, id, entry, source)`,
`:987`), the same one `compilePreBuiltTask` uses. Steps 1–6 must then be skipped or replaced
(they currently run unconditionally inside `addInner`).

## 2. Tier phase lists, in constructor order

Phases in brackets are option-gated. TornadoVM phases are in **bold**; the rest are Graal built-ins.

**Graph-builder suite** (`CUDASuitesProvider.createGraphBuilderSuite`): `GraphBuilderPhase`.
`TornadoSketcher` then runs `DeadCodeEliminationPhase(Optional)`.

**Sketch tier** (`RT/graal/compiler/TornadoSketchTier.java`): see step 5.

**High tier** (`CU/graal/compiler/CUDAHighTier.java`):
1. Canonicalizer (with the custom simplification `CUDACanonicalizer`)
2. [Inline] `InliningPhase` → DCE → [ConditionalElimination] canonicalizer + `IterativeConditionalEliminationPhase`
3. **`CUDAKernelContextNullCheckElimination`**
4. **`TornadoTaskSpecialisation`** (iterates up to 15×, internally running canonicalizer, **`TornadoLoopUnroller`** with tile-aware `CUDATileLoopPolicies`, **`TornadoValueTypeReplacement`** and DCE)
5. **`TornadoBatchGlobalIndexOffset`**
6. **`TornadoFieldAccessFixup`**
7. canonicalizer → DCE → canonicalizer
8. **`TornadoNewArrayDevirtualizationReplacement`**
9. **`TornadoHalfFloatReplacement`**
10. [PartialEscapeAnalysis] `PartialEscapePhase`
11. **`TornadoPrivateArrayPiRemoval`**
12. **`TornadoValueTypeCleanup`**
13. [OptConvertDeoptsToGuards; forced true in `RT/TornadoCoreRuntime.java:137`] `ConvertDeoptimizeToGuardPhase`
14. **`TornadoShapeAnalysis`** → canonicalizer
15. **`TornadoParallelScheduler`**
16. `SchedulePhase(EARLIEST)` → `LoopFullUnrollPhase` (tile-aware policies) → canonicalizer → DCE → `SchedulePhase(EARLIEST)`
17. `HighTierLoweringPhase` (via `CU/graal/CUDALoweringProvider.java`, which extends Graal `DefaultJavaLoweringProvider`: thread-id nodes, reductions via snippets, indexed load/store, atomics, new arrays → private/local arrays, div/rem, float convert)
18. **`TornadoCUDAIntrinsicsReplacements`**
19. **`TornadoLocalMemoryAllocation`**
20. **`ExceptionSuppression`**

**Mid tier** (`CU/graal/compiler/CUDAMidTier.java`):
**`TornadoPanamaSegmentsHeaderPhase`** → **`ExceptionCheckingElimination`** → canonicalizer →
**`BoundCheckEliminationPhase`** → **`ExceptionCheckingElimination`** → [OptFloatingReads]
**`TornadoFloatingReadReplacement`** → canonicalizer → [ConditionalElimination]
`IterativeConditionalEliminationPhase` → `RemoveValueProxyPhase` → `GuardLoweringPhase` →
canonicalizer → [`tornado.experimental.partial.unroll`] **`TornadoPartialLoopUnrollPhase`** →
`MidTierLoweringPhase` → `FrameStateAssignmentPhase` → [ReassociateExpressions]
`ReassociationPhase` → canonicalizer.

**Low tier** (`CU/graal/compiler/CUDALowTier.java`):
**`CUDATensorCoreSupportPhase`** → **`CUDADynamicParallelismSupportPhase`** →
**`CUDATileSupportPhase`** → **`CUDAFP64SupportPhase`** → **`CUDAFP16SupportPhase`** →
`LowTierLoweringPhase` → [ConditionalElimination] `IterativeConditionalEliminationPhase` →
[`tornado.enable.fix.reads`, default on] `FixReadsPhase` → `UseTrappingNullChecksPhase` →
**`TornadoFixedArrayCopyPhase`** → `AddressLoweringByNodePhase(CUDAAddressLowering)` →
`DeadCodeEliminationPhase(Required)` → **`TornadoHalfFloatVectorOffset`** →
**`TornadoLoopCanonicalization`** → [`tornado.enable.fma`] **`CUDAFMAPhase`** →
[`tornado.enable.mathOptimizations`] **`InverseSquareRootPhase`** → **`InfinityReplacementPhase`** →
**`TornadoAtomicsParametersPhase`** → **`TornadoAtomicsScheduling`** → **`CUDAFieldCoopsAccessPhase`** →
`SchedulePhase(LATEST_OUT_OF_LOOPS)` → [`tornado.feature.extraction`] **`TornadoFeatureExtraction`** →
[low-tier IGV dump] **`DumpLowTierGraph`**.

**LIR stages** (`CU/graal/compiler/CUDACompilerConfiguration.java`): Graal
`PreAllocationOptimizationStage` (applied) and `TornadoAllocationStage` (empty, applied).
`PostAllocationOptimizationStage` is constructed but not applied by `CUDACompiler.emitLowLevel`.

Graal option overrides that shape all of this (`RT/TornadoCoreRuntime.java:128-137`): the
defaults come from `HotSpotGraalOptionValues`, with `MatchExpressions=true`,
`RemoveNeverExecutedCode=false`, `OptConvertDeoptsToGuards=true`, LIR constant-load optimisation off
and redundant-move elimination off. Any `-Dgraal.*` property (e.g. the IGV flags) feeds in here.

## 3. Kernel signature / ABI

Where it is decided:
- `CU/graal/CUDAArchitecture.java` (`:48-60`, `getABI` `:134`): the four **reserved ABI regions** and their order.
- `CU/graal/backend/CUDABackend.java` `emitPrologue` (`:439`) + `emitMethodParameters` (`:522`): the kernel/device-function header and the user parameters.
- `CU/graal/asm/CUDAAssemblerConstants.java` (`:28-54`): modifiers and region names.
- `CU/mm/CUDAKernelStackFrame.java` (`RESERVED_SLOTS = 4`, `RETURN_VALUE_INDEX = 0`, `DEVICE_LAUNCH_INFO_INDEX = 3`): the kernel-context frame contents.
- `CU/graal/CUDAInstalledCode.java` `setKernelArgs` (`:149`): host-side marshalling, which must match the above.
- `RT/interpreter/TornadoVMInterpreter.java` (`:1367-1385`): what is pushed per argument.

The ABI in words:
1. **Entry point.** A host-launched kernel is an `extern "C" __global__` function. A device-launched kernel (dynamic parallelism) is also `__global__`, under a derived name. A tile kernel is `extern "C" __tile_global__` plus launch hints. Non-inlined callees are `__device__` functions with Tornado-mangled names and their real return type, and they **also receive the four ABI regions** as leading parameters.
2. **Four leading pointer parameters, always present and in fixed order:** `_kernel_context` (64-bit-element pointer), `_constant_region` (byte pointer), `_local_region` (byte pointer) and `_atomics` (int pointer). In CUDA C none of them carries an address-space qualifier.
   - **Kernel context.** A small device buffer of 4 64-bit slots owned by the launch frame. Slot 0 is the return/exception slot: with `tornado.exceptions` on, the prologue emits an early return when it is non-zero (`CUDABackend.java:479`). Slot 3 is the flag that makes device-launched kernels report their grids. Under `GridScheduler` with dynamic loop bounds, scalar parameters that bound parallel loops are **read from kernel-context slots** (`CUDAKernelContextAccessNode`, set up in `TornadoTaskSpecialisation`) instead of being folded to constants. The frame is re-uploaded only when it changes.
   - **Constant region.** Either the task's constant data or the frame's constant address.
   - **Local region.** Kept for parity with the OpenCL ABI. On CUDA it is filled with a zeroed placeholder argument (`CU/CUDAKernel.java` `setLocalRegion`) or marked unused. Real shared memory comes from **`__shared__` declarations inside the kernel body**, sized at compile time by `TornadoLocalMemoryAllocation` and `KernelContext.allocate*LocalArray`.
   - **Atomics.** A device int buffer that backs `AtomicInteger`/`TornadoAtomicInteger` parameters. Their positions are recorded in the static `TornadoAtomicIntegerNode.globalAtomicsParameters`.
3. **User parameters** follow, in Java order, named from the method's local-variable table. Names that clash with CUDA built-ins (e.g. `blockDim`) get a `_` prefix, which **requires `-g`/LVT in the class file**.
   - Primitives and `HalfFloat` are passed by value with their C type.
   - Every reference (arrays, TornadoNativeArrays/Panama segments, vector types, field-holding objects) is a raw `unsigned char *` device address. The native-array header precedes the data (length read at offset 0 after `TornadoPanamaSegmentsHeaderPhase`).
   - `KernelContext`, `TileContext` and `AtomicInteger` parameters are **dropped** from the signature: they exist only to be intrinsified.
4. **Value specialisation.** With concrete args (host-launched kernel), `TornadoTaskSpecialisation` folds every boxed-primitive argument into a constant and evaluates field and array-length loads on object args. The emitted kernel therefore depends on argument *values*, while scalar parameters stay in the signature (and are still passed) even though they are unused. Recompilation is driven by `task.shouldCompile()`. The code-cache key is `taskId-entryName` per plan, plus the source hash for the on-disk cubin cache.
5. **Locals.** All SSA values become C locals declared at the top of the function, grouped by type. Private arrays become fixed-size C arrays (`FixedArrayNode`).

OpenCL (`OCL/graal/OCLArchitecture.java:60`, `getDeclaration` `:176`) uses the same four regions
but qualified (`__global`, `__constant`, `__local`, `__global`) with `__kernel`. There
`_local_region` *is* the real dynamic local memory. Metal (`MTL/graal/MetalArchitecture.java:62,136`)
adds `[[buffer(i)]]` / `[[threadgroup(i)]]` attributes, and `MTL/graal/backend/MetalBackend.java:491-494`
appends four `uint3` thread-position parameters (`thread_position_in_grid`, …), because MSL has no
global id built-ins.

## 4. Where `Access[]` is computed
1. `RT/graal/phases/sketcher/TornadoDataflowAnalysis.java` (sketch tier, near the end): for each **object-stamped parameter**, it walks the uses, classifying reads/writes through loads, stores, calls and address nodes. There are special cases for `@Parallel` range guards with stride 1. The result goes into `TornadoSketchTierContext.getAccesses()` (`RT/graal/phases/TornadoSketchTierContext.java:66`). Primitive params are `NONE`.
2. `RT/sketcher/TornadoSketcher.java` `:199-228`: merges callee sketches (invoke arguments that are caller parameters) and device-launched child sketches into the caller by OR-ing the access bits. Children in a launch cycle force `READ_WRITE`.
3. The result is stored in `Sketch.getArgumentsAccess()`. It is consumed by `TornadoTaskGraph.addInner` (`:869`), turned into TornadoVM bytecode/data-flow (`RT/graph/TornadoGraphBuilder.java:197`), copied into `TaskDataContext` by `CUDATornadoDevice.compileTask`, and used for buffer allocation (`CUDATornadoDevice.java:523-525`).

TC must reproduce this exactly (gate C4c). It is computed on the **sketch**, i.e. after inlining
and API replacement, but **before** argument specialisation.

## 5. Where CUDA Tile mode diverges
| Point | Path | Divergence |
|---|---|---|
| Recognition | `CU/graal/CUDATileKernels.java` (`isTileKernel` `:56`, `isTileContext` `:76`) | A method with a `uk.ac.manchester.tornado.api.tile.TileContext` parameter (matched by name) is a tile kernel |
| Parse | `CU/graal/compiler/plugins/CUDATileGraphBuilderPlugins.java` | `TileContext` calls become `CUDATile*Node`s (19 node classes) |
| High tier | `CUDATileLoopPolicies` passed to `TornadoTaskSpecialisation`'s unroller and to `LoopFullUnrollPhase` | loops containing tile ops are kept rolled (nvcc pipelines them), except for a K-loop unroll for large tiles on sm_80+ (`tornado.cuda.tile.unrollKLoop`) |
| Low tier | `CU/graal/phases/CUDATileSupportPhase.java` | rejects devices below sm_80, toolkits below 13.3 (it checks **nvcc**, not NVRTC) and unsupported operand types |
| LIR / text | `CU/graal/lir/CUDATileStmt.java`; `CUDABackend.emitPrologue` uses `TILE_KERNEL_MODIFIER` + `tileLaunchHints()`; `CUDACompilationResultBuilder.finish` adds `TILE_PREAMBLE` | same four-slot ABI, `__tile_global__`, tile C++ surface |
| Install | `CU/CUDACodeCache.java` (`:241` onward), `CU/CUDATileCompiler.java` (`isTileSource` `:79`, `compile` `:136`) | detected by the substring `__tile_global__` in the source; compiled out of process by **nvcc** to cubin; the toolchain identity goes into the cache key |
| Launch | `CU/graal/CUDAInstalledCode.java:86`, `CU/scheduler/CUDATileScheduler.java` | tile-specific launch geometry (one thread per block) |

## 6. Other observations relevant to TC
- **The sketch is backend-specific.** Parse-time plugins create CUDA node classes, so a "neutral" sketch does not exist today. TC's frontend should produce neutral IR, with intrinsic calls lowered later by `tc-tornado` / the dialect.
- **Two graph builders.** Besides the sketcher, `RT/analyzer/CodeAnalysis.buildHighLevelGraalGraph` builds a plain Graal graph (no plugins) for `@Reduce` task-graph rewriting (`ReduceTaskGraph`, `TornadoTaskGraph.java:1700-1711`). TC (or a small bytecode analysis) must cover this too, or `@Reduce` task graphs keep Graal alive.
- **Global state.** `compileSketchForDevice` is `static synchronized`. The canonicalizer's context is set per compile on a shared instance. Atomics positions live in a static map on a node class. TC should keep compile state per call.
- **Dead code.** `TornadoIfCanonicalization` (cuda/opencl/metal copies) and `DC/common/compiler/phases/loops/TornadoPragmaUnroll.java` are never referenced. `CUDACanonicalizer.simplify` is empty.
- **Fallback hook exists.** `compilePreBuiltTask` already installs raw source with the right ABI expectations, so the C3 design test can use it unchanged.
