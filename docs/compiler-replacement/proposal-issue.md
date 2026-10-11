# [Proposal] TC: a Graal-free kernel compiler for TornadoVM

**Type:** design proposal · **Scope:** compiler (frontend → IR → passes → CUDA / OpenCL / Metal source) · **Runtime and API:** unchanged

## 1. Summary

TornadoVM compiles Java kernels through a vendored, relocated Graal 23.1 compiler that runs on
JVMCI. This proposal replaces it with **TC**, a small compiler owned by the project:

```
class file ──► frontend ──► TC IR (SSA) ──► passes ──► Tornado lowering ──► structuring ──► C core + dialect ──► CUDA C++ / OpenCL C / MSL
 (bytecode)    (own reader)  (block args)   (canon, GVN,  (@Parallel, ABI,     (if/while/      (one printer,
                                             LICM, DCE,   reductions, atomics) break)          thin dialects)
                                             unroll)
```

The kernel ABI, the runtime, the drivers' install paths and the public API stay as they are.
`-Dtornado.compiler=graal|tc` selects the compiler; Graal remains the fallback until removal.

## 2. Motivation

- **Bytecode ceiling.** The frozen Graal class reader cannot read newer class files, so builds pin
  `--release 22`. TornadoVM cannot follow the JDK.
- **JVMCI dependency.** Graal needs JVMCI and `jdk.internal.vm.ci` exports. That makes JDK
  upgrades, Leyden AOT caches and GraalVM native image awkward or impossible.
- **Size and ownership.**
  - 644 TornadoVM files import `tornado.graal.*`.
  - Three backends each maintain about 160–220 Graal-based classes (nodes, LIR, phases).
  - Graal's sea-of-nodes pipeline was designed for CPU JIT compilation, not for emitting source kernels.
- **Debuggability.** There is no textual IR you can read, diff or write a test against.

## 3. Requirements

| # | Requirement | Acceptance |
|---|---|---|
| R1 | **Functional parity** with the Graal path on every backend | `tornado-test` with fallback off: TC passes every test Graal passes on CUDA, OpenCL and Metal. Each remaining difference is listed with its reason |
| R2 | **JDK 21 to 27** | Builds and passes on JDK 21 (floor), 25 and 27-ea:<br>• class files are read by TC's own parser on 21, and through `java.lang.classfile` on 24+ (multi-release jar);<br>• no `--release` cap;<br>• no JDK-internal APIs |
| R3 | **Project Leyden** compatible | Works with `-XX:AOTCacheOutput` / `-XX:AOTCache` (JDK 25+):<br>• no runtime bytecode generation or class definition inside the compiler;<br>• deterministic class loading;<br>• AppCDS-friendly static initialisers |
| R4 | **GraalVM native image** compatible | The compiler builds as a native image and emits byte-identical kernels:<br>• no JVMCI;<br>• no reflection outside registered metadata;<br>• no JNI/FFM bindings to LLVM/MLIR/NVVM;<br>• no dynamic proxies |
| R5 | **No JVMCI, no new third-party dependencies** | Pure Java 21+. An enforcer rule forbids `jdk.vm.ci`, `org.graalvm` and `tornado.graal` imports in the compiler modules |
| R6 | **Performance** | Kernel time at or better than Graal's, within run-to-run noise. Compile latency no worse than Graal's when warm; cold compile improved by Leyden or native image (R3/R4) |
| R7 | **Same ABI and runtime contract** | Unchanged:<br>• kernel signature and argument binding;<br>• `Access[]` per parameter;<br>• launch domain;<br>• atomics region;<br>• profiler timings (`TASK_COMPILE_TC_TIME`) |
| R8 | **Debuggable by design** | Textual IR with a printer and a parser (round-trip tested); per-stage dumps; stable diagnostic codes (`TC-E1xxx`…`E9xxx`) with Java line and bci; JFR events; FileCheck-style golden tests |
| R9 | **Extensible backends** | A new target is a dialect (types, address spaces, intrinsic spellings, preamble) plus target capabilities; the C core, passes and lowering are shared |
| R10 | **Clean-room** | No code is copied from Graal, LLVM or other compilers. Algorithms are implemented from their publications and recorded in a provenance document |

## 4. Design

**Frontend.**
- Reads class files directly.
- Builds SSA on the fly (Braun et al. 2013) from the operand stack and locals. Value names come from
  the local variable table, and every instruction keeps its Java line and bci.
- Resolves TornadoVM API calls through a binding table:
  - intrinsics such as `KernelContext.globalIdx`, `Math.*` and vector types;
  - inlined Java bodies for everything else.

