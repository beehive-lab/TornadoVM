# Plan: Graal-free compiler for TornadoVM ("tornado-compiler")

## Context
TornadoVM compiles Java kernels through a vendored, relocated Graal 23.1.0 (`tornado.graal.compiler.*`, built by `bin/build_graal_module.py`). 644 source files import it. The heaviest users are the drivers (CUDA 217, Metal 162, OpenCL 155) and the runtime (83). Graal also limits the bytecode level: `jdk22plus` pins `--release 22` because the frozen Graal classfile reader cannot read newer class files. The goal is an independent compiler: bytecode frontend → own SSA IR → passes → structured non-SSA IR → CUDA/OpenCL/Metal source. It must reach functional parity before Graal is removed, with Babylon last.

Decisions made with the user:
- **Repo:** private `mikepapadim/tornadovm-compiler`, a full mirror of beehive-lab `develop` @ `ae409166a`. The local `~/TornadoVM-develop` is behind at `669e1922a`. Work happens on branch `compiler/main`.
- **First parity backend:** CUDA (NVRTC) on the RTX 4090. OpenCL and Metal reuse the same structured IR afterwards.
- **Frontend:** our own `BytecodeSource` adapter. Impl 1 reuses the in-tree Graal/JVMCI-free `runtime/jvmci/reflection/ClassfileParser` (2.1k LOC, works on JDK 21). Impl 2 uses `java.lang.classfile` for JDK 24+.

Environment facts (measured):
- **Live backends:** opencl, cuda, metal. PTX and SPIR-V are stubs.
- **JDKs:** 21, 25, 26 and 27-ea are available through sdkman.
- **Devices:** RTX 4090 (CUDA + OpenCL) and Intel UHD 770 (OpenCL).
- **Tests:** 205 `Test*.java` files, 223 `tornado-test` entries. Biggest groups: kernelcontext 45, tile 22, api 21, arrays 18.

