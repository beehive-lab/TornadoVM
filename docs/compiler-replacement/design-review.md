# TC design review (2026-10-10, before the OpenCL and Metal dialects)

A campaign to check the compiler infrastructure against a clean compiler design before a second and
third backend are added: boilerplate, templated or stringly-typed patterns, hidden coupling,
duplicated logic, missing invariants and latent miscompiles. Four read-only reviews (IR and
optimiser; frontend and runtime hook; TornadoVM passes and pipeline driver; structuring and code
generation) read every file of `tornado-compiler/*/src/main` (about 16 000 lines) against one rubric.
Findings are ranked:

- **P0**: risks a miscompile, or blocks a clean OpenCL/Metal port.
- **P1**: design debt worth fixing soon.
- **P2**: cosmetic.

Findings marked *suspected* were found by reading only. Each one gets a reproducing test before it is
fixed; one that does not reproduce is recorded here as such.

## The root cause behind most findings

**Intrinsics are bare name strings, and nothing in `tc-ir` describes them.** Meaning is spread
over name prefixes and parsed back out of names:

- data packed into names: `tornado.vector.lane.3`, `tornado.device_launch.<mode>.<Owner>.<method>`,
  `tile.view.F32`, `tornado.local_array.<space>.<elem>`, `tornado.async_copy.4.e2`;
- purity decided by prefix (`Opcode.isPure`);
- no verifier rule for any intrinsic;
- codegen keeping the only table, CUDA spellings only.

Every new dialect would have to re-derive the semantics. Attributes are free-form strings in the same
way: `parallel(iv=k)`, `domain(...)`, `atomics(init=..)`, and the printf format as C-escaped text.

## Findings

IDs: FE frontend and hook; IR tc-ir and tc-opt; TP tc-tornado and tc-runtime; CG tc-lower and tc-codegen.

### P0: correctness (suspected until reproduced)

| ID | Where | Finding |
|---|---|---|
| CG-1 | `Intrinsics.expand`, `CEmitter` 735 | Operands are pasted into spelling templates without parentheses, and the template's precedence is guessed from its text. `(long long) $3 * 2` with `$3 = i + 1` prints `(long long) i + 1 * 2`; `atomicSub(..., $1) - $1` evaluates `$1` twice and binds as an atom. |
| CG-2 | `CEmitter` 536 | `"-" + operand` prints `--x` (a decrement) for a negated negative operand. |
| CG-3 | `Structurizer` 374-389 with 540-587 | Pure address arithmetic is inlined at uses in other blocks, while copy coalescing only checks readers in the defining block. A coalesced block argument can change the value seen by an inlined use elsewhere. |
| CG-4 | `TileSpellings` 434-443 | A non-constant `full` value or reduction axis becomes 0, and a missing table entry prints `null`, instead of an error. |
| CG-10 | `CEmitter` 869 | `i8` is `char`, whose signedness is implementation-defined; Java `byte` needs `signed char`. |
| TP-1 | `TcCompiler` 139-167, `AtomicSlots` | Device-launched children number their atomic slots from 0 too: they overlap the parent's, and their initial values are not registered. |
| TP-2 | `ReductionLowering` 192-217 | `out[k] += v` is matched without checking `k == 0`; the partial result always goes to `out[group + 1]`. |
| TP-3 | `BatchOffset` 57, 87-94 | An index expression that is also used as a value gets the batch offset in its array index as well; the depth cut-off fails in the unsafe direction. |
| TP-4 | `ParallelLoopLowering` 81-90 | Sibling top-level `@Parallel` loops get grid dimensions x and y and the domain becomes their product, so each body runs once per thread of the other. |
| TP-5 | `ValueSpecialisation` 110-121 | Lengths are folded only for `array.length` directly on a parameter. A length reached through a block argument, or any length in a device-launched child (no facts), reads a possibly uninitialised header. |
| TP-14 | `ValueSpecialisation` 150 | Scalar constants are inserted before the entry terminator, after earlier uses in the entry block. |
| IR-1 | `Opcode.isPure` 318 | Purity by name prefix: `tornado.vector.store_param` (a memory write) reports pure, and `Structurizer.loadCanMove` trusts `isPure`. |
| IR-3 | `Canonicalizer` 129-192 | Lane numbers are parsed from names, unchecked against the vector length. |
| FE-4 | `Translator` 196, 675 | Vector setters write back to the caller only for kernel parameters. A device helper `void f(Float4 v) { v.setX(1); }` loses the write. |
| FE-5 | `Translator.rebind` 921 | Walks every reference slot with `readVariable`, which can raise a spurious TC-E1006 or create block arguments for unrelated slots. |
| IR-5 | `GlobalValueNumbering` 95 | The value-number key ignores attributes, and the fallback ordinal comes from `identityHashCode`. |

