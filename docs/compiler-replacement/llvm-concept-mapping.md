# LLVM / MLIR / ILGPU concept mapping

LLVM and MLIR are sources of **design ideas** for TC, not dependencies. The table maps each concept
to the TornadoVM problem it addresses and to its place in TC. TC never links any LLVM/MLIR library
(no JNI, no FFM); the optional `tc-interop` module only writes `.ll`/`.mlir` text for external tools
(see [toolchain.md](toolchain.md)). Concepts are re-expressed in TC's own design and names (see
[provenance.md](provenance.md)).

Priority: **P0** needed for parity, **P1** design improvement worth having before parity is
declared, **P2** later optimisation or research. Priorities are revisited at gate C2.

| LLVM / MLIR concept | TornadoVM problem or opportunity | TC adaptation | Module | Priority |
|---|---|---|---|---|
| Frontend / optimiser / backend separation | Graal tiers interleave Java semantics, Tornado transforms and backend lowering; three backends copy most of it (Metal is a near-copy of OpenCL) | One frontend, one SSA IR, one pass pipeline; backends only provide target capabilities and a source dialect | all | P0 |
| Typed SSA IR with a verifier | Invalid graphs today surface late as NVRTC/OpenCL build errors or wrong results | Typed SSA IR; verifier after the frontend and after every pass in tests (`-Dtornado.tc.verify=each`) | `tc-ir` | P0 |
| MLIR block arguments instead of phi nodes | Phi placement and destruction are a frequent source of codegen bugs | Blocks take arguments; branches pass values. SSA destruction becomes edge copies with parallel-copy sequencing | `tc-ir`, `tc-lower` | P0 |
| MLIR `scf` structured control flow | The structured C output needs `if`/`for`/`while` reconstructed; today done per backend on the Graal CFG | `scf`-shaped structured IR (`SIf`, `SLoop` with yielded values) as the single input to every emitter | `tc-lower` | P0 |
| New pass manager: analyses with explicit preserve/invalidate | Graal phases recompute or silently reuse stale information | Passes declare required and preserved analyses; the manager caches and invalidates (dominators, loops, memory effects) | `tc-opt` | P0 |
| `TargetTransformInfo` / target capabilities | Device differences (fp64, half, atomics, warp size, local-memory size) are scattered through node lowering | `TargetCaps` record queried by legality checks and lowering; emitters stay declarative | `tc-runtime`, `tc-codegen` | P0 |
| Intrinsics table (`Intrinsics.td`) | Math and builtin mapping is spread over many node classes per backend | One op/intrinsic registry with CUDA / OpenCL / MSL / LLVM-NVVM / MLIR spellings per row | `tc-codegen` | P0 |
| Address spaces (`ptr addrspace(n)`) | Global, shared (local), constant and private memory are separate node families | One pointer type with an address-space attribute, using NVPTX numbering (1 global, 3 shared, 4 constant, 5 local) | `tc-ir` | P0 |
| InstCombine / InstSimplify, constant folding | Numeric bugs from issue #1149 (`>>>`, NaN min/max, `(long)float`, shift masking) | Canonicaliser with Java semantics as small, separately tested rules; scalar rules checked with Alive2 on exported `.ll` | `tc-opt` | P0 |
| DCE, GVN / CSE | Graal provides these implicitly; parity needs equivalent cleanliness of the emitted code | Classic DCE and dominator-based GVN on the SSA IR | `tc-opt` | P0 |
| Inliner with cost model | Kernels call helper methods; GPUs need full inlining | Always-inline for kernel-reachable methods, with a recursion check and a diagnostic for unsupported recursion | `tc-opt` | P0 |
| LoopInfo, ScalarEvolution (subset) | `@Parallel` loops and reductions need induction-variable and trip-count facts | Loop nesting forest and induction-variable recognition (affine subset) | `tc-opt`, `tc-tornado` | P0 |
| Memory SSA / alias analysis | `Access[]` (READ/WRITE per argument) and safe reordering need memory effects | Explicit memory effects per op; a simple argument-based alias analysis (TornadoVM arguments do not alias by contract) | `tc-ir`, `tc-tornado` | P0 |
| `-verify-each`, `-print-after-all`, `-opt-bisect-limit` | Debugging Graal compilations means IGV and reading graphs | `-Dtornado.tc.verify=each`, `-Dtornado.tc.dump=after:<pass>`, `-Dtornado.tc.passLimit=N` | `tc-debug` | P0 |
| Textual IR with round-trip parser (`.ll`, `.mlir`) | No human-readable, hand-writable IR exists today | TC textual IR with printer and parser; golden tests written in it | `tc-debug`, `tc-tests` | P0 |
| FileCheck / lit tests | Graal-path tests are end-to-end only | A FileCheck-style matcher in Java for golden IR and source tests | `tc-tests` | P0 |
| Optimisation remarks (`-pass-remarks`) | Users cannot see why a loop was or was not parallelised or reduced | Remarks as readable text plus JSON lines (`-Dtornado.tc.remarks=...`) | `tc-debug` | P1 |
| Debug locations (`!dbg`, `#line`) | cuda-gdb shows generated C, not Java lines | Source location on every op; `// File.java:N` comments and optional `#line` in emitted code | `tc-ir`, `tc-codegen` | P1 |
| Translation validation (Alive2) | Canonicaliser bugs give wrong results silently | Export before/after `.ll` for scalar kernels and check with `alive-tv` in CI when available | `tc-interop`, `tc-tests` | P1 |
| Compilation caching with stable keys | Recompilation cost across runs; future Leyden integration | Cache key = bytecode hash + TC version + options + target caps; analysis results separated from device code | `tc-runtime` | P1 |
| Pass statistics / `-time-passes` | Unknown cost distribution inside compilation | JFR events per pass (`TcPass`) plus a timing table in the HTML report | `tc-runtime`, `tc-debug` | P1 |
| Loop vectoriser / SLP | Vector types are explicit in TornadoVM today | Not needed for parity; reconsider after C7 measurements | — | P2 |
| Polyhedral (Polly) / loop tiling | Possible performance gains for stencils and matrix multiply | Research item after parity | — | P2 |
| MLIR dialect lowering chains (`gpu` → `nvvm`/`rocdl`/`spirv`) | AMD and SPIR-V targets are absent | Offline experiment through `tc-interop` `.mlir` export (Phase 9), external tools only | `tc-interop` | P2 |
| LLVM NVPTX backend (`llc`) | NVRTC compile latency | Offline experiment via external `llc`; compared against NVRTC at C9 | `tc-interop` | P2 |

