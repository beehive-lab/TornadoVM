# Babylon as a TC frontend: assessment (design review Phase D)

Probe: `spikes/babylon/Probe.java`, run 2026-10-11 on the local Babylon build (`openjdk 28-internal`,
babylon `35157c612`). Three TornadoVM-style kernels (an element-wise loop with a conditional, a nested
reduction with `continue` and `Math.max`, a helper call) are reflected with `Op.ofMethod`, lowered
(`CodeTransformer.LOWERING_TRANSFORMER`) and put in SSA form (`SSA.transform`).

## What a Babylon frontend would receive

After lowering and SSA, the code model has TC-SSA's shape: blocks with block parameters (no phis),
and a small op set:

| kernel | ops after lowering + SSA |
|---|---|
| saxpy | add 2, array.load 2, array.store 1, branch 5, cbranch 2, constant 4, conv 2, gt 1, lt 1, mul 1, return 1 |
| rowSum | add 4, array.load 1, branch 8, cbranch 3, constant 6, conv 1, invoke 1, lt 2, mul 1, neq 1, return 1 |
| helperCall | add 1, array.length 1, array.load 1, branch 2, cbranch 1, constant 4, gt 1, invoke 1, return 1 |

Every op has a direct TC counterpart: arithmetic and comparisons map to `Binary`/`ICmp`/`FCmp`,
`conv` to `Convert`, `array.*` to TC's Java-level array ops, `branch`/`cbranch` to `br`/`cond_br` with
block arguments, `invoke` to `call` or a binding. Every op carries a source location (line and column).
Before lowering, the model also has structured ops (`for`, `if`, conditional expressions), which a
later version could map to TC-S directly instead of re-structuring.

## What maps directly, what is gained, what is lost

- **Direct:** types (`java.type:"float[]"` → `array<f32>`), the CFG with block parameters (no SSA
  construction: Braun et al. and the Translator's variable machinery are not needed), calls (a
  `MethodRef` gives owner, name and descriptor, so the `Bindings` table and its signature test are
  reused unchanged), and source locations.
- **Gained:** no javac-pattern recovery (the operand stack, `assert` guards, varargs arrays, boxing
  sequences and `null`-then-assign locals are already resolved to values); columns as well as lines.
- **Lost / blocking:**
  1. **Only `@Reflect` methods have a code model.** This build exports code model → bytecode
     generation (`jdk.incubator.code.bytecode.BytecodeGenerator`) but no public bytecode → code model
     lifting, so TornadoVM's existing tasks (plain method references, unannotated helpers such as
     `clamp` in the probe, the TornadoVM API's own methods) cannot be reflected. A Babylon frontend
     serves kernels written for it, not the current API surface.
  2. **No released JDK.** `jdk.incubator.code` exists only in the Babylon builds (here JDK 28-internal);
     TC's floor is JDK 21 and the AOT/native-image results assume JDK 25.
  3. **Two frontends to keep equal:** bindings, intrinsics and lowering would be shared, but parity
     would have to be tested per frontend.

## Effort

A `tc-frontend-babylon` adapter is small next to the bytecode Translator (about 1,500 lines): a
mapping of about twenty op classes and the Java types onto `IrBuilder`, reusing `Bindings`,
`Immediates`, the intrinsic registry and everything after the frontend. Estimate: 600-900 lines plus
tests, one to two weeks, most of it on parity tests for annotated kernels.

## Recommendation

Keep the bytecode frontend as TC's frontend. Build the Babylon adapter (plan Phase 10) when
TornadoVM wants a `@Reflect`-based kernel API, or when Babylon exposes bytecode lifting publicly.
Nothing in the core needs to change for it: the IR's block arguments, typed immediates and the
binding table already match what Babylon produces.