### P0: blocks a clean OpenCL/Metal port

| ID | Where | Finding |
|---|---|---|
| FE-2, IR-2, TP-9, CG-8 | Translator, Verifier, Canonicalizer, AbiLowering, TcCompiler, CEmitter | No intrinsic registry; data in names, parsed in several places; `when name.startsWith(...)` statement hooks in the emitter. |
| CG-5, CG-6, CG-7, CG-17 | CEmitter | The kernel header, pointer address spaces, the shared-array declaration, float bit-casts and specials, f16/bf16 conversions, `__builtin_unreachable`, printf and the device-launch block are all spelled in the "core" C emitter. Includes are chosen by scanning the emitted text. |
| FE-3 | Translator 1045 | The printf format is C-escaped in the frontend. |
| TP-7 | TcCompiler, TargetCaps, CUDATornadoDevice 366 | No legality stage: fp64/fp16/warp size/work-group/local-memory capabilities are never checked, tile legality uses the CUDA compute capability, and the driver hard-codes its TargetCaps values. |
| TP-8 | ReductionLowering, Helpers | The reduction algorithm is a CUDA helper behind an opaque intrinsic. It should be IR (a shared alloca, a loop, barriers) sized from TargetCaps. |

### P1: design debt

| ID | Finding |
|---|---|
| IR-4 | Untyped string attributes, no verifier rules, copied blindly when blocks merge. |
| IR-6 | The post-ABI verifier skips block arguments; tile types and tokens are allowed anywhere; unchecked local-pointer array accesses. |
| IR-7, FE-13 | Error codes with two meanings (TC-E2108; E1003, E1010 and E1011 used for user errors and unsupported features); a stale code list in the Translator javadoc. |
| IR-8, FE-11, TP-6 | `ObjectType` keyed by simple class name (layouts and KernelContext identity can collide); free-form tags `"?"`, `"Object[]"`; tile types in core IR with string dtypes; some dumps do not parse back. |
| IR-9, TP-13 | Constant, predicate and counted-loop helpers duplicated in four or more places; the `IrBuilder(before inst)` boilerplate about 15 times. |
| IR-10 | Two hand-written cloners (Inliner, FullLoopUnroll); the Inliner iterates an IdentityHashMap, so its output order is not deterministic. |
| IR-11, TP-10, TP-11, TP-17 | No real pass pipeline. The TcCompiler stages are a hand-written sequence of `if (usesTiles)`, `if (atomics)` and similar, with undeclared ordering rules, and are not verified. Inliner and LocalCollections run outside the stages. There is no analysis manager. `verify=end` verifies nothing. |
| IR-12, IR-13 | Each pass has its own rewrite loop; LoopForest gives no preheader, exits or induction variable. |
| IR-14 | No `convergent` effect for barriers and warp operations. |
| TP-12 | Device-launch linking: an 8-argument overload with `rename`, a `Compiled` record exposing the mutable compiler, an implicit `_dp` naming contract, the first overload wins. |
| TP-15, TP-16 | Typing and analysis passes that also rewrite; i32 thread ids mixed with i64 induction variables; domains truncated to int. |
| FE-1, FE-6, FE-7, FE-8, FE-9, FE-10, FE-12, FE-14 | Translator side channels: placeholder values in identity maps instead of a typed abstract stack value. The Translator is a god class, with three ways of reading through block arguments. Locals are keyed and parsed by string. Bindings is 230 lines of `METHODS.put` with no signature check. Enum ordinals are hard-coded. Callee names come from a descriptor hash. TcHook parses options and owns layout logic. |
| CG-9, CG-11..CG-16, CG-18, CG-19 | Emitter decisions made by sniffing its own output text. Signed shift and overflow are undefined behaviour in C++. Float-to-f16 conversion rounds twice. Duplicated element-access recovery. The inline heuristic is a god method. Synthetic names are detected by suffix. Reduction helpers parse names. The emitter uses a `@@loop:` text sentinel, and declarations are re-parsed from type strings. |

P2 items (stale javadocs, dead code, magic numbers, CUDA wording in reports, tool defaults fixed to
sm_89, test gaps) are listed in the review transcripts. They are fixed opportunistically.

