# TC architecture — DRAFT for review at gates C2/C3

Companion documents: [ir-specification.md](ir-specification.md),
[backend-contracts.md](backend-contracts.md), [compatibility-contract.md](compatibility-contract.md).

## 1. Pipeline

```
                ┌──────────────┐   TC-SSA    ┌────────────┐   TC-SSA    ┌──────────────┐
class bytes ───▶│ tc-frontend  │────────────▶│   tc-opt   │────────────▶│  tc-tornado  │
 (BytecodeSource)│ bytecode→SSA │  verified   │ canon, DCE │             │ API lowering │
                │ intrinsic map│             │ GVN, inline│             │ parallel/red │
                └──────────────┘             │ loops      │             │ ABI lowering │
                                             └────────────┘             └──────┬───────┘
                                                                               │ TC-SSA (post-ABI)
                      ┌─────────────────────────────── path B (direct) ────────┤
                      ▼                                                        ▼
               ┌──────────────┐  source   ┌──────────────┐   TC-S     ┌──────────────┐
 driver ◀──────│  tc-runtime  │◀──────────│  tc-codegen  │◀───────────│   tc-lower   │
 installCode   │ cache, JFR,  │           │ C core +     │  path A    │ out-of-SSA + │
 (NVRTC/OCL/MTL)│ diagnostics │           │ dialects     │            │ structuring  │
               └──────────────┘           └──────────────┘            └──────────────┘
```

Every arrow carries a verifiable artefact with a text form, so any stage can be dumped, diffed,
hand-edited and re-fed (`tc` CLI) without a GPU.

## 2. Modules and dependency rules

| Module | Depends on | Must not depend on |
|---|---|---|
| `tc-ir` | JDK only | everything else |
| `tc-frontend` | `tc-ir` | Graal, JVMCI, ASM, `tornado-runtime`, drivers |
| `tc-opt` | `tc-ir` | frontend, tornado, codegen |
| `tc-tornado` | `tc-ir`, `tc-opt` (analyses), `tornado-api` (annotations, enums only) | runtime, drivers |
| `tc-lower` | `tc-ir` | codegen, tornado |
| `tc-codegen` | `tc-ir`, `tc-lower` | runtime, drivers |
| `tc-interop` (optional) | `tc-ir`, `tc-lower` | any LLVM/MLIR library (text only, external tools) |
| `tc-runtime` | all TC modules, `tornado-api` | Graal, JVMCI |
| `tc-debug` | all TC modules | Graal, JVMCI |
| `tornado-runtime` / drivers (hook) | `tc-runtime` | — |

Enforced by a test in `tc-tests` that scans imports (no `tornado.graal`, `jdk.vm.ci`,
`org.graalvm`, `org.objectweb.asm`) and module edges. JDK 21 is the language floor; JDK 25+
features are isolated behind multi-release classes.

## 3. Runtime integration

- **Selection:** `-Dtornado.compiler=graal|tc` (default `graal` until C8),
  `-Dtornado.compiler.fallback=true|false` (default `true` until C6). On a TC diagnostic with
  fallback on, the Graal path compiles the task and the diagnostic is logged once.
- **Hook point:** `CUDATornadoDevice.compileTask` (later OpenCL/Metal devices). TC produces
  `{source, entryName, Access[], launch info, atomics map, diagnostics}` and installs through the
  existing source-install entry used by prebuilt tasks.
- **Sketch phase:** with `tc` selected, `TornadoTaskGraph.addInner` must not build a Graal sketch;
  `Access[]` comes from TC's analysis (gate C4c). `@Reduce` task-graph rewriting (today a second
  Graal graph in `CodeAnalysis`) gets a small TC bytecode analysis.
- **Per-compilation state:** no static mutable state (today: static atomics map, shared
  canonicaliser context, `static synchronized` compile). TC compilations are independent and may
  run in parallel; caches are concurrent maps keyed by immutable records.
- **Cache key:** class-bytes hash of every method in the call tree + TC version + options +
  `TargetCaps` + specialisation values (when value specialisation applies).

## 4. Compilation flow inside TC

1. **Frontend:** resolve the task method; read class bytes (`BytecodeSource`); build CFG; abstract
   interpretation of the operand stack/locals → TC-SSA (Braun et al.); map calls through the
   intrinsic-binding registry (`KernelContext`, `TornadoMath`, vector types, atomics, `Math`);
   read `@Parallel`/`@Reduce` type annotations from the class file; attach `loc`.
2. **Generic optimisation:** inline-all → canonicalise → GVN → DCE → loop info → full unroll
   (policy parity with TornadoVM) → canonicalise.
3. **Tornado lowering:** parallel loops → thread-id arithmetic (`TargetCaps` schedule);
   reductions → templates; local/private memory; value specialisation; `Access[]`;
   half/bf16; atomics slots; then ABI lowering (`array<T>` → `ptr<global>` + header offset,
   parameter objects flattened). Verifier rule 5 must hold afterwards.
4. **Target legality:** capability checks (fp64, fp16, MMA, tile, CDP) → `TC-E4xxx`.
5. **Lowering:** path A: out-of-SSA (edge copies, parallel-copy sequencing) → structuring
   (Ramsey 2022) → TC-S with expression folding. Path B: direct emission for reducible kernels.
6. **Emission:** C core + dialect (`cuda`, `opencl`, `metal`), registry-driven intrinsics.
7. **Install:** driver compile (NVRTC / OpenCL / Metal) through existing driver code.

## 5. Debuggability (summary; details in plan.md)

Textual IR with parser; `-Dtornado.tc.dump`, `-Dtornado.tc.verify=each`,
`-Dtornado.tc.passLimit=N`, `-Dtornado.tc.only=<method>`; per-compilation HTML report;
optimisation remarks; stable `TC-E` diagnostics with Java source lines; `tc` CLI
(`explain|dump|emit|diff`); JFR events (`TcCompilation`, `TcPass`, `TcFrontend`, `TcEmit`,
`TcCacheHit`).

## 6. Decisions to confirm at C3 (⚑)

1. Block-argument SSA + `scf`-shaped TC-S (see IR spec).
2. `array<T>` kept until `tc-tornado` ABI lowering.
3. Always-inline for parity; device functions only after C6.
4. Path B limited to reducible kernels and reusing TC-S expression folding.
5. Value specialisation reproduced as a `tc-tornado` pass driven by actual arguments, with the
   specialisation values in the cache key.
