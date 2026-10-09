# TornadoVM compiler replacement ("TC")

Goal: replace TornadoVM's dependency on the vendored Graal compiler (`tornado.graal.compiler.*`)
with an independent, clean-room compiler:

```
Java bytecode ─▶ tc-frontend ─▶ tc-ir (typed SSA) ─▶ tc-opt / tc-tornado passes
             ─▶ tc-lower (SSA destruction + structuring) ─▶ structured IR
             ─▶ tc-codegen (CUDA C++ / OpenCL C / MSL) ─▶ existing driver install path
```

Graal stays the reference implementation and fallback until gate C8.

| Document | Content |
|---|---|
| [examples.md](examples.md) | **End-to-end examples: Java → TC IR → TC-S → CUDA, next to Graal's CUDA** |
| [phase-trace.md](phase-trace.md) | Measured: which Graal/TornadoVM phases and node classes the CUDA suite actually runs |
| [plan.md](plan.md) | Approved execution plan, phases 0–10 and gates C0–C10 |
| [checkpoints.md](checkpoints.md) | Gate results, evidence, profiling decision reviews |
| [repository-inventory.md](repository-inventory.md) | Repositories, revisions, configurations |
| [existing-work.md](existing-work.md) | Prior work and reuse decisions |
| [provenance.md](provenance.md) | Clean-room policy, algorithm sources, novel decisions |
| [toolchain.md](toolchain.md) | Optional LLVM/MLIR/Alive2 tools (external processes only) |
| [ir-specification.md](ir-specification.md) | TC-SSA and TC-S (accepted) |
| [testing.md](testing.md) | LLVM-style file tests (lit/FileCheck) and API unit tests |
| [optimizations.md](optimizations.md) | Optimisations and kernel transformations TC must deliver, with gates and status |
