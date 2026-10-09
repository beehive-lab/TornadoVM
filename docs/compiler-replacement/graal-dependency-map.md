# Graal and JVMCI dependency map (Phase 2, static)

Recorded 2026-10-09 on `compiler/main` (reference `ae409166a`). Every count below comes from a grep
over `src/` with `target/` excluded. The dynamic trace (which classes actually run) is a separate
Phase 2 item, and its results go into `phase-migration-matrix.md`.

Path abbreviations used in all three Phase 2 documents:

| Short | Path |
|---|---|
| `RT` | `tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime` |
| `DC` | `tornado-drivers/drivers-common/src/main/java/uk/ac/manchester/tornado/drivers` (`common/…`, `providers/…`) |
| `CU` | `tornado-drivers/cuda/src/main/java/uk/ac/manchester/tornado/drivers/cuda` |
| `OCL` | `tornado-drivers/opencl/src/main/java/uk/ac/manchester/tornado/drivers/opencl` |
| `MTL` | `tornado-drivers/metal/src/main/java/uk/ac/manchester/tornado/drivers/metal` |

Classification legend:
- **MUST REPLACE**: TC needs its own equivalent before Graal can go.
- **CAN SIMPLIFY**: the function is needed, but in a much smaller form, because a source-emitting GPU compiler needs far less than a HotSpot JIT.
- **CAN REMOVE**: has no role once TC is the compiler, or is already dead.

## 1. Graal (`tornado.graal.compiler.*`) usage per module

Counts are **files** that import at least one class from the package. The command used was
`find <module>/src -name '*.java' | while read f; do grep -hoE '^import (static )?tornado\.graal\.compiler\.[a-zA-Z0-9_]+' "$f" | sort -u; done | sort | uniq -c`.
Total import lines are given for scale.

| Graal package | runtime | drivers-common | cuda | opencl | metal | annotation |
|---|---:|---:|---:|---:|---:|---:|
| `nodes` (IR node classes, `spi`, `calc`, `memory`, `java`, `extended`, `loop`, `cfg`, `graphbuilderconf`) | 57 | 22 | 170 | 115 | 122 | 0 |
| `graph` (Node / Graph / NodeClass / iterators) | 45 | 10 | 141 | 89 | 96 | 0 |
| `core` (almost all `core.common`: `type` stamps, `LIRKind`, `cfg`, `spi`; plus `core.gen`, `core.match`) | 31 | 6 | 160 | 110 | 117 | 0 |
| `nodeinfo` (`@NodeInfo`, cycle/size annotations) | 30 | 0 | 120 | 73 | 80 | 0 |
| `phases` (`BasePhase`, `PhaseSuite`, `tiers`, `common`, `util.Providers`, `schedule`) | 32 | 19 | 38 | 33 | 33 | 0 |
| `lir` (`Variable`, `LIRInstruction`, `gen`, `framemap`, `asm`, `phases`, `ssa`) | 12 | 1 | 138 | 91 | 98 | 0 |
| `options` (`OptionValues`, `OptionKey`) | 6 | 2 | 15 | 15 | 15 | 0 |
| `debug` (`DebugContext`, `GraalError`, timers) | 4 | 3 | 7 | 7 | 7 | 0 |
| `replacements` (snippets, `StandardGraphBuilderPlugins`, `DefaultJavaLoweringProvider`) | 2 | 0 | 6 | 6 | 6 | 0 |
| `api` (`SnippetReflectionProvider`, `@Snippet`, `@Fold`) | 3 | 0 | 5 | 5 | 5 | 0 |
| `code` (`CompilationResult`, `DataSection`) | 0 | 0 | 5 | 5 | 5 | 0 |
| `asm` (`Assembler`, `Label`) | 0 | 0 | 2 | 2 | 2 | 0 |
| `loop` (`LoopTransformations`, `DefaultLoopPolicies`, `LoopFullUnrollPhase`) | 0 | 3 | 1 | 1 | 1 | 0 |
| `word` (word-type support) | 0 | 1 | 2 | 2 | 2 | 0 |
| `java` (bytecode parser: `GraphBuilderPhase`) | 2 | 0 | 1 | 1 | 1 | 0 |
| `printer` (`GraalDebugHandlersFactory`, IGV dumps) | 2 | 0 | 1 | 1 | 1 | 0 |
| `hotspot` (`HotSpotGraalOptionValues`) | 1 | 0 | 1 | 1 | 1 | 0 |
| `bytecode` (`ClassfileBytecodeProvider`) | 1 | 0 | 0 | 0 | 0 | 0 |
| `virtual` (`PartialEscapePhase`) | 0 | 0 | 1 | 1 | 1 | 0 |
| **Files importing any Graal package** | **83** | **27** | **217** | **155** | **162** | **0** |
| Import lines (all packages) | 518 | 197 | 1914 | 1386 | 1445 | 0 |

