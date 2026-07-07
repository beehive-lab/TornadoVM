# JDK 27+ (JVMCI-free) — Completion Checklist

Tracks the remaining work to take the `jdk27-jvmci-removal` branch from
"VectorAddInt runs on OpenCL/JDK 27" to a complete, tested, multi-backend,
multi-JDK story. See `JDK27-NO-JVMCI.md` for the architecture and how to run.

Legend: `[x]` done · `[ ]` todo · `[~]` partial

---

## 0. Current status
- [x] Vendor `jdk.internal.vm.ci` as a same-named application module (`bin/build_jvmci_module.py`)
- [x] `jdk27` Maven profile + `bin/compile --jdk jdk27`
- [x] Reflection-only runtime bootstrap (no `HotSpotJVMCIRuntime`) — OpenCL
- [x] `SerializedLambda` kernel-entry resolution
- [x] Native-array accessor intrinsics (Int/Float/Double/Long/Short/Byte/Char) — OpenCL
- [x] `@Reduce` detector guarded; synthesized `LocalVariableTable`
- [x] JDK-conditional launcher (`tornado.py`)
- [x] Validated on RTX 4090: `VectorAddInt` (Int) + `MonteCarlo` (Float) — correct results, real kernel time

---

## 1. Test coverage — `make fast-tests` on JDK 27
The `jdk27` profile **skips surefire** (`skipTests`), so tests run via the launcher
(`tornado-test`; user-added `make fast-tests-jdk27` target).

> **BLOCKER (2026-07-05): `make fast-tests` / `--ea` aborts 100% of tests at backend init on JDK 27.**
> With assertions enabled, Graal's `StandardGraphBuilderPlugins.registerUnsafePlugins0`
> (vendored `tornado.graal` jar, v23.1.0) registers an invocation plugin for
> `jdk.internal.misc.Unsafe.getObject(Object,long)` — a method **renamed to `getReference`**
> and absent on JDK 27 — and the `InvocationPlugins$Checks.checkResolvable` **assertion** throws
> `NoSuchMethodError` from `OCLHotSpotBackendFactory.createGraphBuilderPlugins` before any kernel
> compiles. Result: `tornado-test --ea` reports `{PASS:0, FAILED:916}` (all fail identically at
> `TornadoCoreRuntime` init). **Not a regression from the reflection work — it's a Graal-23.1.0-vs-JDK27
> `Unsafe` naming mismatch, gated purely by `-ea`.** Running the same suite **without `-ea` works**
> (`TestIntegers#test01` passes). TODO: teach the Unsafe-plugin registration to use `getReference` on
> JDK ≥ 12 (or make `make fast-tests-jdk27` drop `--ea`), otherwise the assertion-enabled suite is unusable.

**First-pass results (2026-07-04, OpenCL / RTX 4090, JDK 27.ea.24) — core works broadly:**
- `arrays.TestArrays` ~14–18/23 pass. **All failures are HalfFloat** (`getShortAtIndex → getAtIndex`
  abstract-accessor gap — `HalfFloatArray` isn't covered by the accessor intrinsic; it's a special type
  with its own `OCLHalfFloatPlugins`), plus `initHalfFloatVector` hits **`Unsupported class file major
  version 71`** (a Java-27/v71 class in the half-float init path — beyond frozen-Graal v66 / ASM v67).
  The 4 "OPENCL CONFIGURATION UNSUPPORTED" are CUDAGraph tests (expected, not our regression).
- `math.TestMath` ~26/28 pass.
- `loops.TestLoops` 23/26 pass — the 3 failures are `forConstant04/05/06`, all **`Matrix2DFloat`**
  (nested `@Parallel`). `forConstant03` (**IntArray**) passes. These **compile** (no bailout) but write
  0.0 → a **nested-object correctness bug**, NOT a regression from the accessor intrinsic: `Matrix2DFloat.set`
  delegates to `FloatArray.set` on a `storage` field, and my intrinsic emits the *same* nodes as the
  pre-existing `getFloatAtIndex` path. In the kernel the nested-array base is reached via a field load
  (`ul_12 = base + (ui_11 << 3)`), and the write address ends up wrong. Root cause is likely the
  **reflection field-offset / nested-object marshalling** for a native-array reference field inside another
  type (a direct array *parameter* like IntArray works; a nested array *field* like `Matrix2DFloat.storage`
  does not). Same class of issue will hit `Matrix*`, `VolumeShort`, image types, and any kernel that reads a
  native array out of another object's field.