## What is kept

- Block-argument SSA built Braun-style, with sealed `Opcode`/`Type`/TC-S hierarchies and exhaustive
  switches.
- Use lists kept consistent by every edit API; verifier diagnostics with stable codes and context.
- A round-tripping printer and parser, lit/FileCheck tests, and `verify-each`, bisection and observers
  in the PassManager.
- Java-exact folding (−0.0, saturating conversions, masked shifts).
- Pluggable `BytecodeSource`, with reflection isolated in the host-facing classes.
- `TcHook`'s fallback/strict split.
- Ramsey structuring, precedence-aware printing, NaN-correct predicate complements.
- One `Naming` rule set per dialect.
- Deferred-lowering passes (local arrays, collections).

## Plan

User direction (2026-10-10): duplication and cleanliness are fixed **before** a new backend is added,
reusing what exists wherever possible. The spikes in Phase D come last, before the new backend.

**Phase A (now): correctness.** For each suspected P0 miscompile: a reproducing test, then the fix,
then the test kept. Covers CG-1/2/3/4/10, TP-1/2/3/4/5/14, IR-1/3/5 and FE-4/5.

**Phase B: the architecture the second dialect needs.**

1. An `IntrinsicDef` registry in `tc-ir`: id, typed immediates (lane, mode, element type, space,
   callee), operand and result signature, and effects (reads, writes, convergent, may trap). The
   verifier checks every intrinsic against it. Bindings and every dialect's spelling table key on
   it, and no code parses names any more.
2. Typed attributes (`Parallel(iv)`, `Domain(...)`, `Format(String)`, ...) with verifier rules and
   defined behaviour on merge and clone.
3. The `Dialect` split of the C emitter: `CCore` keeps statements, control flow, precedence and
   declarations. `CudaDialect` takes the kernel and function headers, address-space type names,
   shared declarations, float specials, conversions, unreachable, printf, device launch, preamble
   features, and tile mode as `CudaTileExtension`. Spellings become typed builders with declared
   precedence.
4. A declared pass pipeline built per target. Every stage is a pass run by the PassManager: guarded,
   verified, dumped. Its ordering rules are written down.
5. A legality stage driven by real `TargetCaps` values (queried from the device).
6. Reductions lowered in tc-tornado to IR (shared alloca, loop, barriers).

**Phase C: duplication and cleanliness (also before the new backend).**

- One shared set of IR utilities: constants and folding helpers, a `CountedLoop` analysis
  (preheader, exits, induction variable), one IR cloner used by the Inliner and the unroller, and
  `IrBuilder.before(inst)`.
- An analysis manager, so passes stop rebuilding dominators and loop forests.
- One element-access analysis shared by the structurizer and the emitters.
- One way to read through block arguments in the Translator, and a typed abstract stack value
  instead of placeholder instructions in side maps.
- A data-driven Bindings table, checked against the API signatures.
- Fully qualified class identity; one meaning per error code; dead code and stale comments removed.

**Phase D: spikes before the new backend.**

- **Leyden AOT:** can TC's classes (and the JIT profile of a warm compile) come from a JDK 25+
  AOT cache (`-XX:AOTCache`), and what does it do to the first kernel's compile time?
- **Cold-start compile time:** measure where a cold TC compile spends its time (JFR, the stage
  timers), then fix or improve it. Today TC's front end is 1.4-2x Graal's in a fresh JVM, while warm
  compiles are close (4.5 vs 3.8 ms).
- **Babylon as a frontend:** assess building TC-SSA from Babylon code models instead of bytecode
  (`tc-frontend-babylon`, plan Phase 10): what maps directly, what is lost or gained, and the effort.

**Then: the OpenCL dialect, then Metal.**

## Progress

Recorded per finding as fixed, not reproduced, or deferred (with the commit).

| ID | Status |
|---|---|
| CG-1 | **Reproduced and fixed.** `(long long) i + j * 2` printed for `(i + j) * 2`. Template operands are now parenthesised unless they fill a whole call argument, and the template's own precedence comes from its shape (lit `Cuda/printing.tcir`). |
| CG-2 | **Reproduced and fixed.** `--x` printed for `-(-x)`, which is a decrement (lit `Cuda/printing.tcir`). |
| CG-10 | **Reproduced and fixed.** Java `byte` printed as `char`; now `signed char` (lit `Cuda/printing.tcir`). |