The `phases`, `core`, `nodes` and `lir` packages break down further as follows (files):

| Module | `phases.*` | `nodes.*` | `lir.*` |
|---|---|---|---|
| runtime | util 11, tiers 8, common 8 | calc 18, spi 17, java 9, extended 6, memory 5, loop 4, graphbuilderconf 2 | gen 7, phases 5 |
| drivers-common | common 3, util 2, tiers 1 | java 6, loop 5, extended 5, memory 4, spi 3 | — |
| cuda | common 11, util 10, tiers 3, schedule 2, graph 1 | spi 121, calc 44, memory 27, java 15, graphbuilderconf 12, extended 9, util 7, cfg 7 | gen 97, framemap 7, asm 7, phases 3, ssa 1 |

Graal SDK modules that are not relocated (`org.graalvm.*`) are also used directly:
- `org.graalvm.collections` (`EconomicMap`): runtime 2 files, cuda 6, opencl 6, metal 6.
- `org.graalvm.word`: drivers-common 2, cuda 8 lines / 7 files.

### Graal outside the `graal/` packages (runtime core coupling)
These files sit outside any `graal/` package but still import Graal. They are where a TC switch
has to cut the runtime loose:

| File | What it takes from Graal |
|---|---|
| `RT/TornadoBackendProvider.java` (backend SPI) | `OptionValues` appears in the provider interface signature |
| `RT/TornadoAcceleratorBackend.java` | `Providers` |
| `RT/TornadoCoreRuntime.java` | builds the global Graal `OptionValues` from `HotSpotGraalOptionValues` defaults plus overrides (`:128-137`), `DebugContext`, the IGV printer |
| `RT/sketcher/{TornadoSketcher,SketchRequest,Sketch}.java` | the sketch *is* a Graal `Graph` / `StructuredGraph`; the request carries a Graal `PhaseSuite` |
| `RT/tasks/TornadoTaskGraph.java`, `RT/tasks/ReduceTaskGraph.java` | keep the sketch graph (`compilationGraph`) and pass it on to the reduce rewriting |
| `RT/analyzer/{CodeAnalysis,ReduceCodeAnalysis,MetaReduceTasks}.java` | a **second, independent bytecode→Graal-graph build** (its own `GraphBuilderPhase`) used to analyse `@Reduce` task graphs on the host |
| `RT/common/RuntimeUtilities.java`, `RT/profiler/FeatureExtractionUtilities.java` | Graal loop analysis (`LoopsData`, `LoopEx`) and `StructuredGraph` |
| `RT/jvmci/TornadoConstantReflectionProvider.java` | `SnippetReflectionProvider` |
| `DC/providers/*` (5 files), `DC/common/utils/*`, `DC/common/code/CodeUtil.java`, `DC/common/compiler/cfg/PostDominators.java` | Graal provider implementations, the CFG used by the structurizer |
| `CU/CUDABackendImpl.java`, `CU/CUDATornadoDriverProvider.java`, `CU/CUDATargetDescription.java`, `CU/builtins/CUDAIntrinsics.java` | backend bootstrap, `@Fold` on intrinsic stubs |
| `CU/runtime/CUDATornadoDevice.java` (no Graal import of its own) | reaches into a **Graal node class's static map** (`TornadoAtomicIntegerNode.globalAtomicsParameters`) after compilation to fix up the atomics buffer |

