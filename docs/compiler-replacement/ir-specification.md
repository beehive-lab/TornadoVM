# TC IR specification

Status: **accepted for implementation** (user, 2026-10-09). Choices marked ⚑ are still validated
by the C3 design test (three hand-built kernels end to end on the RTX 4090); a C3 finding that
contradicts one is brought back for review before it changes.

## 0. What it resembles, and why

In one line: **MLIR `cf`/`scf`/`arith` with LLVM-style types, plus Java-semantics ops and
TornadoVM intrinsics.** Only concepts are shared; the IR, its names and its implementation are TC's
own (see [provenance.md](provenance.md)).

| Feature | Taken from (concept) | TC difference |
|---|---|---|
| Block arguments instead of phi nodes | MLIR `cf`, Cranelift CLIF, Swift SIL | — |
| Structured regions (`if`/`for`/`while`) for emission | MLIR `scf` | TC-S also folds single-use values into expression trees |
| Types `i1…i64`, `f16 bf16 f32 f64`, vectors | LLVM IR | adds pre-ABI `array<T>` and frontend-only `obj<…>` |
| Opaque pointers with address spaces | LLVM (NVPTX numbering) | — |
| `icmp`/`fcmp` predicates, `select`, `sext`/`zext`/`trunc`, `bitcast` | LLVM IR | — |
| Dialect-like op families | MLIR (`arith`, `math`, `memref`, `gpu`) | one flat namespace with families, not pluggable dialects |
| Typed SSA, flat op list per block, text form + parser, strict verifier | Cranelift CLIF | — |
| High-level Java array/field ops, host-API → intrinsic mapping | ILGPU | — |
| **Java semantics in the ops** (`f2i.sat`, masked shifts, Java `div`/`rem`, NaN/−0.0 in `min`/`max`) | none (LLVM leaves these undefined or poison) | TC-specific |
| **TornadoVM intrinsics as first-class ops** with per-dialect spellings | none | TC-specific |

Deliberately **not** like Graal: no Sea of Nodes or floating nodes, no frame states or
deoptimisation, no guards, no stamps. Every op lives in a block, in order.

### Benefits

Compared with today's Graal path:
1. **Readable and debuggable:** ordered text dumps that diff cleanly per pass; hand-written IR
   tests without Java or a GPU; no IGV.
2. **Correct Java numerics by construction:** the issue #1149 class (`>>>`, `(long)float`,
   NaN `min`/`max`) is encoded in op semantics, not left to each emitter.
3. **One core instead of three backend copies:** a backend is `TargetCaps` + dialect printer +
   registry rows (today 120–226 Graal-coupled classes per backend).
4. **Fewer bug sources:** no phis (SSA destruction = edge copies), no floating-node scheduling,
   no guards; at least six Graal phases exist only to remove guards, deopt state and proxies
   ([phase-migration-matrix.md](phase-migration-matrix.md) §C).
5. **Newer bytecode:** TC's own class-file reader lifts the `--release 22` cap.
6. **Smaller, faster start-up (to be measured at C7):** no ~4.6k-class Graal jar or its module
   flags; better fit for Leyden.

Compared with adopting MLIR or LLVM directly:
1. **Pure Java:** no JNI, FFM or C++ toolchain; JDK 21+ and Leyden friendly.
2. **Interop nearly free:** the shape maps mechanically to `.ll`/`.mlir` text, so `opt`/`mlir-opt`
   (second verifier), Alive2 (pass proofs), `-O3` (optimisation oracle) and future SPIR-V/AMD paths
   work through external tools.
3. **Keeps Java/TornadoVM meaning longer:** `array<T>`, `@Parallel`, reductions and
   `KernelContext` stay visible until Tornado lowering, which keeps parallelisation and `Access[]`
   analysis simple.
4. **Readable emitted C:** `scf`-shaped TC-S with expression folding produces
   `c[i] = a[i] + b[i];` rather than one temporary per value — better for cuda-gdb/`tcd` and `ncu`.

Costs: TC must implement its own optimiser for the parity subset — see
[optimizations.md](optimizations.md) — and C7 measures whether any performance gap remains after
NVRTC/OpenCL compilers optimise the emitted C.

TC has two IRs:

1. **TC-SSA** (`tc-ir`): typed SSA with block arguments. It is the input and output of every
   analysis and optimisation pass.
2. **TC-S** (`tc-lower`): structured, non-SSA statements and expressions. It is the only input of
   the source emitters (path A). Path B (direct emission) reads TC-SSA instead.

Both have a stable text form with a printer and a parser (`tc-debug`), round-trip tested.

## 1. TC-SSA

### 1.1 Structure