## Existing work to reuse (Phase 0 findings)
| Asset | Path | Use |
|---|---|---|
| Structured CF recovery (post-dominator based, PRs #848/#849/#1138) | `tornado-drivers/{cuda,opencl,metal}/.../graal/compiler/*StructuredControlFlow.java` (3 × 428 LOC) | Behavioural reference + regression cases only; TC structurizer written fresh from Ramsey 2022 (see IP policy) |
| Graal-free class/method model | `tornado-runtime/.../runtime/jvmci/reflection/` (`ClassfileParser`, `ReflectionUniverse`, …) | Frontend impl 1 |
| Prebuilt-source install path | `CUDATornadoDevice.compilePreBuiltTask` → `deviceContext.installCode(planId, meta, id, entry, source)` (`tornado-drivers/cuda/.../runtime/CUDATornadoDevice.java:315`) | Runtime hook: new compiler hands source to the existing NVRTC install |
| Graal compile hook | `CUDATornadoDevice.compileTask` (`:255`), which uses `TornadoSketcher.lookup` + `CUDACompiler.compileSketchForDevice` | Selection point (`-Dtornado.compiler=graal\|tc`) |
| Kernel ABI / intrinsic names | `--printKernel` output of the Graal path + CUDA/OpenCL/MSL specs | ABI captured as a black-box contract; intrinsic tables re-derived from vendor specs |
| C99 code-builder design | `~/src/babylon/hat/core/.../codebuilders/C99HAT*Builder.java` | Concept only (one C core, thin dialect layers); no code reuse (GPLv2) |
| SSA IR design references | `mikepapadim/Simple` (Sea of Nodes), ILGPU fork | Design input only |
| Conformance bug list | memory `cuda-codegen-quest.md` / issue #1149 (`>>>`, NaN min/max, `(long)float` saturation, shift masking) + PR #1054/#1043/#1138 cases | Seed tests for the semantics suite |
| Requirements input | `~/work/tornadovm-vs-babylon-cuda/docs/feature-matrix.md` | Feature-matrix seed |

## Repository and module layout
- Setup: `gh repo create mikepapadim/tornadovm-compiler --private`. Push beehive `develop` (full history) as `develop`, then branch `compiler/main`. Keep the beehive remote as `upstream` and rebase periodically.
- Worktree: `~/tornadovm-compiler`.
- New Maven modules under `tornado-compiler/`, with no dependency on `tornado.graal`, `jdk.vm.ci` or `tornado-drivers`. A build-time enforcer rule fails the build if any of them appears.
  - `tc-frontend`: `BytecodeSource` + `ClassfileParser` adapter, method resolution, inlining oracle.
  - `tc-ir`: typed SSA IR (blocks with block arguments instead of phis + instructions + explicit memory effects), verifier, text dump, `.dot` dump.
  - `tc-opt`: pass manager with LLVM-style declared analyses (preserved/invalidated), canonicalisation, constant folding, DCE, GVN, loop info, inlining.
  - `tc-tornado`: TornadoVM API lowering (KernelContext, `@Parallel`, reductions, atomics, local memory, vector types, `TornadoMath`), parameter access analysis (produces `Access[]`).
  - `tc-lower`: SSA destruction (block-argument edge copies, parallel-copy sequencing), structurizer (new implementation from the published algorithm; rejects irreducible CFGs with a diagnostic), structured IR (`scf`-shaped: `SDecl/SAssign/SIf/SLoop/SReturn/SExpr`).
  - `tc-codegen`:
    - common C-core printer
    - `StructuredEmitter` (path A)
    - `DirectEmitter` (path B, SSA → text with its own inline phi-copy + scope handling, for the reducible subset)
    - dialect layers `cuda` / `opencl` / `metal`
  - `tc-runtime`: `TcCompiler.compile(Method, TargetCaps) → {source, entry, Access[], launch info, diagnostics}`, keyed cache (bytecode hash + compiler version + options + target caps).
  - `tc-debug`: IR parser/printer, HTML report, `.dot` output, remarks, and the `tc` CLI (see Debug infrastructure).
  - `tc-tests`: golden IR/source tests (FileCheck-style), the differential harness, and the JMH benchmarks.
- Runtime integration: a small `if (TornadoOptions.COMPILER == TC)` branch in `CUDATornadoDevice.compileTask`, later in the OCL/Metal devices too.
  - It calls `TcCompiler` and installs through the prebuilt-source `installCode`.
  - It also skips sketch building in the task-graph path when TC is selected.
  - On a TC bailout it falls back to Graal when `-Dtornado.compiler.fallback=true` (the default while parity is incomplete).
- Docs in `docs/compiler-replacement/`: the files from the brief plus `llvm-concept-mapping.md`.

## Clean-room / IP policy (no copyright issues)
- Graal is GPLv2+CPE/UPL, OpenJDK Babylon/HAT is GPLv2+CPE, LLVM is Apache-2.0 with LLVM exception, and ILGPU/Simple have their own licenses. **No code, comments, class structure or naming is copied from any of them.** They are used only as published ideas and concepts.
- TC components are implemented from published algorithms and cited in `docs/compiler-replacement/provenance.md`. Examples: Braun et al. 2013 SSA construction; Cooper–Harvey–Kennedy dominators; Boissinot/Sreedhar SSA destruction; Ramsey 2022 "Beyond Relooper" structuring; Click 1995 GVN/Sea of Nodes as concept only.
- Our own novel design decisions are recorded there too.
- TornadoVM-authored code (same project and license) may be used as behavioural reference: the kernel ABI, intrinsic names, and the structurizer test cases. The structurizer itself is rewritten from the paper, not ported line by line.
- The person writing a TC component does not have the corresponding Graal class open. Graal is consulted only via the black-box differential harness.
- Automated evidence:
  - an enforcer/ArchUnit rule: no `tornado.graal`/`jdk.vm.ci`/`org.graalvm` imports in `tornado-compiler/**`
  - an SPDX header check on every TC file
  - a JPlag similarity scan of `tornado-compiler/**` against the Graal 23.1.0 sources jar and HAT `core/`, at C4, C6 and C8; any hit above threshold is reviewed and rewritten
  - the dependency license report (`mvn license:aggregate-third-party-report`) must show no new third-party deps in TC.

## Modern Java
- Language floor is the TornadoVM JDK 21 build:
  - `var` everywhere the type is obvious
  - records for IR values, ops and diagnostics
  - sealed interfaces for IR instruction, type and structured-IR hierarchies
  - pattern-matching `switch` and record patterns in passes, printers and emitters (exhaustive, no visitors unless profiling says otherwise)
  - text blocks for emitter templates
  - `SequencedCollection`, `Optional`, streams only off hot paths.
- JDK 22+ features: `_` unnamed variables, `java.lang.classfile`, stream gatherers, `ScopedValue` for compile-context instead of ThreadLocal. They are used in the `jdk22plus` build or behind the multi-release adapter (`META-INF/versions/25`), so JDK 21 still works.
- Immutable IR snapshots for dumps; mutable builders inside passes. `null` is not allowed in IR APIs.

## Debug infrastructure (human-readable, first-class)
- **Textual IR, LLVM-.ll-like but our own syntax, with printer *and* parser** (round-trip tested):
  ```
  kernel @VectorAdd.add(f32[] %a, f32[] %b, f32[] %c) {
  b0:                                   ; VectorAdd.java:21
    %i     = tornado.globalId.x : i32
    %n     = array.length %c : i32
    br.lt  %i, %n -> b1, b2
  b1:
    %x = array.load %a[%i] : f32         ; bci 14
    ...
  ```
  Value names come from the local-variable table, which makes hand-written IR tests possible.
- The new SSA lowering is also written fresh. The structurizer is new, not ported: Ramsey-style dominator-tree structuring, validated against the in-tree regression cases.
- **Structured-IR pretty printer** (indented pseudo-C before the dialect is applied) and **emitted source annotated** with `// Foo.java:12` comments. Optional `#line` directives let cuda-gdb/`tcd` step through Java lines.
- **Dump controls:**
  - `-Dtornado.tc.dump=all|<pass>|before:<p>|after:<p>`, `-Dtornado.tc.dumpDir=…`, `-Dtornado.tc.only=<Class.method>`
  - `-Dtornado.tc.passLimit=N`, a bisect knob like LLVM's opt-bisect-limit, to find the first bad pass
  - `-Dtornado.tc.verify=each` (on in tests).
- **Per-compilation HTML report** (self-contained, offline). Side by side: bytecode → IR after each pass (with diff highlighting) → structured IR → emitted source, with click-through line mapping, a CFG/dominator SVG (Graphviz `.dot` also emitted) and a pass-timing table.
- **Diagnostics:**
  - stable codes (`TC-E1xxx` frontend, `E2xxx` IR/verifier, `E3xxx` Tornado lowering, `E4xxx` target, `E9xxx` internal)
  - message with Java source line + bci + a caret excerpt, plus a "why / what to do" hint
  - verifier failures print the offending instruction in context.
- **Optimisation remarks** (`-Dtornado.tc.remarks=inline,loop,reduce`) as readable text plus JSON lines.
- **`tc` CLI** (`tc explain|dump|emit|diff <class> <method> --target cuda`): runs the compiler headless with no GPU, which suits jshell/debugging and CI. `tc diff` runs Graal vs TC on one task and prints the emitted kernels plus a behavioural diff.
- **Test utilities:** a FileCheck-style matcher in Java (`// CHECK:` lines in golden IR/source tests) and a small IR fuzzer that feeds the verifier and the structurizer.

## Profiling and decision checks
- **JFR custom events** in `tc-runtime`: `TcCompilation`, `TcPass`, `TcFrontend`, `TcEmit`, `TcCacheHit` (duration, method, pass, IR size before/after). They are always compiled in, enabled with `-XX:StartFlightRecording`.
- Profile at every checkpoint from C3 on with JFR (+ `jfr print --events Tc*` / JMC), async-profiler CPU and alloc flame graphs of the compiler, and JMH microbenchmarks per stage (`tc-tests/jmh`). Nsight Systems/Compute (`nsys`, `ncu`) cover the generated kernels vs Graal kernels.
- Each gate gets a short **decision review** in `checkpoints.md`: what profiling showed, and whether it confirms or overturns choices such as IR data structures, sealed+switch dispatch vs visitors, cache key cost and emission path. If a change is overturned, it is flagged to the user before continuing.

## LLVM IR / MLIR interoperability (amendment 2026-10-09)
Interop is in scope, but as text in public formats, never as a core dependency. **No JNI and no FFM bindings to LLVM/MLIR/NVVM libraries, anywhere in TC** (user decision 2026-10-09). LLVM/MLIR tools are only ever run as external processes on files, and only when present. The TC core stays pure Java, so it works on JDK 21 and with Leyden. Structured C-like source remains the primary output.

**Phase 3 design choices (cheap now, expensive to retrofit):**
- **SSA IR uses block arguments, not phi nodes** (MLIR style). This maps 1:1 to MLIR `cf`, maps trivially to LLVM phis, and makes SSA destruction a matter of edge copies.
- **Structured IR shaped like MLIR `scf`**: `if`/`for`/`while` with explicit yielded values. Lowering to `scf` + `arith` + `memref` + `gpu` is then mechanical.
- **Types and address spaces match the LLVM/NVPTX conventions**: `i1..i64`, `f16`/`bf16`/`f32`/`f64`, `vector<N x T>`, `ptr addrspace(n)` with NVPTX numbering (1 global, 3 shared, 4 constant, 5 local). Each other dialect gets a mapping table.
- **Op/intrinsic registry** has an LLVM/NVVM intrinsic column and an MLIR op column next to the CUDA/OpenCL/MSL spellings.
- Clean-room still applies: the LLVM LangRef and MLIR dialect docs are public specifications. Writing text in those formats is fine; copying code is not.

**Module `tc-interop` (optional, off by default):**
- `.ll` printer (LLVM IR text) and `.mlir` printer (`func`/`cf` or `scf`/`arith`/`memref`/`gpu`), reachable via `tc emit --format=ll|mlir` and `-Dtornado.tc.dump.format=...`.
- No importer at first. Kernels come from bytecode; an LLVM/MLIR → TC importer is a Phase 9+ item only if a use case appears.

**Uses, in order of value:**
1. **Second, independent verifier (from C4b).** In CI, feed every dumped kernel to `opt -passes=verify` / `mlir-opt --verify-each` when the tools are present.
2. **Translation validation of TC passes (from C4b).** Use Alive2 (`alive-tv`) on before/after `.ll` for scalar arithmetic kernels. This catches canonicaliser and folding bugs (the issue #1149 class: `>>>`, NaN min/max, `(long)float`).
3. **Optimisation oracle (Phase 7).** Compare TC-optimised vs `opt -O3`-optimised IR on hot kernels to find missing optimisations, measured with ncu.
4. **Experimental backends (after C6 parity, Phase 9):**
   - LLVM `llc -mtriple=nvptx64` as an *offline, optional* PTX path (external process), measured against NVRTC. In-process libNVVM is out of scope because it would need an FFM/JNI binding.
   - SPIR-V via `llvm-spirv`, which brings back the dead SPIR-V/Intel Level Zero target.
   - AMDGPU via `llc`.
   - CUDA Tile IR (MLIR-based) for the `TileContext` path instead of the Tile C++ text; this needs checking against the installed CUDA version.
   - None of these replace the structured-source path.

**Local tooling (measured 2026-10-09):**
- `libnvvm` is present (CUDA 12.6).
- `opt`, `llc`, `mlir-opt`, `llvm-spirv` and `alive-tv` are not installed (`/usr/lib/llvm-14` has libraries only).
- Interop tests skip cleanly when the tools are absent. Installing LLVM ≥ 19 + MLIR + Alive2 is a prerequisite for uses 1–3 and needs the user's OK (system packages).

**Gate additions:**
- C3: the IR spec documents the block-argument / scf / address-space choices, and one hand-built kernel round-trips through `.ll` and `.mlir` text, verified by hand once.
- C4b: `opt`/`mlir-opt` verify every golden kernel, and an Alive2 run covers the canonicaliser rules.
- C9: optional `llc` PTX path compile-latency numbers vs NVRTC (offline only).

## Phases and design checkpoints
Each checkpoint is a go/no-go gate with a concrete test. It must pass, with results recorded in `docs/compiler-replacement/checkpoints.md`, before the next phase starts. Design-affecting decisions are brought back for review at the gates marked ⚑.

**Phase 0: Repo and inventory**
- Create the private repo and push.
- Build `make BACKEND=opencl,cuda` on JDK 21 and on `jdk22plus`/JDK 25.
- Write `repository-inventory.md` and `existing-work.md` from the findings above.
- **C0:** the repo exists and is private (`gh repo view --json visibility`); both JDK builds succeed; `tornado --devices` lists the 4090 under CUDA and OpenCL.

**Phase 1: Baseline**
- On CUDA, OpenCL-4090 and OpenCL-UHD770: run `make tests` and `make fast-tests`, then `tornado-benchmarks.py --iterations 10 --validate` with `--enableProfiler silent --dumpProfiler`, which splits Graal compile, driver compile and kernel time.
- jitLLM: `~/work/jitllm-jev`, `./jitllm bench -m ~/models/Llama-3.2-1B-Instruct-Q8_0.gguf --gpu --pp 128,512 --tg 64 --repetitions 5 --output json`, plus Qwen3-0.6B F16.
- Record pre-existing failures separately. Deliverables: `baseline-{tests,features,performance}.md` + JSON.
- **C1:** two consecutive full runs give the same pass/fail set; benchmark coefficient of variation (CV) is recorded per workload, and these set the later regression thresholds.

**Phase 2: Dependency and pipeline analysis + LLVM study**
- Static map of all `tornado.graal` / `jdk.vm.ci` users, per module.
- Dynamic trace: phases run and node types seen across the full test suite, via a temporary counting hook on `TornadoHighTier/Mid/Low` + `CUDAHighTier…`, on a scratch branch.
- Classify each dependency MUST REPLACE / CAN SIMPLIFY / CAN REMOVE.
- Write `phase-migration-matrix.md` (one row per Tornado phase: ~20 runtime + ~21 CUDA phases, 122 CUDA nodes) and `llvm-concept-mapping.md`.
- **C2 ⚑:** every phase and node class that executed in the test run has a row with an owner, replacement and test. Review with the user before Phase 3 is frozen.

**Phase 3: Architecture contracts**
- Write `architecture.md`, `ir-specification.md`, `backend-contracts.md`, `compatibility-contract.md` (kernel ABI as captured by `--printKernel` for each test family, the diagnostics taxonomy, and numeric semantics).
- **C3 ⚑ design test:** before building the full core, hand-build an IR for 3 kernels (vector add, `@Parallel` loop with an if/else, KernelContext reduction with local memory), lower them through stub structurizer and emitter, and run them on the 4090 through the prebuilt install path. This proves the IR shape, the ABI contract and the runtime hook end to end with minimal code. The IR printer/parser, `-Dtornado.tc.dump` and the JFR events are built here first, so every later phase is debuggable from day one. Review with the user, including a sample HTML report.

**Phase 4: Core (frontend, IR, passes, Tornado transforms)**
- Order: frontend (bytecode → CFG with operand-stack typing → SSA using the Braun et al. on-the-fly construction) → verifier → pass manager → canonicalise/fold/DCE/GVN → loops → inlining → `tc-tornado` lowering.
- **C4a:** frontend round-trips every method reachable from `tornado-unittests` kernels into verified IR (a headless test, no GPU, enumerating the task methods). Report the unsupported bytecodes.
- **C4b:** a golden IR-dump test for each pass, and the verifier runs after every pass in test mode.
- **C4c:** `Access[]` from TC equals the Sketch's `getArgumentsAccess()` for every unittest task (a differential check, no GPU).

**Phase 5: Structured lowering and emitters (CUDA first)**
- SSA destruction, ported structurizer, structured IR, CUDA dialect, then the direct emitter for reducible kernels.
- **C5a:** the structurizer passes the existing switch and short-circuit regression cases (#1054/#1043/#1138) and every CFG shape found in C4a. Irreducible CFGs give a deterministic diagnostic.
- **C5b ⚑:** a vertical slice. `tornado-test` foundation, arrays, loops, logic, numpromotion and math pass on CUDA with `-Dtornado.compiler=tc -Dtornado.compiler.fallback=false`. Both paths A and B pass where B applies. Review the emitted-source quality with the user.

**Phase 6: Differential parity (central phase)**
- Differential harness: run each unittest under `graal` and `tc` and compare pass/fail, outputs, bailout class and launch config. Generated text is not compared.
- Extend the numeric semantics suite with the issue #1149 list.
- Expand family by family: kernelcontext → reductions → vectortypes → atomics → fields/instances → lambdas → tile (CUDA Tile mode last) → api/runtime families.
- Then port the OpenCL and Metal dialect layers. OpenCL is validated on both the 4090 and the UHD 770; Metal is build- and golden-tested here because there is no Mac device locally (gap recorded).
- **C6 per family:** TC ≥ Graal pass set with fallback off. Every difference is listed in `parity-matrix.md`.
- **C6-final:** full suite parity on CUDA and OpenCL; jitLLM bench passes with identical token output under TC.

**Phase 7: Performance and stability**
- **C7:** compile latency (TC vs Graal) and kernel time are within thresholds derived from the C1 variability (approved 2026-10-09: TC kernel time regression ≤ max(2·CV, 3%) per workload, CV from C1). There must be no nondeterminism over 20 repeated full-suite runs, and the cache invalidation tests must pass.

**Phase 8: Graal removal**
- Make TC the default and keep Graal selectable for one cycle.
- Then delete the `graal/` packages and `bin/build_graal_module.py` (`minimize_graal_jar.py` and its keep-list were already removed in Phase 2), and the Graal pom dependencies, the export lines and the `tornado.py` flags.
- JVMCI removal is assessed separately (it builds on PR #894/#1192).
- **C8:** a clean build from an empty `~/.m2` cache has no `tornado.graal` artifact (`mvn dependency:tree`, `jdeps`); the full suite and jitLLM pass on JDK 21 and JDK 25+ with `--release` raised above 22.

**Phase 9: Future-JDK / Leyden (docs + prototype only if justified)**
- Strategy docs; `java.lang.classfile` adapter tests on JDK 27-ea; a cache-key design that reuses the `tc-runtime` cache.

**Phase 10: Babylon (last)**
- `tc-frontend-babylon` adapter (code model → `tc-ir`) built against `~/src/babylon` JDK. The core and backends are unchanged.
- **C10:** a subset of HAT-style kernels runs through the TC core on CUDA with no Babylon imports outside the adapter.

## Working rules
- Small commits on `compiler/main`, pushed to the private repo only.
- Commits are as the user with no Co-Authored-By/Claude footers, and dated in the weekday 19:30–03:45 window (from memory).
- No changes to beehive-lab and no PRs there unless asked.
- Each task reports changed files, commands run, results, gaps and the next task.
- Graal stays the reference and fallback until C8.

## Verification summary
- Headless (CI-able, no GPU): `mvn -pl tornado-compiler/... test`, which covers golden IR/source, verifier-after-every-pass, frontend round-trip (C4a) and `Access[]` diff (C4c).
- GPU: `tornado-test --verbose -J"-Dtornado.compiler=tc -Dtornado.compiler.fallback=false"` per family, plus the differential harness script `tc-tests/bin/tc-diff`.
- Performance: the same `tornado-benchmarks.py` + jitLLM commands as Phase 1, compared against the stored C1 JSON.