## 2. JVMCI (`jdk.vm.ci.*`): a separate track

**JVMCI is not Graal.** TornadoVM uses JVMCI as an *interface library* for describing methods,
types, fields, constants and kinds. On JDK 27+ it comes from a vendored copy (`tornado.jvmci:jvmci`
21.0.2, built by `bin/build_jvmci_module.py`). JVMCI is also implemented reflectively in
`RT/jvmci/reflection/`. Removing it is assessed separately (plan Phase 8, PR #894/#1192). TC must
not import it (enforcer rule), but TC's runtime glue will still receive JVMCI objects such as
`ResolvedJavaMethod` from the runtime until that track is done.

| JVMCI package | runtime | drivers-common | cuda | opencl | metal | annotation |
|---|---:|---:|---:|---:|---:|---:|
| `meta` (`JavaKind`, `Value`, `ResolvedJavaMethod/Type/Field`, `JavaConstant`, `MetaAccessProvider`, `PlatformKind`, `Local`, `Signature`) | 59 | 10 | 189 | 137 | 139 | 1 |
| `code` (`TargetDescription`, `Architecture`, `Register`, `CallingConvention`, `CodeCacheProvider`, `InstalledCode`, `StackSlot`) | 3 | 2 | 17 | 17 | 17 | 0 |
| `hotspot` (`HotSpotCallingConventionType`) | 0 | 0 | 2 | 2 | 2 | 0 |
| `common` (`InitTimer`) | 0 | 0 | 1 | 1 | 1 | 0 |
| **Files importing any JVMCI package** | **59** | **11** | **194** | **142** | **144** | **1** |

The most-used classes (import lines, all modules) are `JavaKind` 297, `Value` 234, `ResolvedJavaMethod` 86,
`ResolvedJavaType` 58, `JavaConstant` 44, `MetaAccessProvider` 39 and `AllocatableValue` 33.

JVMCI also reaches into the runtime and launch path, not only into the compiler:
- `ResolvedJavaMethod` is the sketch cache key (`RT/sketcher/TornadoSketcher.java`) and is stored in task metadata (`RT/tasks/meta/AbstractRTContext.java`) and in the device interface (`RT/common/TornadoXPUDevice.java`).
- `CU/graal/CUDAInstalledCode.java` (the launch object) extends JVMCI `InstalledCode`.
- `CU/mm/*Wrapper.java` (10 files) use `JavaKind` for buffer element kinds.
- `tornado-annotation/.../ASMClassVisitor.java` takes a `ResolvedJavaMethod` to locate the class file whose `@Parallel` local-variable annotations it reads.
- `RT/TornadoVMConfigAccess.java` reads VM struct offsets the way `HotSpotVMConfigAccess` does.

Classification: **JVMCI = out of scope for TC (separate track).** TC takes `java.lang.reflect.Method`
plus class bytes and gives back plain data, so the TC boundary is JVMCI-free. The `CU` glue converts.

## 3. Non-Java coupling

| Item | Location (line numbers) | What it does | Class. |
|---|---|---|---|
| Root pom `graalvm.version` | `pom.xml:64` | pins 23.1.0 | MUST REPLACE (delete at C8) |
| Root pom **global** `<dependencies>` | `pom.xml:756-806`: `org.graalvm.sdk:word`, `org.graalvm.sdk:collections`, `org.graalvm.truffle:truffle-compiler` (`:778-794`), `tornado.graal:tornado-graal:23.1.0` scope provided (`:796-804`) | declared in the parent's `<dependencies>`, not `<dependencyManagement>`, so **every module inherits them, `tornado-api` and the `cu*` libraries included** | MUST REPLACE: move to the driver/runtime poms first, then delete |
| `jdk22plus` profile | `pom.xml:453-538` (id at `:476`): `--release 22` (comment at `:466-468` says 22 is the ceiling imposed by Graal's classfile reader); vendored `tornado.jvmci:jvmci` (`:486-493`) | the bytecode-level cap | MUST REPLACE (cap lifted at C8; jvmci part is the JVMCI track) |
| Compiler export lists | `pom.xml:142-145, 429-432, 529-532, 582-585` (`@…/exportLists/*-exports`) | javac `--add-exports` for every profile | CAN SIMPLIFY |
| Test argLines | `pom.xml:377`, `:551` | JVMCI add-exports / patch-module for surefire | JVMCI track |
| `bin/graal-relocate/pom.xml` | whole file (shade relocation `org.graalvm.compiler`→`tornado.graal.compiler`, `:80-89`) | helper POM driven by the build script | CAN REMOVE (C8) |
| `bin/build_graal_module.py` (256 LOC) | called from `bin/pull_graal_jars.py:207-208`, which `bin/compile:33,182-191,717` calls | relocates Graal into module `tornado.graal` and installs it in `~/.m2` | CAN REMOVE (C8) |
| `bin/pull_graal_jars.py` | `:31-39` (Maven Central `org/graalvm` URLs), `:117-208` | downloads Graal jars, validates the relocated jar, calls the two module builders | CAN SIMPLIFY (JVMCI module staging may remain) |
| `bin/minimize_graal_jar.py` (159 LOC) + `bin/graal-compiler-keep.txt` (2457 class entries) | **not called by anything**: the only references are the script itself and `plan.md` | trims the Graal jar to a measured class keep-list | CAN REMOVE **now** (dead tooling) |
| `.github/workflows/build-test-platform.yml:106`, `deploy-maven-central.yml:101-104` | CI runs `pull_graal_jars.py` | CI coupling | CAN SIMPLIFY |
| `Makefile:42,58-68` | `graal-jdk-21` profile and targets (GraalVM *JDK*, not the compiler) | build-JDK choice | unaffected |
| `tornado-assembly/assembly.xml:135-159` | ships `graalJars/tornado-graal-*.jar` + SDK jars to `share/java/graalJars`, and `jvmci-*.jar` to `share/java/jvmci` | SDK packaging | MUST REPLACE (drop the graal part) |
| `tornado-assembly/src/etc/exportLists/common-exports` | 30 lines; **26 of them name `tornado.graal`** (JVMCI packages exported/opened to Graal, truffle-compiler and word-impl exports) | runtime module exports | CAN REMOVE the 26 at C8 |
| `…/exportLists/{cuda,opencl,metal}-exports` | 8 lines each (metal has a 23-line header) | JVMCI `meta/code/common/runtime/hotspot/amd64` exported to each driver | JVMCI track |
| `bin/bin/tornado.py` (symlink into `dist/…/bin`; source `tornado-assembly/src/bin/tornado.py`, identical) | `:39-40` IGV flags (`-Dgraal.Dump…`, `-Dtornado.debug.lowtier`), used at `:1015-1019` and declared at `:1442-1445` (`--igv`, `--igvLowTier`) | Graal graph dumps | MUST REPLACE with `-Dtornado.tc.dump` equivalents |
| | `:67` `__TORNADOVM_ADD_MODULES__` contains `tornado.graal`, used at `:1282` | module resolution | CAN REMOVE (C8) |
| | `:85` `-XX:+EnableJVMCI`; `:93-107` no-JVMCI flags for JDK 27+; `:408-418`, `:1047-1072` vendored/patched jvmci | JVMCI launch flags | JVMCI track |
| | `:110` `-ea -da:tornado.graal...` used at `:1285-1286` (`--enableAssertions`) | turns Graal's own assertions off because TornadoVM graphs violate them | CAN REMOVE (C8) |
| | `:1074-1088` `--upgrade-module-path share/java/graalJars` for GraalVM-branded JDKs | module clash workaround for the Graal SDK jars | CAN REMOVE (C8) |
| | `:63-66`, `:1317-1320` export-list files | see above | CAN SIMPLIFY |
| `tornado-assembly/src/bin/tornado-test:504-505` | IGV option strings | test-runner debugging | MUST REPLACE (TC dump flags) |
| `tornado-unittests/pom.xml:112,121` | module path includes `share/java/graalJars`; `--add-modules … tornado.graal …` | exec plugin for tests | CAN REMOVE (C8) |
| `module-info.java` files `requires transitive tornado.graal` | `tornado-runtime/src/main/java/module-info.java:11` (plus `requires org.graalvm.collections` `:8`, `requires transitive jdk.internal.vm.ci` `:10`); `tornado-drivers/drivers-common/…/module-info.java:3`; `cuda/…:5` (+ `org.graalvm.collections`, `org.graalvm.word` `:6-7`); `opencl/…:6-8`; `metal/…:5-7` | `transitive` exposes Graal to everyone who requires these modules | MUST REPLACE: becomes non-transitive first, then deleted |
| | runtime exports `…runtime.graal.*` (9 packages, `:24-41`); each driver exports `…<backend>.graal.*` (12 packages) | the Graal-typed classes are public module API of runtime and drivers | CAN REMOVE (C8) |
| | `tornado-annotation/src/main/java/module-info.java:19` `requires transitive jdk.internal.vm.ci` | JVMCI only | JVMCI track |
| `META-INF/services` | only `uk.ac.manchester.tornado.runtime.TornadoBackendProvider` (`{cuda,opencl,metal}/src/main/resources/META-INF/services/`) and `…library.spi.TornadoLibraryProvider` (8 `tornado-cu*` modules) | **no Graal service entries in TornadoVM sources**. Graal's own internal `ServiceLoader` use lives inside the vendored jar. | unaffected |
| Annotation processors | root pom only lists JMH processors (`:130-136` and the other profiles) | no Graal processors are used. `@NodeInfo` nodes need none at build time, and `@Fold` in `CU/builtins/CUDAIntrinsics.java` is handled by the plugin registry. | — |

## 4. Public API leakage

**Verified: no Graal or JVMCI type is reachable from the user-facing API.**
- `tornado-api`: 0 files import `tornado.graal` or `jdk.vm.ci`; a grep for the substring `graal` in `tornado-api/src` returns nothing. Its `module-info.java` requires only `java.management`.
- `tornado-unittests`, `tornado-examples`, `tornado-benchmarks`, `tornado-matrices`, `tornado-cu*`: 0 imports of `tornado.graal`, `jdk.vm.ci`, `…runtime.graal` or `…drivers.*.graal`. The `cu*` libraries `requires tornado.runtime` (not transitive), which only brings in Graal at module-resolution time.

Two coupling caveats that are not API leaks:
1. **Build-level leak.** Because the Graal deps sit in the root pom's `<dependencies>`, `tornado-api` is *compiled* with the Graal jars on its path. The project skill notes that a stale `graalJars/` breaks the first module (`tornado-api`) with "cannot access module-info" (`.claude/skills/tornadovm/SKILL.md:31`).
2. **Semantic leak.** `@Parallel` and `@Reduce` (`tornado-api/.../annotations/{Parallel,Reduce}.java`) are `LOCAL_VARIABLE`/`TYPE_USE` annotations. They are not visible through reflection, so they are read from the class file by ASM (`tornado-annotation/.../ASMMethodVisitor.java:40`, local-variable type annotations), loaded reflectively by `RT/graal/phases/sketcher/TornadoApiReplacement.java` via `-Dtornado.load.annotation.implementation`. The TC frontend has to read the same `Runtime{Visible,Invisible}TypeAnnotations` (target `LOCAL_VARIABLE`) itself.

## 5. Classification per Graal group

| Group | Class. | Rationale |
|---|---|---|
| `java` bytecode parser + `graphbuilderconf` plugins | **MUST REPLACE** | TC frontend (`tc-frontend`): bytecode → SSA, with an intrinsic-call registry in place of `InvocationPlugin`s |
| `graph` + `nodes` + `nodeinfo` (sea-of-nodes IR) | **MUST REPLACE** | `tc-ir` with block-argument SSA; 46 runtime and 122 CUDA node classes map to ops/intrinsics (see matrix) |
| `phases` framework (`BasePhase`, `PhaseSuite`, tiers, contexts) | **MUST REPLACE** | `tc-opt` pass manager; the tiers collapse into one ordered pipeline |
| `phases.common` built-ins (canonicalizer, conditional elimination, DCE, inlining, lowering, guards) | **MUST REPLACE** (subset) | see the built-in table in the matrix: about 6 are needed, the rest are subsumed or unnecessary |
| `loop` (`LoopsData`, `LoopEx`, `LoopTransformations`, `DefaultLoopPolicies`) | **MUST REPLACE** | loop info + full/partial unroll in `tc-opt`; also used by the runtime analyzer |
| `virtual` (partial escape analysis) | **CAN SIMPLIFY** | GPU kernels only need scalar replacement of small value objects (vector types, `HalfFloat`); full PEA is unnecessary |
| `replacements` (snippets, `DefaultJavaLoweringProvider`, `StandardGraphBuilderPlugins`) | **CAN SIMPLIFY** | reduction snippets become hand-written IR templates in `tc-tornado`; Java-semantics lowering becomes a small table (array/field access, div/rem, conversions); `java.lang.Math` intrinsics go into the TC intrinsic registry |
| `core.common.type` (stamps) | **CAN SIMPLIFY** | TC types are plain `i1..i64/f16/bf16/f32/f64/vector/ptr addrspace`; stamp lattices (ranges, non-null) are only needed for a few folds |
| `lir`, `core.gen`, `core.match`, `asm`, `code` (LIR + "assembler" that prints text) | **CAN REMOVE** | TC emits source from structured IR (`tc-lower` → `tc-codegen`). There is no register allocation (the allocation stage is empty, see `RT/graal/phases/TornadoAllocationStage.java`). The LIR → text step disappears, and so do `CUDAGlobalLoadBatching`'s LIR reordering and `PureRegisterComputation` (re-expressed as an IR pass if still needed) |
| `options` | **CAN SIMPLIFY** | TC reads `TornadoOptions` / `-Dtornado.tc.*`; Graal option defaults (`TornadoCoreRuntime:128-137`) no longer apply |
| `debug`, `printer`, `hotspot` | **CAN REMOVE** | replaced by TC dumps / HTML report / JFR; `HotSpotGraalOptionValues` is only used for option defaults |
| `word`, `org.graalvm.word` | **CAN REMOVE** | word types only serve Graal's lowering plumbing |
| `org.graalvm.collections` | **CAN REMOVE** | swap for `java.util` collections in the runtime/drivers that remain |
| `api` (`SnippetReflectionProvider`, `@Fold`, `@Snippet`) | **CAN REMOVE** | snippet infrastructure; `@Fold` on intrinsic stubs is replaced by the registry |
| `bytecode` (`ClassfileBytecodeProvider`) | **CAN REMOVE** | TC reads class bytes through its own `BytecodeSource` |
| Build tooling (`build_graal_module.py`, relocation POM, pull script Graal part, assembly graal fileSet, export-list Graal lines, `tornado.py` Graal flags) | **CAN REMOVE** at C8 | listed in plan Phase 8 |
| `minimize_graal_jar.py` + keep-list | **CAN REMOVE now** | not wired into any build or CI path |

## 6. Notes for the clean-room policy
- 391 Java files in runtime/drivers-common/cuda/opencl carry an "Oracle and/or its affiliates" copyright line next to the APT one (runtime 58, drivers-common 22, cuda 177, opencl 134, metal 0). Most of these look like copied boilerplate: even `CU/CUDATileCompiler.java`, a 2025 nvcc driver with no Graal content, has it. So the line is not evidence of derivation. But some classes are explicitly modified Graal phases, e.g. `CU/graal/phases/TornadoFloatingReadReplacement.java`, whose Javadoc says it modifies Graal's `FloatingReadPhase`. **Rule for TC authors:** treat such files like Graal (black-box behaviour only), and read only TornadoVM-original logic (Tornado-specific phases, ABI, intrinsic names) as a behavioural reference.
