# Porting a TornadoVM backend to the post-JVMCI (reflection-only) architecture

This guide captures how the **OpenCL** backend was made to run without JVMCI (JDK 27+) using the
reflection + ASM + `Unsafe` metadata path, so the same work can be repeated for **PTX / CUDA /
Metal / SPIR-V**. OpenCL is the reference implementation; every item below links to the concrete
OpenCL file so it can be copied.

The runtime-, build- and SPI-level work is **shared** and already done for every backend. What
remains per backend is the **code-generation** work (items B1–B9): the reflection graph-builder
misses the `InvocationPlugin`s that the JVMCI path relied on, and the compact-object-header /
`KernelContext` handling has to be taught to the backend's LIR emitter.

---

## A. Shared infrastructure (done once, applies to all backends)

| Concern | Where | Notes |
|---|---|---|
| Reflection metadata providers | `tornado-runtime/.../runtime/jvmci/reflection/*`, `TornadoMetaAccessProvider`, `TornadoConstantReflectionProvider` | JDK-neutral `jdk.vm.ci.meta` implementations. No host backing provider. |
| Kernel-entry resolution | `TaskUtils.resolveViaSerializedLambda` | lambda → `SerializedLambda` → `Method`. |
| VM layout constants | `TornadoVMConfigAccess` | `Unsafe`-probed header size, compact-object-header-aware `hubOffset`. |
| SPI (no HotSpot type) | `TornadoBackendProvider.createBackend(OptionValues, TornadoVMConfigAccess)` | backends construct reflection providers directly. |
| Barrier set (no GC on GPU) | `drivers-common/.../providers/NoBarrierSet` + `TornadoPlatformConfigurationProvider` | supplies a no-op `BarrierSet`. |
| `@Parallel` extraction on new class files | `tornado-annotation/.../ASMClassVisitor` | clamps class-file major to 67 (ASM 9.7 max). |
| Build: uniform JVMCI vendoring | pom `jdk25/jdk26` profiles `--patch-module jdk.internal.vm.ci=graalJars/jvmci-21.0.2.jar`; `jdk27` vendored module dep; launcher `tornado.py` `jvmci_patched` branch | freezes the JDK-21 `jdk.vm.ci` SPI on every JDK. |
| Build: class-file version | all `jdk22`–`jdk26` profiles compile at `--release 22` | frozen Graal 23.1.0 classfile reader rejects > v66. |

---

## B. Per-backend code-generation checklist

For each backend `X` (packages `tornado-drivers/x/.../graal`), port these. The OpenCL column is the
authoritative reference.

### B1. Backend factory constructs reflection providers
- OpenCL: `OCLHotSpotBackendFactory.createJITCompiler` → `new TornadoMetaAccessProvider()`,
  `new TornadoConstantReflectionProvider(snippetReflection)` (no `HotSpotJVMCIRuntime` param).
- Port: `X HotSpotBackendFactory.createJITCompiler`. The `*GraphBuilderPlugins.registerInvocationPlugins`
  signature must take `jdk.vm.ci.meta.MetaAccessProvider`, not `HotSpotMetaAccessProvider`.

### B2. FieldBuffer uses the runtime meta-access
- OpenCL: `OCLFieldBuffer` resolves the object type via `TornadoCoreRuntime.getTornadoRuntime().getMetaAccess()`
  and the reflection `ResolvedJavaType` (no `HotSpotResolvedJavaType` cast).
- Port: `x/mm/XFieldBuffer` — replace `getVMRuntime().getHostJVMCIBackend().getMetaAccess()`.

