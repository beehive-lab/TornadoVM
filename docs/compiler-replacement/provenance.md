# Provenance and clean-room policy

TC must be free of copyright entanglement with Graal, OpenJDK Babylon/HAT, LLVM, ILGPU,
Sea-of-Nodes `Simple` and any other compiler.

## Rules

1. No code, comments, class structure, identifiers or file layout are copied from third-party
   compilers. They may inform *concepts* only.
2. Every non-trivial algorithm in `tornado-compiler/` cites its published source below.
3. While writing a TC component, the corresponding Graal class is not consulted. Graal is used
   as a black box (behaviour, `--printKernel` output) through the differential harness.
4. TornadoVM-authored code (same project, same licence) may be read as behavioural reference:
   kernel ABI, intrinsic names, regression cases. TC re-implements, it does not port.
5. Intrinsic and builtin tables are derived from the vendor specifications
   (CUDA C++ Programming Guide, OpenCL C 3.0, Metal Shading Language Specification).
6. TC adds no new third-party dependency without review.

## Automated evidence

| Check | When | Tool |
|---|---|---|
| No `tornado.graal`, `jdk.vm.ci`, `org.graalvm` imports in `tornado-compiler/**` | every build | test in `tc-tests` (import scan) |
| Licence header present on every TC source file | every build | test in `tc-tests` |
| Similarity scan vs Graal 23.1.0 sources and HAT `core/` | gates C4, C6, C8 | JPlag |
| Third-party dependency report | gates C4, C6, C8 | `mvn license:aggregate-third-party-report` |

## Algorithm sources

| Component | Source |
|---|---|
| SSA construction | M. Braun, S. Buchwald, S. Hack, R. Leißa, C. Mallon, A. Zwinkau. *Simple and Efficient Construction of Static Single Assignment Form.* CC 2013 |
| Dominators | K. Cooper, T. Harvey, K. Kennedy. *A Simple, Fast Dominance Algorithm.* 2001 |
| SSA destruction | B. Boissinot et al. *Revisiting Out-of-SSA Translation for Correctness, Code Quality, and Efficiency.* CGO 2009; V. Sreedhar et al. *Translating Out of Static Single Assignment Form.* SAS 1999 |
| Structured control flow | N. Ramsey. *Beyond Relooper: Recursive Translation of Unstructured Control Flow to Structured Control Flow.* ICFP 2022 |
| Value numbering | C. Click. *Global Code Motion / Global Value Numbering.* PLDI 1995 (concept) |
| Loop nesting | P. Havlak. *Nesting of Reducible and Irreducible Loops.* TOPLAS 1997 |
| Class-file format | *The Java Virtual Machine Specification*, chapter 4 |

## Novel design decisions

Recorded as they are made, with the gate at which they were reviewed.

| # | Decision | Rationale | Gate |
|---|---|---|---|
