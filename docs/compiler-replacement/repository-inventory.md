# Repository inventory (Phase 0)

Recorded 2026-10-09.

## Reference baseline

| Item | Value |
|---|---|
| Upstream | `beehive-lab/TornadoVM`, branch `develop` |
| Reference revision | `ae409166a0ad40b77a984fa7afadd39211b19429` (2026-10-09 17:15 +0100) |
| Work repository | `mikepapadim/tornadovm-compiler` (private), branch `compiler/main` |
| Remotes | `origin` = private work repo, `upstream` = beehive-lab |
| Graal | 23.1.0, relocated to `tornado.graal.compiler.*` by `bin/build_graal_module.py`, jar `graalJars/tornado-graal-23.1.0.jar` |
| JVMCI | `tornado.jvmci:jvmci` 21.0.2 (copied JDK 21 classes, module `jdk.internal.vm.ci`), plus reflection-based provider in `tornado-runtime/.../runtime/jvmci/reflection/` |
| Build profiles | `jdk21` (enable-preview), `graal-jdk-21`, `jdk22plus` (`--release 22`, capped by Graal's classfile reader) |
| Live backends | `opencl`, `cuda` (CUDA C via NVRTC, plus CUDA Tile via `nvcc -tilecubin`), `metal` |
| Dead backends | `ptx`, `spirv` (one stub file each, not in `tornado-drivers/pom.xml`) |

## Machine

| Item | Value |
|---|---|
| GPU | NVIDIA GeForce RTX 4090, driver 610.57.04 |
| OpenCL platforms | NVIDIA CUDA (RTX 4090), Intel (UHD Graphics 770) |
| JDKs (sdkman) | 21.0.2-open, 25.0.2-open, 26.0.1-open, 27.ea.24-open |
| Tools | cmake 3.22.1, Maven 3.6.3 (`./mvnw` in repo) |
| Metal | no Apple device available: Metal is build- and golden-tested only |

## Graal coupling per module (Java files importing the package, excluding `target/`)

| Module | Java files | `tornado.graal.compiler` | `jdk.vm.ci` |
|---|---:|---:|---:|
| tornado-runtime | 211 | 83 | 59 |
| tornado-drivers/drivers-common | 41 | 27 | 11 |
| tornado-drivers/opencl | 242 | 155 | 142 |
| tornado-drivers/metal | 240 | 162 | 144 |
| tornado-drivers/cuda | 316 | 217 | 194 |
| tornado-annotation | 4 | 0 | 1 |
| tornado-api, unittests, examples, benchmarks, matrices, fuzz, cu* libraries | — | 0 | 0 |
| **Total** | | **644** | **551** |

TornadoVM-specific Graal classes under `graal/`: runtime 83 (46 nodes, 20 phases), OpenCL 161
(73 nodes, 17 phases, 21 LIR), Metal 169 (80/17/21), CUDA 226 (122 nodes, 21 phases, 26 LIR).

## Current compiler pipeline (reference)

1. `TornadoTaskGraph` creates a `SketchRequest`.
2. `TornadoSketcher.buildSketch` (`tornado-runtime/.../runtime/sketcher/`) parses bytecode with
   Graal's `GraphBuilderPhase` and runs `TornadoSketchTier`, producing a cached `Sketch`
   (high-level graph + argument `Access[]`).
3. `<Backend>TornadoDevice.compileTask` → `<Backend>Compiler.compileSketchForDevice`, which
   runs the backend High/Mid/Low tiers, LIR generation and `<Backend>CompilationResultBuilder`/
   `<Backend>Assembler` source emission.
4. The driver compiles source: `clBuildProgram` (OpenCL), Metal library compile, NVRTC (CUDA).

Hook used by TC: `tornado-drivers/cuda/.../runtime/CUDATornadoDevice.java` — `compileTask`
(Graal path) and `compilePreBuiltTask` → `deviceContext.installCode(planId, meta, id, entry, source)`.