**Full `--quickPass` run (2026-07-05, JDK 27.ea.24 / OpenCL / RTX 4090, NO `-ea` — see blocker above):
`{PASS: 507, FAILED: 283, UNSUPPORTED: 137}`.** Scalar foundation types are solid; the failures cluster
into a few well-defined causes:

| Cluster | ~count | Cause |
|---|---|---|
| `foundation.*` (Integers/Floats/Doubles/Long/Shorts/If/LinAlg) | 0 fail | ✅ core scalar path solid |
| `api.TestAPI` 32/32, most `kernelcontext.*`, `tasks.*`, `executor`, `loops.TestParallelDimensions` | 0 fail | ✅ KernelContext + API work |
| `expected:<x> but was:<0.0>` (vectortypes, `matrices.TestMatrixTypes` 27/35, `images.TestImages` 18/18) | ~90 | **nested native-array-field bug** (§1 below) — `Vector*`/`Matrix*`/image backed by a `*Array` field |
| `ConstantReflectionProvider.getMemoryAccessProvider() not yet available` | 56 | **concrete reflection-provider gap to port** (message now names the exact method) |
| `testPrivateVector{Int,Float,Double,Half}` → `@SegmentElementSize missing on jdk.internal.ref.CleanerFactory (field commonCleaner)` | 12 | **`TornadoNativeTypeElimination` heuristic misfire** (see below) |
| `address origin unimplemented: InvokeNode` | 32 | unimplemented lowering on reflection path |
| HalfFloat (`TestHalfFloats`, half-vector) | ~20 | `Unsupported class file major version` + `getShortAtIndex→getAtIndex` (known, §1) |
| `reductions.*` | ~75 | expected — `@Reduce` guarded off (§4) |
| `cublas/cublasLt/cufft/cudnn/*MMA*`, SIMD-group | 137 UNSUPPORTED | not applicable on this path/HW (expected) |

**NEW root cause for `testPrivateVector*` (2026-07-05):** the improved diagnostic revealed the phase is
matching **`jdk.internal.ref.CleanerFactory.commonCleaner`** — the Panama MemorySegment cleaner field — not a
native-array segment. `TornadoNativeTypeElimination` finds a `LoadFieldNode` whose `toString()` contains
"segment" and blindly takes `successors().first()` as the `baseIndex` load; on the **reflection path** the local
`FloatArray` allocation's graph shape puts the `commonCleaner` field load there instead. So this is **not** a
missing reflection-provider method — it's the phase's fragile "first-successor `LoadFieldNode`" heuristic
misfiring for *local/private* native arrays. Fix: match the actual `baseIndex`/segment-base field explicitly
(by name/type) rather than positionally.

Prior single-suite `vectortypes.TestFloats` split (kept for reference): (a) correctness `0.0` = nested-object
bug; (b) `testPrivateVector*` = the CleanerFactory misfire above (was mis-labeled a reflection-provider gap).

