# Graal removal: inventory and plan (Phase 8)

Prepared 2026-10-11 on `compiler/main` (efc5c396f), to execute **after Metal passes** (`METAL.md`).
Everything that ties TornadoVM to the vendored Graal compiler or to JVMCI, and the order to remove it.
Counts are from `grep` over the sources (no `target/`, no `dist/`).
[graal-dependency-map.md](graal-dependency-map.md) is the Phase 2 view; this one is current.

## 1. Size

| Area | Graal/JVMCI users | Delete whole | Rewrite |
|---|---:|---:|---:|
| `tornado-drivers/cuda` | 237 main + 3 test files | 216 (`graal/`) | 21 |
| `tornado-drivers/metal` | 182 | 163 (`graal/`) | 19 |
| `tornado-drivers/opencl` | 176 | 156 (`graal/`) | 20 |
| `tornado-runtime` | 99 main + 4 test | 69 (`runtime/graal/`) + 12 (`runtime/jvmci/**`) | 18 |
| `tornado-drivers/drivers-common` | 29 | ~22 (Graal phases, providers) | ~7 |
| `tornado-annotation` | 1 | – | 1 |
| **Java total** | **731 files** | **~640** | **~90** |
| `module-info.java` | 18 `requires` lines on `tornado.graal`, `jdk.internal.vm.ci`, `org.graalvm.*` | | 18 |
| Unit tests mentioning Graal | 7 (`tile/TestTileMatmul`, `logic/TestEnumInKernel`, `fails/MultiDimArrayFail`, `vm/concurrency/TestConcurrentBackends`, `compiler/TestSwitchLowering`, `branching/TestConditionals`, `fuzz/TestCudaCodegenRegressions`) | | 7 (mostly comments or Graal-only expectations) |
| SDK payload | `share/java/graalJars` 8.7 MB; `share/java/jvmci` (JDK 22+ SDK) | all | |
| Launcher argfile | 46 of 60 flag lines are Graal/JVMCI | 46 | |

## 2. Prerequisites: what Graal still does on a TC compile

Selecting TC replaces only the kernel compiler. These runtime features still run through Graal or
JVMCI, so they need TC (or plain Java) replacements **before** anything is deleted. Each item gets a
differential check against the Graal path while both still exist.