```
module      := function*
function    := kind @name(params) -> type attrs { block+ }      kind ∈ {kernel, device}
block       := ^label(args): op* terminator                      first block = entry; its args = params
op          := %result = opname operands : type attrs  loc?
terminator  := br ^b(values) | cond_br %c, ^t(values), ^f(values)
             | switch %v [k: ^b(values)]* default ^d(values) | ret %v? | unreachable
```

- ⚑ **Block arguments instead of phi nodes** (MLIR style). A terminator passes values to the
  arguments of its successor. Out-of-SSA is therefore "edge copies", sequenced as parallel copies.
- Every value has exactly one definition: an op result, a block argument or a function parameter.
- Definitions dominate their uses (checked by the verifier).
- Ops are Java records implementing a sealed `Op` interface. Passes use exhaustive pattern
  `switch`es; no visitor classes.
- Value names come from the class file's local-variable table where one exists (`%i`, `%sum`),
  otherwise numbered (`%7`). Names are for readability only and carry no semantics.

### 1.2 Types

| Type | Text | Notes |
|---|---|---|
| Integers | `i1 i8 i16 i32 i64` | two's complement; Java `char` is `i16` plus the `unsigned` flag on conversions |
| Floats | `f16 bf16 f32 f64` | IEEE 754; `bf16` for TornadoVM's quantised types |
| Vectors | `vec<4 x f32>` | TornadoVM `Float4`, `Int8`, `Half2`, …; lengths 2, 3, 4, 8, 16 |
| Pointer | `ptr<global>`, `ptr<shared>`, `ptr<constant>`, `ptr<private>` | opaque pointer with an address space; ⚑ numbering follows NVPTX (1, 3, 4, 5) |
| Java array | `array<f32>` | a Java or TornadoVM native array **before** ABI lowering; becomes `ptr<global>` + header offset |
| Object | `obj<Name>` | only in the frontend; must be gone after `tc-tornado` lowering (verifier rule) |
| Token | `token` | ordering-only results (barriers, atomics fences) |

### 1.3 Op families

| Family | Ops (examples) | Semantics source |
|---|---|---|
| Constants | `const 3 : i32`, `const 0x1p-3 : f32`, `const nan : f32` | JLS |
| Integer arithmetic | `add sub mul div rem neg and or xor shl shr ushr` | JLS §15.17–15.19: `div`/`rem` with Java rounding; shift counts masked (`& 31` / `& 63`) — the mask is explicit after canonicalisation so emitters never rely on C semantics |
| Float arithmetic | `fadd fsub fmul fdiv frem fneg`, flag `contract` (FMA allowed) | JLS §15.4 (strict IEEE); `contract` only when `tornado.enable.fma` |
| Comparison | `icmp.<pred>`, `fcmp.<pred>` (ordered/unordered explicit), `select` | JLS; NaN behaviour explicit in the predicate |
| Conversions | `sext zext trunc`, `f2i.sat`, `i2f`, `fext ftrunc`, `bitcast` | JLS §5.1.3: float→int saturates, NaN → 0 (`f2i.sat`) |
| Java arrays (pre-ABI) | `array.load %a[%i]`, `array.store`, `array.length` | bounds checks are not emitted (TornadoVM contract), but recorded as a diagnostic remark |
| Fields (pre-ABI) | `field.load %o.f`, `field.store` | only for parameter objects that `tc-tornado` flattens |
| Pointers (post-ABI) | `ptr.offset %p, %bytes`, `load`, `store`, `alloca.private T[N]`, `alloca.shared T[N]` | target ABI (header size, alignment) from `TargetCaps` |
| Calls | `call @fn(args)` | always inlined for kernels unless the target supports device functions and the inliner declines |
| Tornado intrinsics | `tornado.thread_id.global.x`, `tornado.group_size.local.y`, `tornado.barrier.local`, `tornado.reduce.add`, `tornado.atomic.add`, `tornado.printf`, `tornado.launch` | TornadoVM API contract; one registry row per intrinsic with CUDA / OpenCL / MSL / LLVM-NVVM / MLIR spellings |
| Math intrinsics | `math.sqrt`, `math.rsqrt`, `math.fma`, `math.min`, `math.max`, `math.exp`, `math.dp4a`, … | Java `Math` semantics (e.g. `min` with NaN and −0.0) unless the op carries `fast` |
| Vector | `vec.extract`, `vec.insert`, `vec.splat`, element-wise arithmetic on `vec<…>` | TornadoVM vector types |
| Tile, MMA, warp | `tile.*`, `mma.*`, `simd.*` | separate families, CUDA target only at first; added in Phase 6 |

### 1.4 Attributes and metadata

- `loc("VectorAdd.java":21, bci=14)` on every op created by the frontend.
- Loop attributes on loop headers: `parallel(dim=0)` (from `@Parallel` or `KernelContext`),
  `unroll(full|N)`, `reduce(%acc, add)`.