- [~] Port the missing `TornadoConstantReflectionProvider` method(s) hit by private/local arrays:
      `asJavaClass()` (→ `snippetReflection.forObject(mirror)`) and `asJavaType()` (→ inverse, via a
      `ReflectionUniverse`) now implemented — these were the memory's "known hard boundary" and are now
      passable. `testPrivateVector{2,4,8}` now progress past them to a new gap: **"Annotation is missing"**
      thrown by `TornadoNativeTypeElimination.getElementKindSize`.
      - Correction (2026-07-05): this is **NOT** a reflection annotation-read gap. `@SegmentElementSize`
        is `@Retention(RUNTIME)`, and the reflection path's `ReflectionResolvedJavaType.getDeclaredAnnotations()`
        delegates to `Class.getDeclaredAnnotations()`, which *does* return RUNTIME annotations. The phase reads
        the annotation off `baseIndexNode.field().getDeclaringClass()` — so for the private-vector case the
        **declaring class of the `segment`/`baseIndex` field genuinely lacks `@SegmentElementSize`** (a
        private/local vector is backed differently from a plain `FloatArray` parameter). Likely a pre-existing
        limitation surfaced on the reflection path, not a reflection-provider method to port.
      - Done (2026-07-05): made the exception name the offending class + field (was a context-free
        "Annotation is missing"), and added a `kindElement == 0` guard so an annotated-but-not-`SegmentElementSize`
        declaring class fails with a clear message instead of an opaque div-by-zero.
      - **RESOLVED via that diagnostic (2026-07-05 run):** the class is **`jdk.internal.ref.CleanerFactory`,
        field `commonCleaner`** — i.e. `TornadoNativeTypeElimination` grabbed the wrong `LoadFieldNode`. Root
        cause is the phase's positional `loadFieldSegment.successors().first()` heuristic misfiring on the
        reflection-path graph for local/private native arrays (see table note above).
      - **[x] FIXED (2026-07-05):** added `isNativeArrayBaseIndexField(baseIndexNode)` guards to BOTH call sites
        (direct-successor and via-`FixedGuardNode`) — the base-index rewrite now only fires when the successor
        `LoadFieldNode` really is a native array's `baseIndex` (field name `baseIndex` **and** declaring class
        annotated `@SegmentElementSize`), plus an outer `isNativeArraySegmentField(loadFieldSegment)` precision
        guard. `CleanerFactory.commonCleaner` matches are now skipped. Confirmed in the full re-run:
        **CleanerFactory failures 12 → 0**, no new bailouts.
- [x] **Port `ConstantReflectionProvider.getMemoryAccessProvider()`** on the JVMCI-absent path — **DONE
      (2026-07-05):** new nested `ReflectionMemoryAccessProvider` in `TornadoConstantReflectionProvider`
      (returned only when `backing == null`) folds constant reads from an object base at a JVMCI field
      displacement via `sun.misc.Unsafe` (`getInt/getLong/getObject/...`; the displacement IS an Unsafe field
      offset, matching `HotSpotMemoryAccessProviderImpl`). A non-object base returns `null` (not foldable → read
      stays in place; never a wrong fold). Confirmed: **getMemoryAccessProvider failures 56 → 0**; also unblocked
      constant-folding in the reduction kernels (+14 reduction passes).
- [x] Run a representative subset (arrays/math/loops) on JDK 27 / OpenCL — captured above
- [x] Run the full `--quickPass` suite on JDK 27 / OpenCL (no `-ea`) and tabulate — baseline **507/283/137**;
      after fixes (a)+(b) **520/270/137** (+13 pass, 0 residual CleanerFactory/getMemoryAccessProvider errors).
      Verified across two identical-tally runs: no regression. Run-to-run flakes exist (e.g. `compute.MMwithBytes`
      NaN and one `TestReductionsIntegers` test flip between runs — non-deterministic GPU/reduction behaviour,
      each passes in isolation), so treat ±1-2 per-suite as noise.
- [ ] Cover **HalfFloatArray** in the accessor intrinsic (read `short`, wrap `HalfFloat`) or route via the
      half-float plugins so `getShortAtIndex`/`getAtIndex` is never reached