### B3. Intrinsics fallback phase (THE key reflection-path fix)
On the reflection path the `InvocationPlugin`s registered for `KernelContext` /
`OpenCLIntrinsics` **miss** at sketch time, so the calls survive as `Invoke#Direct#...` nodes and
must be lowered by a dedicated high-tier phase.
- OpenCL: `graal/phases/TornadoOpenCLIntrinsicsReplacements` (registered in `OCLHighTier` right after
  `HighTierLoweringPhase`). It switches on `invoke.callTarget().targetName()` and handles
  `Direct#KernelContext.allocate{Int,Long,Float,Double,Byte,HalfFloat}LocalArray`,
  `Direct#KernelContext.{localBarrier,globalBarrier}`, `Direct#OpenCLIntrinsics.*`,
  `Direct#NewArrayNode.newArray`.
- Also `OCLLoweringProvider#isLocalArrayInput` matches surviving `contains("LocalArray")` invokes at
  lowering time.
- Port: `TornadoXIntrinsicsReplacements` — ensure it recognises the same `Direct#...` target names
  (the JVMCI-era version only saw already-intrinsified nodes).

### B4. Kernel signature: skip KernelContext / AtomicInteger
- OpenCL: `OCLBackend.emitMethodParameters` `continue`s past a `KernelContext`/`AtomicInteger`
  parameter so it is not emitted as a `__global` buffer arg.

### B5. LIR prologue: FrameState-only special params
- OpenCL: `OCLNodeLIRBuilder.emitPrologue` — a `KernelContext`/`AtomicInteger` parameter whose only
  remaining usages are `FrameState`s (deopt) must not load a buffer; bind the `KernelContext` param to
  the declared `KERNEL_CONTEXT` slot. Helpers `isSpecialKernelParameter`, `isKernelContextParameter`,
  `hasNonFrameStateUsage`.

### B6. Array addressing uses the Panama header size
- OpenCL: `OCLLoweringProvider` uses `(int) TornadoOptions.PANAMA_OBJECT_HEADER_SIZE` for panama
  array element addresses; raw arrays go through `TornadoVMConfigAccess` (Unsafe-backed).
- Also the host-side raw-array wrapper header must match the device: `OCLArrayWrapper` uses
  `PANAMA_OBJECT_HEADER_SIZE` (fixed 16) rather than the JVM base offset.

### B7. Local HALF arrays + HalfFloat read/write
- OpenCL: `TornadoHalfFloatReplacement` rewrites `allocateHalfFloatLocalArray` → HALF `LocalArrayNode`;
  `OCLLoweringProvider` lowers `LoadIndexed` on a local-HALF array to `ReadHalfFloatNode`.

### B8. String constants in printf
- OpenCL: `OCLAssembler.formatConstant` escapes a `TornadoObjectConstant` wrapping a `String` as a C
  string literal (a `HotSpotObjectConstant` no longer appears on the reflection path).

### B9. @Reduce host reduction (if the backend supports @Reduce)
- Shared: `ReduceTaskGraph.runHostTailReduction` runs the reduction method reflectively (no JVMCI
  compile+install). Started only after `setNeutralElement()` (avoids the neutral-refill race).

---

## C. Validation per JDK

Set `JAVA_HOME`, then:

```bash
bin/compile --jdk jdk<N> --backend <x>     # N ∈ {21,25,26,27}
source setvars.sh
tornado-test --verbose --quickPass          # NOT `make fast-tests` (--ea breaks frozen Graal on JDK≥18)
```

The launcher auto-selects module wiring (vendored `jdk.internal.vm.ci` module on 27+, `--patch-module`
on 22–26). Non-OpenCL backends additionally require their native runtime (CUDA/PTX toolkit, Level Zero
for SPIR-V, Metal on macOS); without a device the backend can only be **compile-checked**:

```bash
./mvnw -Pjdk27,<x>-backend -DskipTests -pl tornado-drivers/<x> -am compile
```

---

## D. Known out-of-scope failures (device/FP, not JVMCI-related)
matrices (transposed-formula tests), `atomics#testAtomic18_*`, half-float `.ftz` device precision,
`testJuliaSets`, `testMandelbrot`, `testComputePi`, `testReductionOneBlockWithLayer`,
`testHalfFloatLocalArrayToFloat`. These fail identically on the reference (OpenCL) backend and are not
gates for a port.