## ILGPU concepts

ILGPU (.NET IL → own SSA IR → PTX / OpenCL C) is the closest existing analogue to TC. As with LLVM,
only concepts are taken; no code, structure or names (see [provenance.md](provenance.md)).

| ILGPU concept | TornadoVM problem or opportunity | TC adaptation | Module | Priority |
|---|---|---|---|---|
| Stack-machine bytecode → typed SSA by abstract interpretation of the operand stack and locals | Graal's bytecode parser is the deepest Graal coupling | Frontend walks the CFG with a typed abstract stack/locals state and builds SSA on the fly (with Braun et al. for merges) | `tc-frontend` | P0 |
| Remapping host API calls to device intrinsics | `KernelContext`, `TornadoMath`, vector types, atomics are matched by Graal plugins per backend | One intrinsic-binding table: Java method signature → TC intrinsic op, resolved in the frontend | `tc-frontend`, `tc-tornado` | P0 |
| Backend hooks over a shared core | Three near-copies of node lowering | Backends provide `TargetCaps` + dialect printer only; legality and lowering are shared | `tc-codegen` | P0 |
| SSA → OpenCL C emission without a structured intermediate | Matches TC's path B (direct emission) | `DirectEmitter` for reducible kernels, sharing the op registry and verifier with path A | `tc-codegen` | P0 |
| Kernel cache keyed by method + specialisation + target | Recompilation; future Leyden reuse | Same key design as the LLVM caching row | `tc-runtime` | P1 |
| Specialisation on constant arguments | Some kernels benefit from compile-time sizes | Research item after parity (TornadoVM already has some constant specialisation) | — | P2 |

## Concepts deliberately not adopted

| Concept | Reason |
|---|---|
| Sea of Nodes (floating nodes, schedule late) | Harder to debug and print; a block-structured SSA IR with GVN gives the parity-level optimisations kernels need. Reconsider only if profiling at C7 shows a scheduling gap |
| SelectionDAG / MachineIR / register allocation | TC emits source code; the vendor compilers (NVRTC, OpenCL, Metal) do instruction selection and register allocation |
| Exceptions / unwind model | GPU kernels have no unwinding; exception paths are bailout diagnostics |
| A full LLVM-style type system (structs, opaque pointers everywhere) | TornadoVM kernels use primitives, arrays, vector types and a fixed set of object shapes; extend only when a parity case needs it |