- [ ] Investigate the **v71 class-file** parsed in `initHalfFloatVector`
- [x] Triage the `TestLoops` `10.0 → 0.0` correctness failures → nested-object (`Matrix2DFloat`) issue (see above)
- [x] **FIXED (2026-07-07) — nested native-array field access on the reflection path.** Root cause was NOT
      marshalling/offsets (those were all verified correct). It was **`TornadoDataflowAnalysis.processUsages`**:
      a `LoadFieldNode` of a native-array field (`Matrix2DFloat.storage`, `VectorFloat.storage`, image storage)
      was skipped via `isTornadoNativeArray(loadField)` → `continue`, which stopped the usage traversal so a
      write THROUGH the nested array (`b.set(...)` → `b.storage.set(...)` → `JavaWriteNode`) never marked the
      enclosing parameter `b` as written. Result: `b` got `Access.READ_ONLY` → no `DependentReadNode` (TornadoGraphBuilder
      line 183) → no `CopyOutNode` → **no `TRANSFER_DEVICE_TO_HOST` bytecode emitted → the output object is never
      copied back → reads host-initial 0**. Reflection-specific because `isTornadoNativeArray` matched
      `field().toString()` on the full array type name (`...arrays.IntArray`): the reflection field's toString
      contains it (skip fires); HotSpot's field toString didn't (so JDK≤21 followed the usages and worked).
      **Fix:** removed the skip so native-array field loads follow their usages (write-through-nested detected;
      the enclosing param becomes READ_WRITE/WRITE_ONLY as appropriate). Diagnosed by tracing the whole
      write→readback chain: marshalling offsets/handle all correct (`storage`@20, handle=3→nested buffer), input
      transfer OK, but **no `transferDeviceToHost` at the interpreter and no `TRANSFER_DEVICE_TO_HOST` in the
      bytecode** for the matrix output, while `TestArrays` emits it 33×.
      **Impact: full suite 520→587 PASS / 270→203 FAILED / 137 UNSUP (+67, zero real regressions; committed
      a5be660f3, pushed mp/jdk27-jvmci-removal).** Per-suite: `matrices.TestMatrixTypes` 27→7, `images.TestImages`
      18→9, `vectortypes.TestFloats` 25→20.
      - RESIDUAL vector failures are a **DIFFERENT, pre-existing bug** (masked by the readback bug until now):
        `VectorFloat4` etc. now correctly emit `TRANSFER_DEVICE_TO_HOST` (readback fix works) but still read 0.0.
        Confirmed: `ARRAY_HEADER=16` (baseIndex=4 → correct data offset `+16`), but the emitted `vload4/vstore4`
        kernel address is `nested_buffer + flatIndex*4 + 12` — a **`+12` header, off by one float (4 bytes)** —
        so every vector element read/write is misaligned. The `+12` is added in BYTES after scaling, so it does
        NOT come from `TornadoNativeTypeElimination`'s element-unit fold; there is no dedicated Float-vector
        offset phase (only `TornadoHalfFloatVectorOffset` exists). The `vload4` comes from vectorizing the 4
        consecutive scalar `array.get(flatIndex+k)` reads in `VectorFloat4.loadFromArray`; the vectorization
        computes the group base with the wrong header. NEXT target (~34 `vectortypes` fails): find where the
        vector-group base address is built (VectorLoadElementProxyNode / VectorStoreElementProxyNode / LIR
        vectorization) and why the header is 12 not 16.
      - **END GOAL clarified (user, 2026-07-07): reach parity with `develop`'s failure rate** (not zero — some
        tests fail on develop too). Reductions (`@Reduce`, ~41) and HalfFloat v66/v71 may be out of scope if
        develop/its JDK also skips/fails them; the reflection path should not ADD failures beyond develop's.
      (superseded investigation below kept for the record)
      - Investigation (2026-07-04): **JDK 27 defaults `UseCompactObjectHeaders=true`** (JDK 25+
        Project Lilliput; the flag didn't exist on JDK 21, so object field layout differs). BUT running
        `forConstant04` with `-XX:-UseCompactObjectHeaders` still writes 0.0 → the header toggle alone is not the fix.
      - **DEEPER INVESTIGATION (2026-07-05) — this is the largest failure family: ~94 correctness `0.0`/`0`
        fails** across `vectortypes.TestFloats/Ints/Doubles` (56), `matrices.TestMatrixTypes` (20/27),
        `images.TestImages` (18). Ruled OUT two long-standing hypotheses with hard evidence:
        - **NOT codegen field offsets.** Instrumented `OCLLoweringProvider.fieldOffset` (temp probe, reverted):
          it emits `Matrix2DFloat.storage = 20`, and `Unsafe.objectFieldOffset` agrees, and the host marshaller
          (`OCLFieldBuffer.offsetOf`, also `Unsafe.objectFieldOffset`) writes at the same 20. So the kernel's
          `ui = *(uint*)(base+20); sub = base + (ui<<3)` reads the **`storage`** reference (NOT the segment;
          earlier notes mis-identified offset 20 as `segment`, which is actually at 28). Kernel structure is
          **correct**: storage handle read + `×8` decompress + inner `IntArray` access with `baseIndex=4` folded
          — all consistent.
        - **NOT compact object headers.** `matrices.TestMatrixTypes` fails **27/35 with `-XX:-UseCompactObjectHeaders`
          exactly as with it on** — the header layout is not the variable.
        - **Localized to: the runtime nested-buffer *handle value* on the reflection path.** The kernel arithmetic
          is right, so the wrong result comes from the `storage` compressed handle (`ui`) being wrong at runtime, or
          the nested `*Array` sub-buffer not being allocated/placed at `handle*8`, or its data not transferred.
          Note `OCLFieldBuffer.serialise` emits **no** field-marshalling trace for these matrix runs (with
          `-Dtornado.debug=true`), suggesting the `storage` handle is written by a *different* buffer path than the
          per-field object serialiser — that path is the prime suspect. This path worked on the JDK-21 reflection
          run (memory Phase 5: matrixmul/images parity), so it's a JDK-21→27 reflection-path regression in nested
          buffer allocation, **not** codegen or object layout. NEXT: trace the actual device buffer bytes at
          `base+20` (the handle) and where the nested `*Array` buffer is allocated (`wrappedFields[].getBufferOffset()`)
          for one `Matrix2DFloat` kernel; compare JDK-21-reflection vs JDK-27.
- [x] **`make fast-tests-jdk27` no longer passes `--ea`** (2026-07-05) — the target dropped `--ea` (see the
      `--ea` BLOCKER note at the top of §1). The assertion-enabled suite is unusable on JDK 27 until Graal's
      Unsafe-plugin registration is made `getReference`-aware; without `-ea` the suite runs normally.
- [ ] **KernelContext local-memory family (~30 fails: `InvokeNode` "address origin unimplemented" + related
      Bailouts)** — root-caused 2026-07-05. `context.allocate{Int,Float,Double,Long,Byte,HalfFloat}LocalArray`
      survives as a direct `InvokeNode` (its `int[]` result used as an address base → `OCLAddressLowering`
      throws). Confirmed via probes: the `allocateIntLocalArray` invocation-plugin `apply()` is **never called**
      — Graal's `InvocationPlugins.lookupInvocation` MISSES it on the reflection path. Yet `IntArray.get(int)`
      and `KernelContext.localBarrier()` (same reflection path, same `Registration(KernelContext.class)`) DO
      match. The **sole differentiator is the array (`int[]`) return type** — a method returning an array type
      isn't matched by the plugin lookup on the reflection path (the reflection array-type descriptor `[I` and
      signature are correct, so it's a deeper Graal-internal matching subtlety). Two fix options: (a) find/repair
      the array-return matching in the vendored `InvocationPlugins` lookup (also clears any other array-returning
      intrinsics), or (b) **workaround phase**: replace a surviving `InvokeNode`→`KernelContext.allocate*LocalArray`
      with a `LocalArrayNode` (mirrors `TornadoNativeTypeElimination`) — bounded and testable.
- [~] **"Fix all remaining failures" plan in progress (2026-07-07, plan file
      `fix-all-remaining-failures-shimmering-peacock.md`). Suite 587→625→630 PASS / 203→165→149 FAILED / 137 UNSUP,
      0 regressions, each phase pushed to mp/jdk27-jvmci-removal:**
  - [x] **Phase 1 (b1d5f56fa) — vector vload/vstore.** `LoadIndexedVectorNode` used `getArrayBaseOffset(kind)` =
        JVM Java-array offset (12 for float[]/int[] under JDK 27 compact headers) instead of the fixed native
        `ARRAY_HEADER=16`. FIX: explicit-base `createArrayAddress(...,PANAMA_OBJECT_HEADER_SIZE,...)` in
        `OCLLoweringProvider.createArrayAccess`/`lowerStoreIndexedNode`. +38.
  - [x] **Phase 2 (4e222dda3) — KernelContext.** Plugin lookup misses `allocate*LocalArray` (array return) +
        `local/globalBarrier` → survive as invokes passing the KernelContext obj as `argN` → OpenCL
        "undeclared identifier 'arg0'". FIX: `TornadoOpenCLIntrinsicsReplacements` rewrites them to
        `LocalArrayNode`/`OCLBarrierNode`; `OCLNodeLIRBuilder.emitPrologue` skips the unused KernelContext/
        AtomicInteger param. Bytes 4→0, others −1..−3.
  - [x] **Phase 3a (commit 3b59d91cb) — HalfFloatArray accessor.** New `registerHalfFloatArrayGetSet` in
        `OCLGraphBuilderPlugins` emits `ReadHalfFloatNode`/`WriteHalfFloatNode` at `HalfFloatArray.get/set`
        (`JavaKind.Short` sizing via `arrayElementAddress`), so the abstract `MemorySegment.getAtIndex`
        (`this.code is null`) is never reached. CORRECT but **net-zero** for the count — every HalfFloat test is
        also gated by v71 (below). Committed as a prerequisite.
  - **TRACTABLE PHASES DONE (1, 2, 3a). Remaining = architectural / reassess territory (per plan decision):**
  - [ ] **Phase 3b — v71 class-file (dominant HalfFloat blocker).** `HalfFloat` ctor/ops call
        `java.lang.Float.floatToFloat16`/`float16ToFloat` (Java-20+, **v71** on JDK 27). `TornadoSketcher`
        (line ~178) recursively sketches every non-intrinsified callee; it descends into `java.lang.Float`
        (v71) which the frozen-Graal ClassfileParser (max v66) rejects → "Unsupported class file major version
        71". These Float methods HAVE invocation plugins (`registerFP16ConversionPlugins`) but they don't fire
        during the sketch graph-builder. FIX = teach `TornadoSketcher` to skip descent into invocation-plugin-
        backed callees (general, JDK-neutral) OR raise the frozen-Graal version cap. **Risky** (touches the core
        access-analysis fixed in Phase-nested-array); do with care / supervision, not blind.
  - [ ] Residual KernelContext-reduction correctness `0.0`/bail (deeper — reduction logic, not compile).
  - [ ] Phase 4 `@Reduce` (~41): architectural host-graph rewrite.
  - [ ] **Phase 0 — develop baseline**: build develop on JDK 21, run the same suite; scope which of the
        remaining ~149 are actually in-scope (parity target = JDK-27 failing-set ⊆ develop failing-set).
- [ ] Vector types (`Float4`, `Int3`, …), matrices, `KernelContext`, atomics — note which work
- [ ] Re-enable surefire for `jdk27` once green, or keep running via the launcher
- [ ] **Regression:** confirm JDK 21 (`make jdk21`) still builds + passes — shared changes
      (Serializable `TaskX`, ASM 9.7, accessor intrinsics, reflection guards) must not break it

**Notes / gaps**
- The accessor intrinsic fires on ALL JDKs now; verify it emits identical codegen to the old
  `getIntAtIndex` path on JDK 21 (same nodes by construction, but confirm).
- `@Reduce` kernels bail on JDK 27 (guarded off) — expected until §4.

---

## 2. Apply to the other backends
Everything so far is **OpenCL only**. Each backend needs the same three touch-points.

For **PTX**, **SPIR-V**, **Metal**, **CUDA**:
- [ ] **Accessor intrinsics** — port `registerNativeArrayGetSet` / `arrayElementAddress` into the
      backend's `*GraphBuilderPlugins` (using that backend's read/write + address node types;
      OpenCL uses `JavaReadNode`/`JavaWriteNode`/`OffsetAddressNode`)