**IR.** Typed SSA with block arguments instead of phis, in the MLIR style:
- integer and float types from `i1`…`i64` and `f16/bf16/f32/f64`, plus vectors;
- pointers with NVPTX-numbered address spaces;
- explicit Java semantics: masked shifts, saturating float→int, NaN-aware min/max;
- a registry of Tornado intrinsics with purity and per-dialect spellings;
- a verifier that runs after every pass in tests.

**Passes.** Canonicalise and constant-fold, CFG simplify, DCE, small-loop full unroll, GVN, LICM and
math peepholes. A pass manager records which passes changed the IR and bisects with
`tornado.tc.passLimit`.

**Tornado lowering.**
- Inlining and specialisation on argument facts (array lengths, scalars, object layouts).
- `@Parallel` loops become grid-stride loops; `@Reduce` becomes work-group reductions.
- Atomics, batches, local arrays.
- ABI lowering: arrays and fields become raw memory, using the same header and field layouts as the
  runtime's buffers.

**Structuring.** SSA destruction, then dominator-based structuring (Ramsey 2022) back into
`if`/`while`/`break`/`continue`. A `goto` remains only for jumps C cannot express. Irreducible CFGs
get a deterministic diagnostic.

**Emission.** One C-family core (statements, precedence, element access, Java integer semantics) and
thin dialects for CUDA (NVRTC), OpenCL C 1.2 and MSL. Generated code keeps Java names and `// File.java:N`
comments.

**Runtime integration.** A small hook in each `XXXTornadoDevice.compileTask` installs TC's source
through the existing prebuilt-source path. On a TC diagnostic it falls back to Graal unless
`-Dtornado.compiler.fallback=false`.

**Tooling.** IR dumps per stage and pass, an HTML report, a headless `tc` CLI, lit tests, a
whole-suite emit sweep, and an interactive visualiser (`tornado --tcirviz`). The visualiser follows a
value from Java → bytecode → every pass → target code.

## 5. Status of the prototype

| Backend | Tests passing, TC vs Graal (fallback off) | Notes |
|---|---|---|
| CUDA (RTX 4090) | **1522 / 1522** (TestMemoryLeak included); 1534 vs 1516 without it | Every Graal-passing test passes. `Integer.reverse` is compiled where the test expects a refusal |
| OpenCL (RTX 4090) | **1053** vs 1027 | Every Graal-passing test passes, except the same `Integer.reverse` test |
| OpenCL (Intel UHD 770, no fp64) | **930** vs 932 | The same exception, plus one racy test (it reads other work-groups' results without synchronisation) |
| Metal | Dialect, ABI and runtime hook done; all 770 unit-test kernels type-check offline | Validation on Apple silicon pending |

**Applications.** jitLLM (Llama-3.2-1B Q8, Qwen3-0.6B F16): all kernels compile under TC, greedy
output is token-identical to Graal, and throughput is 1.06–1.14× (CUDA) and 1.04–1.09× (OpenCL) of
Graal's.

**Cold compile, one kernel (`blackscholes`, JVM start included):**

| Variant | Time |
|---|---:|
| JDK 21 | 231 ms |
| JDK 25 + Leyden AOT cache | 125 ms |
| GraalVM CE 25 native image | 26 ms |

The native image emits byte-identical CUDA.

## 6. Plan

1. **Opt-in** (`-Dtornado.compiler=tc`), Graal default, per-backend parity tracked by a differential
   harness that compares pass/fail and launch configuration per test.
2. **TC default** with Graal fallback for one release; Graal selectable.
3. **Remove Graal**:
   - delete the `graal/` packages of the three drivers, the relocated Graal module and its build scripts;
   - lift the `--release` cap.

   Remove JVMCI separately, after the runtime's remaining JVMCI uses (method resolution, sketches)
   move to the class-file model.
4. **Later**: a Babylon code-model frontend feeding the same IR.

## 7. Risks and open questions

- **Long tail of Graal-specific behaviour**, such as escape analysis of local objects and exception
  paths. Mitigation: the differential harness, and refusing with a diagnostic rather than miscompiling.
- **Device-specific semantics.** Fast-math defaults (Metal), missing fp64 or fp16, and vectors wider
  than 4 lanes on Metal. These are handled through target capabilities and legality checks.
- **Compile latency on the first kernel** on a plain JVM, about 140 ms cold vs a few ms warm.
  Mitigated by Leyden (R3) or a native-image compiler service (R4).
- **Review load**: a new compiler in-tree. The modules are self-contained under `tornado-compiler/`,
  with golden tests per pass.

Feedback welcome on:
- the requirements (especially R2–R4);
- the plan for retiring Graal and JVMCI;
- whether the Metal validation should gate step 2.