| # | Feature | Graal/JVMCI code today | Replacement | Check |
|---|---|---|---|---|
| P1 | **Parameter access** (`Access[]`: read, write, read-write per argument) | `TornadoSketcher` builds a Graal graph per task. `XXXTornadoDevice.compileTask` copies `sketch.getArgumentsAccess()`. C4c (TC's own `Access[]`) never became runtime code | Access analysis on TC IR in `tc-tornado`; `TcHook` returns it with the kernel | For every unit-test task, TC `Access[]` equals the sketch's (headless, like EmitSweep) |
| P2 | **`@Reduce` task-graph rewriting** | `TornadoTaskGraph` → `MetaReduceCodeAnalysis`, `analyzer/CodeAnalysis` and `ReduceCodeAnalysis` (Graal graph patterns: `AddNode`, `ArrayLengthNode`, …), `ReduceTaskGraph` | Same analysis on TC IR (the frontend already sees `@Reduce` and the operation) | `reductions.*` and `TestReductions*` families, both compilers |
| P3 | **Method identity** across the runtime API | `ResolvedJavaMethod` in `TornadoXPUDevice`, `TaskDataContext` / `AbstractRTContext` (`getCompiledResolvedJavaMethod`), the atomics maps (`TornadoAtomicIntegerNode.globalAtomics*` keyed by method), `TornadoGraphBuilder`, `ASMClassVisitor`, `CompilerUtil`; `TornadoCoreRuntime.resolveMethod` | `java.lang.reflect.Method`, or a small `MethodRef(owner, name, descriptor)` record in `tornado-runtime` | Compiles, then full suite |
| P4 | **Object field layouts and VM configuration** | `mm/XXXFieldBuffer` and `TcArguments.describe` read offsets through `getMetaAccess().lookupJavaType(...).getInstanceFields`; `TornadoVMConfigAccess` reads HotSpot config through JVMCI (`coopsUsed`, header sizes) | `Unsafe.objectFieldOffset` (tcirviz already does this); `HotSpotDiagnosticMXBean` for `UseCompressedOops`; `Unsafe.arrayBaseOffset` | `fields.*`, `instances.*`, `matrices.*`, `vectortypes.*` |
| P5 | **Backend plumbing** | `TornadoAcceleratorBackend` (`Providers`), `TornadoBackendProvider` (`OptionValues`), `MetaCompilation` (`InstalledCode`), `drivers/providers/*`, `XXXBackendImpl`, `XXXTargetDescription`, `XXXTornadoDriverProvider`, `XXXJIT` | Plain interfaces: a backend holds a device context; TC caps describe the target | Builds; `tornado --devices` |
| P6 | **`JavaKind` in buffer wrappers** (`mm/*ArrayWrapper`, `CommandQueue`) | `jdk.vm.ci.meta.JavaKind` as a type tag | A `tornado-runtime` enum (or `Class<?>`); mechanical, about 45 files | Builds; `arrays.*` |
| P7 | **Profiler feature extraction** (`-Dtornado.feature.extraction`) | `FeatureExtractionUtilities` over `StructuredGraph` | The same counts over TC IR, or drop the option | `profiler.*` |
| P8 | **Graal debug features** | `--igv`, `--igvLowTier`, `-Dgraal.*`, `tornado.debug.lowtier`, `__GRAAL_ENABLE_ASSERTIONS__`, Graal parts of `--debug`/`--fullDebug` | TC equivalents already exist: `-Dtornado.tc.dump`, HTML report, `tornado --tcirviz`. Map `--igv` to an error that names them | Launcher help and manual check |
| P9 | **Virtual devices** (`drivers/*/virtual`) | `ResolvedJavaMethod` only | Covered by P3 | `virtual.*` |
| P10 | **TC fallback** | `-Dtornado.compiler.fallback` defaults to Graal | Remove the option; a TC diagnostic becomes a bailout to the host Java code, as Graal's failures are today | `fails.*` |

### The metric: Graal classes loaded under TC

`baseline/graal-probe.sh` runs an application under TC with class-load logging and counts what
still comes from Graal or JVMCI. Graal goes **completely** only when this is 0 on every backend.
That proves that no runtime path needs Graal, so the deletion in §3 is mechanical.

Baseline (2026-10-11, one saxpy kernel, RTX 4090, CUDA and OpenCL drivers loaded): 5790 classes, of
which:

| Origin | Classes |
|---|---:|
| `tornado.graal.*` (the vendored compiler) | 1024 |
| TornadoVM's own `*.graal.*` packages | 716 |
| `jdk.vm.ci.*` (JVMCI) | 65 |
| `org.graalvm.*` | 28 |
| TC, for comparison | 406 |

`tornado --devices` alone, with no kernel compiled, loads 712 `tornado.graal`, 694 TornadoVM `graal`
and 47 JVMCI classes: about 70% of the total comes from startup. Most of the 716 come from there: every driver eagerly builds a Graal backend
per device (providers, lowering, phase suites), for example 232 classes in `drivers.cuda.graal.compiler`
and 162 in `drivers.opencl.graal.compiler`. The first load is `RuntimeUtilities` → `LoopsData`.

The per-device object (`OCLBackend`, `CUDABackend`, `MetalBackend`) *is* a Graal backend subclass
holding the device context, and the runtime reaches devices through it. So the decoupling is:
- a plain per-device backend owns the device context and memory;
- the Graal compiler is built lazily, only if Graal compiles (never under TC), and is then deleted.

Work in the order that drives the probe to 0:
1. P1/P2: no sketch, no Graal analysis.
2. Driver startup: lazy Graal backends.
3. P3: method identity.
4. P4/P6: field layouts and `JavaKind`.
5. The remaining stragglers the probe names.

## 3. Delete outright, once P1–P10 are done

**Java.**
- `tornado-drivers/{cuda,opencl,metal}/src/main/java/.../graal/**`: 535 files.
- `tornado-drivers/drivers-common/.../common/compiler/{phases,cfg}/**`, `drivers/providers/*` and `common/code/CodeUtil`, `common/utils/BackendDeopt`, `common/architecture/ArchitectureRegister`.
- `tornado-runtime/.../runtime/graal/**` (69).
- `tornado-runtime/.../runtime/jvmci/**`: the JVMCI-shaped reflection universe and constant providers, about 12. TC has its own class-file reader.
- `tornado-runtime/.../runtime/sketcher/**` (after P1) and `runtime/analyzer/**` (after P2).

**Build and tooling.**

| File | What it does for Graal |
|---|---|
| `bin/build_graal_module.py` | builds the relocated `tornado.graal` module |
| `bin/graal-relocate/` (`pom.xml`) | the relocation build |
| `bin/pull_graal_jars.py` | downloads the Graal and JVMCI jars into `graalJars/` |
| `bin/build_jvmci_module.py` | builds the vendored `jdk.internal.vm.ci` for JDK 22+ and 27 |
| `graalJars/` (staging) | downloaded and relocated jars |
| `tornado-assembly/assembly.xml` | the two `graalJars` filesets → `share/java/graalJars`, `share/java/jvmci` |
| `tornado-assembly/src/etc/exportLists/*` | 29 of 30 lines of `common-exports`, 7 of 8 of each backend list |
| `scripts/templates/settings.xml` | the `tornado-jvmci` Maven profile (`jvmci.root`, `jvmci.version`) |

## 4. Edit

| File | Graal/JVMCI lines | Change |
|---|---:|---|
| `pom.xml` | 28 | Drop the Graal and JVMCI dependencies, the `graal-jdk-21` profile, the JVMCI `--add-exports`/`--patch-module` compiler arguments, the surefire JVMCI argLine, and `<release>22</release>`. Collapse `jdk21`/`jdk22plus` into one profile if nothing else differs (FFM preview on 21 remains) |
| `tornado-drivers/{cuda,drivers-common}/pom.xml`, `tornado-unittests/pom.xml` | 2, 1, 3 | Drop Graal dependencies; unittests exec drops `-XX:+EnableJVMCI`, `graalJars` and `tornado.graal` |
| `tornado-compiler/pom.xml` | 1 | Keep: the enforcer rule that bans Graal imports. Extend it to the whole build |
| 18 `module-info.java` | 18 | Drop `requires (transitive) tornado.graal / jdk.internal.vm.ci / org.graalvm.*`; drivers `requires` the TC modules |
| `tornado-assembly/src/bin/tornado.py` | 65 | Drop `__JAVA_BASE_OPTIONS__` / `_NO_JVMCI__` (one base), the `jvmci_absent`/`jvmci_patched` logic and `TORNADOVM_JVMCI_MODE`, `--upgrade-module-path graalJars`, `__GRAAL_ENABLE_ASSERTIONS__`, the IGV flags (P8), and `tornado.graal` in `__TORNADOVM_ADD_MODULES__` |
| `tornado-assembly/src/bin/tornado-test` | 4 | IGV options (P8) |
| `tornado-assembly/src/bin/tornado`, `tornado.bat`, `readJsonFile.py`, `idea_xml_utils.py` | 3, 1, 2, 2 | Graal mentions and module lists |
| `tornado-assembly/src/bin/gen-tornado-argfile-template.py` | 17 | Drop the per-JVMCI-setup variants (`tornado-argfile-jdk22-26` / `-jdk27plus`): one argfile for every JDK |
| `bin/compile` | 36 | Drop `pull_graal_jars`, the `graal-jdk-21` JDK, and the JVMCI-shape checks |
| `bin/installer_config.py` | 56 | Drop the GraalVM JDK entries (`graal-jdk-21` … `-25`) or keep them as plain JDKs: GraalVM is then just another JDK |
| `bin/post_installation.py` | 21 | Drop `GRAAL_JARS_SDK_LAYOUT` |
| `bin/intellij_build.py` | 6 | Drop the `graal-jdk-21` profile detection |
| `scripts/build-release-sdks.py`, `scripts/set-maven-version.py` | 19, 1 | Drop `clean_graal_jars` and the Graal version property |
| `Makefile` | 8 | Drop `graal-jdk-21` targets and `test-reflection*` (they test the JVMCI reflection layer) |
| `tornado-assembly/src/etc/checkstyle.xml` | 1 | Graal-package suppression |
| `.github/workflows/*` | 54 lines in 9 workflows | `deploy-maven-central` 13, `unit-tests-reflection` 11 (delete the workflow), `build-test-platform` 11, `build-test-with-jdks21` 7, `build-release-sdks` 5, `heavy-memory-tests` 3, `build-test` 3, release workflows 1 each |
| `TornadoOptions` | | Drop `tornado.debug.lowtier`, `tornado.sketcher.threads`, and the `TornadoApiReplacement` reference; `tornado.compiler` and `tornado.compiler.fallback` go (P10) |
| Profiler | | `TASK_COMPILE_GRAAL_TIME` / `TOTAL_GRAAL_COMPILE_TIME` → keep the JSON keys for one release (tools read them), filled by TC |
| User docs | 20 files (`.rst`/`.md` outside `compiler-replacement`) | Installation (GraalVM JDKs, JVMCI), IGV debugging → TC dumps and tcirviz, the architecture pages |
| `docs/compiler-replacement/baseline/tc-diff.py`, `perf/*` | | Keep for history; the Graal comparison stops being runnable |

## 5. Order of work

Each step ends green on:
- the CUDA, OpenCL (RTX 4090 + UHD 770) and Metal suites through `tc-diff.py`, TC only, against the last Graal reference;
- jitLLM token identity;
- the JDK 21 / 25 / 26 / 27-ea matrix (`jdk-matrix.md`);
- `make` on both profiles, the argfile, and `tornado --tcirviz`.

1. **P1, TC `Access[]`.** Add the analysis and check it equal to the sketch's for every task. Then
   use TC's when TC compiles: the sketch stays only for Graal.
2. **P2, `@Reduce` analysis on TC IR**, checked equal to `MetaReduceCodeAnalysis` on every
   reduction test. TC mode then skips the Graal analysis.
3. **TC mode no longer touches Graal at run time**: no sketch, no Graal analysis. Measure by running
   the suite with `graalJars` taken off the module path. This is the gate that proves the rest is
   deletion.
4. **P3, P4, P6**: method identity, field layouts and `JavaKind` move to plain Java in the runtime
   and drivers, while Graal is still selectable. Mechanical, one module at a time.
5. **TC becomes the default** (`tornado.compiler=tc`) for one release, with Graal still selectable.
   Optional, if a release cycle is wanted.
6. **Delete** §3 and edit §4: drivers' `graal/`, runtime `graal/`, `jvmci/`, `sketcher/`,
   `analyzer/`, the build scripts and the SDK payload. Remove the fallback option (P10).
7. **JVMCI gone**:
   - no `-XX:+EnableJVMCI`, no `--patch-module`, no vendored `jdk.internal.vm.ci`;
   - one argfile for every JDK, about 14 flag lines;
   - lift `<release>22</release>`.

   Rerun the JDK matrix including 27, and the Leyden and native-image spikes (R3/R4 of
   beehive-lab/TornadoVM#1227).
8. **Docs and CI** (§4), checkpoint C8 in `checkpoints.md`:
   - `mvn dependency:tree` and `jdeps` show no Graal or JVMCI;
   - a clean `~/.m2` build passes.

## 6. Risks

- **`@Reduce` semantics.** The Graal analysis decides the rewritten task graph (partial-result
  arrays, combine tasks). A mismatch gives wrong sums, not a crash. Step 2 compares the analyses
  structurally, not only the test results.
- **Field offsets without JVMCI.** `Unsafe.objectFieldOffset` gives the same offsets HotSpot
  reports. The runtime's field buffers and TC's layouts must change in the same commit, or objects
  are serialised one way and read another.
- **External users of runtime APIs that expose `ResolvedJavaMethod`** (P3): GPULlama3, jitLLM and
  the extension libraries may call them. Search those repos before changing signatures.
- **GraalVM JDKs as host JDKs.** They keep working as plain JDKs; only the `graal-jdk-21` build
  profile (Graal taken from the JDK) goes.
- **Metal.** Step 6 deletes the Graal Metal backend, so do it only after Metal passes on TC. This is
  why the plan waits for `METAL.md`.