- [ ] **Backend factory reflection branch** — mirror `OCLHotSpotBackendFactory.createJITCompiler`'s
      `if (jvmciRuntime == null) { … reflection providers … }` in `PTXHotSpotBackendFactory`,
      `SPIRVHotSpotBackendFactory`, `MetalHotSpotBackendFactory`, `CUDAHotSpotBackendFactory`
- [ ] **`getLocalVariableTable`** — already fixed centrally in `ReflectionResolvedJavaMethod`
      (shared), but the backends read it in `*NodeLIRBuilder.emitPrologue` / `*Backend`; verify each
- [ ] Confirm the ~60 `jdk.vm.ci.hotspot` imports across the driver are only *compiled* against the
      vendored module (they must never be *called* on the reflection path)
- [ ] Build each backend on JDK 27 (`bin/compile --jdk jdk27 --backend ptx` etc.) and run one kernel
- [ ] `tornado.py` module wiring already lists all backends; confirm the per-backend modules load

Priority order: **CUDA** (same NVIDIA HW, quickest to validate) → PTX → Metal →

---

## 3. Try JDK 25 and JDK 26 — document what it takes
JDK 25/26 still **have** JVMCI and a built-in Graal (renamed
`jdk.internal.vm.compiler` → `jdk.graal.compiler`), so they are a *different* problem from 27.

