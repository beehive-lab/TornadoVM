# Existing work and reuse decisions (Phase 0)

Search covered: `gh repo list` for `mikepapadim` and `beehive-lab`, branches and PRs on both
TornadoVM repositories, local checkouts under `~`, a local Babylon/HAT build.

**No existing bytecode → own IR → C-like source pipeline exists.** The table records what can
inform the work. Reuse is constrained by the clean-room policy in [provenance.md](provenance.md):
third-party code is never copied; TornadoVM-authored code serves as behavioural reference.

| Asset | Location | Maturity | Decision |
|---|---|---|---|
| Structured control-flow recovery (post-dominators) | `tornado-drivers/{cuda,opencl,metal}/.../graal/compiler/*StructuredControlFlow.java` (3 × 428 LOC), PRs #848/#849 | Production, Graal-typed | Behavioural reference + regression cases; TC structurizer written fresh |
| Reflection JVMCI provider + classfile parser | `tornado-runtime/.../runtime/jvmci/reflection/` (2.1k LOC) | Production (JDK 27+ path, PR #894) | Candidate for frontend impl 1 (JDK 21); evaluate coverage in Phase 4 |
| ASM annotation visitor | `tornado-annotation/.../ASMClassVisitor.java` | Annotations only | Not a frontend |
| Prebuilt-source install path | `CUDATornadoDevice.compilePreBuiltTask` | Production | Runtime integration point for TC |
| JVMCI removal | PR #894 (merged), PR #1192 (open), `~/src/TornadoVM-fork/JDK27-NO-JVMCI.md` | Merged / in review | Independent track; JVMCI is assessed separately from Graal (Phase 8) |
| CUDA codegen audit | issue #1149, PRs #1054/#1043/#1138 | Bug list | Seed for the numeric/semantic conformance suite |
| CUDA Tile path | `CUDATileCompiler`, `tornado-unittests/.../tile/` | Production, Graal front-end | Last family to migrate |
| HAT C99 code builders | `~/src/babylon/hat/core/.../codebuilders/` | OpenJDK Babylon (GPLv2) | Concept only |
| Sea of Nodes `Simple` | `mikepapadim/Simple` (fork) | Teaching compiler | Concept only |
| ILGPU | `mikepapadim/ILGPU` (fork) | .NET IL → GPU JIT | Concept only |
| TornadoVM vs HAT feature matrix | `~/work/tornadovm-vs-babylon-cuda/docs/feature-matrix.md` | Study | Requirements input |
| `ProtonVM`, `ASM-Tornadifier`, `beehive-spirv-toolkit` | beehive-lab | Various | Not needed (no SPIR-V target live) |

Branches checked with no replacement-compiler work: `mikepapadim/TornadoVM` (`hat-tensors-tornadovm`,
Graal packaging branches), `beehive-lab/TornadoVM` (`claude/publicity-pr4-babylon`, docs only).
