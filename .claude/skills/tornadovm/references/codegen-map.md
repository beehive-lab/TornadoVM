# TornadoVM codegen map — OpenCL / CUDA / Metal

Where code generation lives for the three in-scope backends, and how to add a new node or intrinsic. PTX and SPIRV are out of scope.

## Package & module layout

Uniform pattern per backend `<B>` ∈ {OpenCL→`opencl`/`OCL`, CUDA→`cuda`/`CUDA`, Metal→`metal`/`Metal`}:

- Module dir: `tornado-drivers/<module>/src/main/java/uk/ac/manchester/tornado/drivers/<module>/`
- Package root: `uk.ac.manchester.tornado.drivers.<module>`
- Codegen packages: `.graal.backend`, `.graal.compiler`, `.graal.compiler.plugins`, `.graal` (lowering), `.runtime`
- Shared phases/utilities: `tornado-drivers/drivers-common`
- Maven profiles: `opencl-backend`, `cuda-backend`, `metal-backend`

Class-name prefix per backend: OpenCL = `OCL`, CUDA = `CUDA`, Metal = `Metal`.

## Key classes (verified present on all three backends)

Paths are relative to each backend's package root. Replace `<P>` with the prefix (`OCL` / `CUDA` / `Metal`).

| Role | Path |
|---|---|
| Backend (kernel prologue/epilogue, ABI) | `graal/backend/<P>Backend.java` |
| High tier (arch-agnostic → arch phases) | `graal/compiler/<P>HighTier.java` |
| Low tier (lowering to LIR-ready graph) | `graal/compiler/<P>LowTier.java` |
| Node → LIR builder (emit per-node) | `graal/compiler/<P>NodeLIRBuilder.java` |
| Compilation result builder (LIR → source/asm) | `graal/compiler/<P>CompilationResultBuilder.java` |
| GraphBuilder plugins / intrinsics registration | `graal/compiler/plugins/<P>GraphBuilderPlugins.java` |
| Lowering provider (load/store, snippets) | `graal/<P>LoweringProvider.java` |
| Code cache (compile + install kernels) | `<P>CodeCache.java` |
| TornadoDevice (runtime device driver) | `runtime/<P>TornadoDevice.java` |

Example concrete paths:
- OpenCL: `tornado-drivers/opencl/src/main/java/uk/ac/manchester/tornado/drivers/opencl/graal/backend/OCLBackend.java`, `.../graal/OCLLoweringProvider.java`, `.../graal/compiler/plugins/OCLGraphBuilderPlugins.java`
- CUDA: `tornado-drivers/cuda/src/main/java/uk/ac/manchester/tornado/drivers/cuda/graal/backend/CUDABackend.java`, `.../graal/CUDALoweringProvider.java`, `.../graal/compiler/plugins/CUDAGraphBuilderPlugins.java`
- Metal: `tornado-drivers/metal/src/main/java/uk/ac/manchester/tornado/drivers/metal/graal/backend/MetalBackend.java`, `.../graal/MetalLoweringProvider.java`, `.../graal/compiler/plugins/MetalGraphBuilderPlugins.java`

## Recipe: add a new node / intrinsic

1. **Define the node** — a Graal node (often a `FloatingNode` or `FixedWithNextNode`) under `.graal.nodes` for the backend; give it a LIR-emitting counterpart if needed under `.graal.lir`.
2. **Register the builtin** — map the Java method/pattern to the node in `<P>GraphBuilderPlugins` (an `InvocationPlugin`). This is also where the **reflection-path recovery** cases live (used on JDK 21+ where the sketcher can't descend into some FFM/`MemorySegment` internals).
3. **Lower it** — in `<P>LoweringProvider` (e.g. `lowerLoad/StoreIndexed`, or a snippet) if the node needs lowering before LIR.
4. **Emit LIR** — in `<P>NodeLIRBuilder.emitNode(...)`, add the case that appends the LIR op; the final source/asm text is produced in `<P>CompilationResultBuilder`.
5. **Mirror across backends** — implement for **both OpenCL and CUDA** (Metal if applicable). OpenCL historically lagged CUDA on reflection-path recoveries: if a feature already works on CUDA and not OpenCL, find the matching CUDA commit and mirror it (register the same plugin, add the same lowering/LIR case). Put backend-agnostic phases in `tornado-drivers/drivers-common`.
6. **Test** — add a `Test<Feature>` under `tornado-unittests/.../<feature>/` validating vs a sequential Java reference; run on each built backend with `tornado-test <class> -V` (and `-pk` to inspect the generated kernel).

## Gotchas specific to codegen

- **Half-float (`HalfFloat`) typing** — device-function (non-kernel) parameters and local `HALF` arrays need explicit HALF handling in `<P>Backend.emitMethodParameters` and `<P>LoweringProvider` (a plain SHORT path truncates fp16 values). The reflection path may wrap arrays in a `PiNode` — `GraphUtil.unproxify` before `instanceof` checks.
- **`ByteArray.getHalfFloat/setHalfFloat`** and native-array get/set must be registered as InvocationPlugins in `<P>GraphBuilderPlugins` (byte-addressed read/write nodes) rather than fixed in the constant-reflection provider — folding host `MemorySegment`/`VarHandle` internals yields garbage.
- **CUDA vs PTX** — `tornado-drivers/cuda` (CUDA-C via NVRTC) is a separate backend from PTX. Everything here refers to the `cuda` module.