### JDK 26 (`26.0.1-open`)
- [ ] Decide the strategy and document:
  - **Vendored path (this branch's approach):** the platform still ships `jdk.internal.vm.ci`,
    so a same-named application module **collides** ("Two versions of module"). Needs
    `--patch-module`/`--upgrade-module-path` or a differently-named module — document the exact wiring.
  - **SPI drift:** JDK 26's `jdk.vm.ci.*` added abstract methods (`ResolvedJavaMethod.isDeclared()`,
    `ResolvedJavaType.getAllMethods(boolean)`, `ConstantPool.lookupConstant(int,boolean)` +
    `lookupBootstrapMethodInvocations(boolean)`) and removed `HotSpotJVMCICompilerFactory`. If NOT
    vendoring, the reflection providers must implement these (a `jdk26`-shaped SPI).
  - The `jdk26` Maven profile already exists (release 26, no preview) — note what else is needed.
- [ ] Build + run one kernel on JDK 26; record the diff vs JDK 27

### JDK 25 (`25-open`)
- [ ] Cross-check against the upstream `beehive-lab/TornadoVM` **`jdk25` branch**, which targets
      JDK 25 by using the **built-in `jdk.graal.compiler` + real HotSpot JVMCI** (no vendoring, no
      reflection path). Document that this is the simpler route for 25/26 and where the two
      strategies converge/diverge.
