# Plan: post-JVMCI branch — efficiency pass + unit tests

## Context
Branch `jdk27-jvmci-removal` (PR #894) makes TornadoVM run without JVMCI (removed in JDK 27):
a reflection + classfile-parser + Unsafe implementation of the `jdk.vm.ci.meta` SPI
(`tornado-runtime/.../runtime/jvmci/reflection/`, ~2,000 LOC) replaces HotSpot metadata; JDK-21
JVMCI is vendored via `--patch-module`; kernel entry points resolve via `SerializedLambda`.
OpenCL and CUDA both run at parity on the reflection path (CUDA: 822 PASS / 9 whitelisted-only FAIL).

Now: review the committed code for efficiency/correctness debt and add the first pure-JVM
(no-GPU) unit tests for the reflection layer.

## Findings (from exploration)

### Performance debt in the reflection layer (all in `tornado-runtime/.../runtime/jvmci/reflection/`)
1. **Classfile read+parsed per method, twice** — `ReflectionResolvedJavaMethod.classfileBytes()`
   (`:117-132`) does `getResourceAsStream().readAllBytes()` for `code()` AND again in
   `getConstantPool()` (`:304`); `ClassfileParser.parse` + `ReflectionConstantPool` ctor each
   re-parse the full constant pool. A class with N methods parses its classfile ~2N times.
2. **Hot metadata rebuilt on every call** — `getSignature()` (`:147`), `getLocalVariableTable()`
   (`:314`), `getProfilingInfo()` (`:347`), and `toString()` (`:371`, used by NodePlugin
   matching = hot path) allocate fresh objects each call; none memoized.
3. **`ReflectionConstantPool.resolveClass` uncached** (`:134-143`) — `Class.forName` per lookup,
   same CP index re-resolved repeatedly; line 138 has a dead identical-branch ternary.
4. **`ReflectionMembers` linear scans** (`:40-119`) — super-chain + `allInterfaces()` O(n²) walk
   with `getDeclaredMethod` at each level, per CP ref, uncached.
5. **`ReflectionResolvedJavaType.getInstanceFields/getStaticFields`** (`:198-220`) allocate fresh
   `ReflectionResolvedJavaField`s bypassing the `universe.lookupField` cache → breaks field
   identity canonicalization AND re-sorts each call; `findInstanceFieldWithOffset` (`:347`) does
   the full rebuild + linear search per call.

### Correctness debt
6. **`TornadoDataflowAnalysis.isSurvivingMMAStore/isSurvivingAtomicAdd`** (`:306,316`) match
   `targetName().contains("mmaStore"/"atomicAdd")` — no declaring-class check, substring match.
   A user method named `atomicAdd`/`myMmaStoreHelper` on any class is a false positive (array
   silently forced READ_WRITE). Scope to `KernelContext` declaring class + exact method names.
7. **`CodeAnalysis.buildHighLevelGraalGraph`** (`:93-96`) — `catch (Throwable) { printStackTrace(); return null; }`
   swallows compile failures → latent downstream NPE. Rethrow as TornadoBailout/log via TornadoLogger.
8. **`ReflectionUniverse.lookupPolymorphicMethod`** deliberately uncached → polymorphic methods
   lose identity canonicalization (document or fix).

### Cross-backend duplication + plugin-miss ROOT CAUSE (the headline finding)
9. **The InvocationPlugin "miss" is NOT a lookup-key mismatch.** Full trace against vendored
   Graal: registration bucket key (`MetaUtil.toInternalName`) == reflection
   `ReflectionResolvedJavaType.getName()` descriptor; method-name + `isSameType` descriptor
   prefix all match; `canBeIntrinsified` is constant-true; no intrinsification predicate; the
   string path is always taken (plugin set never closed) so `ReflectionResolvedJavaMethod
   .equals` is never consulted; no plugin `apply` returns false.
   **Leading hypothesis (needs 1 runtime probe): `OCLSuitesProvider.createGraphBuilderSuite`
   (`OCLSuitesProvider.java:58-67`, copied in every backend's SuitesProvider) calls
   `config.withEagerResolving(true)` and DISCARDS the return value** — `GraphBuilderConfiguration`
   is copy-on-write, so eager resolving is never enabled. Under lazy resolving on the reflection
   path the invoke target reaches the parser unresolved → `lookupInvocation` skipped → invoke
   survives → every "recover surviving Direct#KernelContext.*" case exists to repair this.
10. **Duplication quantified**: OCL carries 8 recovery cases (263 LOC file), CUDA 26 (507 LOC);
   Metal/PTX/SPIRV phases (~197 LOC each) are pre-JVMCI-era and share ~150 LOC of byte-identical
   boilerplate (run/switch skeleton, `getConstantNodeFromArguments`, NewArray lowering group,
   local-array allocation helpers) differing only in node packages + memory-space constants.
   Plus duplicated non-phase fixes: `isLocalIDNode`/`isLocalHalfArray` string matchers
   (OCL:571-586, CUDA:378-611), PANAMA header math in 7 ArrayWrapper/VectorWrappers,
   `resolveShape` (CUDA-only, Metal will need it).

## Work packages

### WP0 — Store this plan in the repo
First action on approval: DONE — this file (`Plan-eff.md`)
(repo root, alongside CUDA_BACKEND_PLAN.md; untracked scratch style — do not commit unless asked).

### WP1 — Reflection-layer caching (perf, no behavior change)
Files: `ReflectionUniverse`, `ReflectionResolvedJavaMethod`, `ReflectionResolvedJavaType`,
`ReflectionConstantPool`, `ReflectionMembers`.
- `ReflectionUniverse`: add per-`Class` cache `Map<Class<?>, byte[]> classfileBytes` (or a small
  `ParsedClassfile` record holding bytes + parsed CP) so each classfile is read+parsed once.
- `ReflectionResolvedJavaMethod`: memoize `signature`, `localVariableTable`, `profilingInfo`,
  `toString` in final/lazy fields.
- `ReflectionConstantPool`: `int cpi → Class` cache; delete the dead ternary (`:138`).
- `ReflectionMembers`: memoize (holder, name, descriptor) → Executable/Field lookups.
- `ReflectionResolvedJavaType`: route `getInstanceFields/getStaticFields` through
  `universe.lookupField` + cache the sorted arrays; cache `findInstanceFieldWithOffset` map.

### WP2 — Correctness hardening
- `TornadoDataflowAnalysis`: gate `isSurvivingMMAStore/isSurvivingAtomicAdd` on the declaring
  class being `uk.ac.manchester.tornado.api.KernelContext` (via `callTarget.targetMethod()
  .getDeclaringClass().toJavaName()`) and exact method-name sets.
- `CodeAnalysis:93` — replace swallow with TornadoLogger + rethrow bailout.

### WP3 — Fix sketch-time intrinsification centrally, then delete recovery cases
**WP3a (probe + one-line fix, HIGHEST leverage).**
- Fix the discarded copy-on-write call in every `*SuitesProvider.createGraphBuilderSuite`:
  `config = config.withEagerResolving(true);` (OCL `OCLSuitesProvider.java:58-67`, CUDA, Metal,
  PTX, SPIRV equivalents — same copied pattern).
- Probe: temporary counter/log on the recovery `switch` `Direct#KernelContext.*` cases; run CUDA
  quickPass. If plugins now fire at sketch time, the cases log zero hits.
- If confirmed: delete the KernelContext recovery cases backend-by-backend (OCL 8, CUDA 26),
  each deletion gated on a full quickPass (CUDA 822/9, OpenCL baseline). ALSO deletable then:
  `TornadoDataflowAnalysis.isSurvivingMMAStore/isSurvivingAtomicAdd` (MarkArrayParameterAccess
  nodes will exist at sketch time), the invoke-name branches in `isLocalIDNode`/
  `isLocalHalfArray`, and the `getHalfFloatValue`/swizzle-invoke recognition in
  `TornadoHalfFloatReplacement`. Keep the pre-JVMCI `NewArrayNode.newArray` + `*Intrinsics.*`
  cases (unrelated to the miss; present in all 5 backends since before the port).
- If the probe DISPROVES the hypothesis (invokes still survive): fall back to a re-intrinsify
  pass after sketch parse or force-resolving KernelContext invoke targets; recovery cases stay
  until one works.
**WP3b (boilerplate extraction — do regardless, sized to what survives WP3a).**
`tornado-drivers-common`:
- `TornadoIntrinsicsReplacementsBase` abstract phase: run/switch skeleton,
  `getConstantNodeFromArguments`, the NewArray lowering group + local-array allocation helpers
  parameterized by node factories + local/private space constants (collapses ~150 LOC × 5 files).
- `PanamaLayout.headerElements(JavaKind)` = `PANAMA_OBJECT_HEADER_SIZE / byteCount` — replace the
  7 hardcoded sites (ArrayWrappers/VectorWrappers, CUDA lowerMMAStore/lowerAtomicAdd).
- `resolveShape(ValueNode)` (MMAShape) moved from CUDAGraphBuilderPlugins for Metal reuse.

### WP4 — Pure-JVM unit tests (no GPU)
Location: `tornado-runtime/src/test/java/uk/ac/manchester/tornado/runtime/jvmci/reflection/`
(same package → package-private access; mirrors the accepted `tornado-drivers/metal/src/test`
precedent). JUnit 4.13.2 (already in root dependencyManagement); add test-scope dep to
`tornado-runtime/pom.xml`. NOTE: every JDK profile sets surefire `skipTests=true` — run with
`mvn -pl tornado-runtime test -DskipTests=false` (document in Makefile target `unit-tests`).
Tests (all instantiable standalone via `new ReflectionUniverse()`):
- `ReflectionResolvedJavaMethodTest`: name/signature/modifiers; **canonical toString format**
  (the NodePlugin-matching contract); equals/hashCode + universe canonicalization (same
  Executable → same instance).
- `ReflectionResolvedJavaTypeTest`: descriptor name form (`Lpkg/Cls;`), array/component,
  instance-field enumeration + Unsafe offsets, **field identity canonicalization** (locks in the
  WP1 `getInstanceFields` fix).
- `ClassfileParserTest` / `DescriptorSignatureTest`: parse a real compiled class's bytes
  (via `getResourceAsStream`) — Code attribute recovery, CP entries; descriptor parsing
  `(IF[J)Ljava/lang/String;` etc.
- `TaskUtilsLambdaResolutionTest`: Serializable lambda → `resolveViaSerializedLambda` → correct
  Method (incl. inherited impl walk + non-serializable error path).
- `TornadoVMConfigAccessTest`: header sizes/array base offsets vs the JVM's actual Unsafe values
  (not hardcoded — layout varies with compressed-oops flags).
- `TornadoDataflowAnalysisMatchTest`: after WP2 extracts the name-match into a static string
  helper, assert exact-match semantics (no `myAtomicAddHelper` false positive).
- CI (optional follow-up): a GitHub-hosted-runner job running
  `./mvnw -pl tornado-runtime test -DskipTests=false` — first no-GPU CI stage.

## Verification
1. `bin/compile --jdk jdk27 --backend cuda` and `--backend opencl` → BUILD SUCCESS.
2. `source setvars.sh; tornado-test --verbose --quickPass` on CUDA → still 822/9 (whitelist-only);
   spot-run OpenCL quickPass → unchanged baseline. WP1 is perf-only: any test delta = regression.
3. `mvn -pl tornado-runtime test -DskipTests=false` → new unit tests green on jdk21 + jdk27.
4. Optional perf sanity: time `tornado-test --quickPass` before/after WP1 (expect same-or-faster
   compile phase; classfile parsing collapse is the visible win on many-method kernels).
5. `make checkstyle` clean.

## Notes
- Commit style: one-line messages, no attribution; never `git add -A` (untracked .gguf/scratch).
- `--quickPass` (no `--ea`); `source setvars.sh` inline per shell call.
- WP order: **WP0 → WP3a probe first** (one line + a probe run; its outcome sizes everything
  else and may delete the code WP2/WP3b would otherwise touch) → WP2 correctness → WP1 caching
  → WP4 tests → WP3a deletions (gated per-backend on quickPass) → WP3b extraction.
- Each build+GPU-test cycle ≈ 50 min (build ~25 + quickPass ~30); batch changes per cycle.
- WP2's isSurviving* scoping fix still worth landing even if WP3a later deletes it (defensive,
  tiny), but skip if WP3a confirms fast.