- Function attributes: `kernel`, `grid_stride`, `device_launch`.
- Memory effects are a property of the op kind (`pure`, `read(as)`, `write(as)`, `sync`), not
  stored per op. Kernel parameters do not alias (TornadoVM contract); private allocas never alias
  anything.

### 1.5 Verifier rules (excerpt)

1. Every block ends with exactly one terminator; terminators appear only at the end.
2. Successor argument count and types match the block's arguments.
3. Every use is dominated by its definition (block arguments dominate their block).
4. Operand and result types match the op's signature table.
5. After `tc-tornado` lowering: no `obj<…>`, no `array<…>`, no `field.*`, no Java-only intrinsics.
6. Before structuring: the CFG is reducible, or the structurizer must reject it with `TC-E3xxx`.
7. Address-space rules: `shared` allocas only in kernels; no `store` through `ptr<constant>`.

Diagnostics use stable codes: `TC-E1xxx` frontend, `E2xxx` IR/verifier, `E3xxx` Tornado lowering
and structuring, `E4xxx` target capability, `E9xxx` internal. Each message names the op, prints
it in context, and gives the Java source line.

### 1.6 Text form

Implemented by `IrPrinter` / `IrParser` (`tornado-compiler/tc-ir`); the lit tests under
`tc-ir/src/test/lit` are the normative examples.

- One instruction per line; `;` starts a comment.
- `%name = op operands : type [attributes]  loc("File.java":line, bci=n)`; instructions without a
  result have no `%name =` and no type.
- Intrinsics always take parentheses: `tornado.barrier.local()`, `%r = math.sqrt(%x) : f32`.
- Attributes are `[name, name(args)]` after the type (instructions) or after the block header.
- Field names are quoted: `field.load %o, "count" : i32`.
- `alloca.<space> T[N] : ptr<space>`; `switch %v [k: ^b, …] default ^d`.
- The entry block's arguments are the function parameters and are not repeated on `^entry`.

Example:

```
kernel @VectorAdd.add(%a: array<f32>, %b: array<f32>, %c: array<f32>) {
^entry:
  %n = array.length %c : i32                                   loc("VectorAdd.java":20)
  %i0 = tornado.thread_id.global.x() : i32
  %s = tornado.global_size.x() : i32
  br ^head(%i0)
^head(%i: i32) [parallel(dim=0)]:
  %more = icmp.slt %i, %n : i1                                 loc("VectorAdd.java":20)
  cond_br %more, ^body, ^exit
^body:
  %x = array.load %a[%i] : f32                                 loc("VectorAdd.java":21, bci=14)
  %y = array.load %b[%i] : f32
  %z = fadd %x, %y : f32 [contract]
  array.store %c[%i], %z
  %next = add %i, %s : i32
  br ^head(%next)
^exit:
  ret
}
```

## 2. TC-S (structured, non-SSA)

```
kernel  := SKernel(name, params, body: SBlock)
stmt    := SDecl(var, type, init?) | SAssign(var, expr) | SStore(ptr-expr, expr)
         | SIf(cond, then: SBlock, else: SBlock?)
         | SFor(init, cond, step, body)            -- recognised counted loops
         | SWhile(cond, body) | SLoop(label, body) -- general loops
         | SBreak(label?) | SContinue(label?) | SReturn(expr?) | SExpr(call)
         | SSwitch(expr, cases, default) | SBarrier(kind) | SComment(text)
expr    := var | const | unary | binary | select | convert | load | intrinsic-call
```

- ⚑ Shaped like MLIR `scf`: loops and conditionals are nested regions. Values that flow out of a
  region are written to declared variables, which is what makes it non-SSA.
- Pure single-use SSA values are folded into expression trees, so the emitted code reads like
  hand-written C (`c[i] = a[i] + b[i];`), not one temporary per value. Multi-use values and values
  with side effects become `SDecl` variables named after the Java local where known.
- `SFor` is produced only when the loop matches `init; cond; step` with one induction variable;
  otherwise `SWhile` or a labelled `SLoop` with `SBreak`.
- Every statement keeps its `loc`, so emitters can print `// VectorAdd.java:21` or `#line`.

## 3. Resolved by the drafts, re-checked at C3

1. Keep `array<T>` as a first-class pre-ABI type (readable IR, simple frontend) versus lowering to
   pointers in the frontend. Draft: keep, lower in `tc-tornado`.
2. Device functions: always inline (Graal parity) or emit `__device__` functions when the inliner
   declines. Draft: always inline for parity; device functions after C6.
3. Whether path B (direct emission) needs its own expression folding or reuses the TC-S
   expression builder. Draft: reuse.
4. Numbering of `TC-E` codes and their stability guarantee (documented, never reused).