- [ ] Build + run one kernel on JDK 25; record what it takes
- [ ] Check if the non JVMCI approach will work also here

### Deliverable
- [ ] A short compatibility matrix: **JDK 21 / 25 / 26 / 27** × {JVMCI present?, built-in Graal name,
      preview needed?, vendoring needed?, reflection path needed?, extra flags} — add to `JDK27-NO-JVMCI.md`

---

## 4. Deferred features (JVMCI-free path)
- [ ] `@Reduce` kernels — the detector + hybrid combiner use host-side Graal-as-JIT via
      `HotSpotJVMCIRuntime`; replace with a JDK-neutral path (reflective invocation, or reflection-built
      host graph) so reductions work on JDK 27
- [ ] Any other code paths that reach `HotSpotJVMCIRuntime`/`CompilerToVM` at runtime

---

## 5. Packaging / reproducibility polish
Right now a clean `bin/compile jdk27` does **not** produce a runnable SDK without manual fixups.
- [ ] Auto-stage the vendored jvmci jar into `graalJars/` and ship it to `share/java/jvmci`
      (wire `bin/build_jvmci_module.py` into `bin/pull_graal_jars.py` / `bin/compile`)
- [x] Fix ASM dependency mediation so the SDK ships **asm-9.7** (needed for release-22/v66 class files).
      Verified (2026-07-05): both `tornado-annotation/pom.xml` and `tornado-runtime/pom.xml` now declare
      `org.ow2.asm:asm:9.7`, and the assembled dist ships **only `asm-9.7.jar`** in `share/java/tornado`
      (`dist/.../share/java/tornado/asm-9.7.jar`; no `asm-9.5` anywhere). `tornado-assembly` has no separate
      asm path — its profiles are backend-based, not JDK-based, so the jdk27 assembly resolves identically.
- [ ] Verify `make jdk27 BACKEND=opencl` end-to-end produces a working `bin/sdk` with no manual copies
- [ ] Consider a `Makefile` `fast-tests-jdk27` target

## 6. Can the apporach of bypassing JVCI can be a standlone tool 
- [ ] Can it be a standalone tool
- [ ] if yes find usages 
- [ ] use seperate repo

