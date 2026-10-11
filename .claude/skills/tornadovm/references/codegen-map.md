# TornadoVM codegen map — OpenCL / CUDA / Metal

Kernels are compiled by **TC** (`tornado-compiler/`), TornadoVM's own compiler: Java bytecode to
CUDA C++ (NVRTC), OpenCL C or Metal Shading Language. The drivers (`tornado-drivers/<module>`) only
build and launch what TC prints. PTX and SPIRV are out of scope. `tornado-compiler/README.md` is the
overview; the design documents live in `mikepapadim/tornado-compiler-spec`.

## Pipeline and modules

Paths are under `tornado-compiler/<module>/src/main/java/uk/ac/manchester/tornado/tc/<package>/`.

| Stage | Module / class |
|---|---|
| Class-file reader, bytecode to TC-SSA, API bindings (`KernelContext`, native arrays, math, atomics, matrices) | `tc-frontend`: `Frontend`, `Translator`, `Bindings`, `LocalArrays`, `LocalCollections` |
| IR, types, verifier, intrinsic registry (names and effects) | `tc-ir`: `Type`, `Verifier`, `IntrinsicRegistry` |
| Optimisations | `tc-opt`: `PassManager.standard`, `Inliner`, `Canonicalizer`, `GlobalValueNumbering`, `LoopInvariantCodeMotion`, `FullLoopUnroll`, `MathPeepholes`, `FmaRematerialization` |
| TornadoVM lowering: specialisation, atomics, reductions, `@Parallel` loops, legality, kernel ABI | `tc-tornado`: `ValueSpecialisation`, `AtomicSlots`, `ReductionLowering`, `ParallelLoopLowering`, `Legality`, `AbiLowering` |
| SSA destruction and structuring | `tc-lower`: `Structurizer`, `Simplifier` |
| Source printing | `tc-codegen`: `CEmitter` with a `CDialect` per target (`CudaDialect`, `OpenClDialect`, `MetalDialect`); `Intrinsics` (spelling of each intrinsic per target), `Helpers` (preamble helper functions per target) |
| Entry point, options, access analysis for the runtime | `tc-runtime`: `TcCompiler`, `TcOptions`; `tc-runtime/.../tools/Tc` is `tc emit` |
| Runtime hook (compile, install, map diagnostics to TornadoVM exceptions) | `tornado-drivers/drivers-common/.../drivers/common/tc/`: `TcHook`, `TcKernels`, `TcArguments`, `TcAccessProvider` |

## Recipe: add an intrinsic or API method

1. **Name it** in `IntrinsicRegistry` (`tc-ir`) with its effects (pure, reads, writes, convergent).
2. **Bind it** in `Bindings` (`tc-frontend`): map the Java owner, name and descriptor to the intrinsic,
   or to `InlineJava` when the method body can be compiled as is.
3. **Spell it** per target in `Intrinsics` (`tc-codegen`); put any helper function it needs in
   `Helpers` for that target. A target without a spelling refuses the kernel with a TC diagnostic.
4. **Lit test** in `tc-codegen/src/test/lit/<Target>/*.tcir` (`RUN: tc-emit --target=... %s | FileCheck %s`,
   plus `nvcc`, `clbuild`, `metalc` or `mslcheck` lines that build the output with the device
   compiler when installed). `tornado-compiler/utils/update-checks.py <test>` regenerates CHECK lines.
5. **Unit test** — a `Test<Feature>` under `tornado-unittests/.../<feature>/`, validated against a
   sequential Java reference; run with `tornado-test <class> -V` (`-pk` prints the generated kernel)
   on every backend you built.

## Gotchas specific to codegen

- **Java semantics first.** Integer division, shifts, float-to-int conversion and NaN comparisons
  follow Java, not C. Metal compiles with fast math on, so NaN tests are printed with `isnan`
  (`CDialect.fastMath`).
- **No `goto` in MSL.** `MetalDialect.gotos()` is false; `CEmitter` lowers unstructured jumps to a
  flag plus `break`/`do { } while (false)`.
- **Clean room.** `SourceRules` (in every module's tests) and checkstyle fail the build on Graal,
  JVMCI or ASM imports.
- **Differential check.** `tornado-compiler/utils/tc-diff.py run --device <d> --out DIR --against OLD`
  runs the unit-test suites on one device and compares pass/fail and launch configurations with an
  earlier run.
- **CUDA vs PTX** — `tornado-drivers/cuda` (CUDA C++ via NVRTC) is the CUDA backend; PTX is out of scope.
