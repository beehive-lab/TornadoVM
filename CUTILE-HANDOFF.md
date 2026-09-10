# cuTile for TornadoVM - handoff

Branch `feat/cutile`, off `develop` (`9604d16e0`). Read this before touching anything.

This branch integrates NVIDIA **CUDA Tile** into the TornadoVM CUDA backend so that tile
kernels chain in one `TaskGraph` with JIT SIMT kernels and native library tasks. Full design:
`~/.claude/plans/warm-twirling-pearl.md` and the published note
<https://claude.ai/code/artifact/bdab0ad1-252a-458c-a2e5-c94503ed62d0>.

**The hard constraint:** every tile operation must lower to CUDA Tile (`ct::` calls, or Tile IR)
and let `tileiras` choose the instruction. Hand-writing `mma.sync` from a tile-shaped API is the
thing being replaced, not the goal. The check is that `--printKernel` shows `ct::mma` and **no**
`asm volatile("mma.sync...")`.

---

## 1. Status by phase

| Phase | What | State |
|---|---|---|
| 0 | Design note | **done** |
| 1 | Toolchain spike: tile C++ with TornadoVM's ABI, compiled to sm_89 cubin | **done, verified** (see 2) |
| 3a | `TileContext` Java API + JVM fallback | **done, verified** (see 3) |
| 3b | Compiler integration: plugins, nodes, LIR, emitter, gate | **in progress** (see 5) |
| 2 | Prebuilt-task bring-up: run a tile cubin inside a TaskGraph | **BLOCKED on driver R580+** (see 6) |
| 4 | Chaining: mixed JIT + tile + cuBLAS graph, cache keys, guards | **BLOCKED on driver R580+** (see 7) |
| 5 | In-process NVRTC (`nvrtcGetTileIR`) | blocked: needs R610+ and `nvidia-cuda-nvrtc` |
| 6 | Direct Tile IR emission | research, separate branch |

Phases 2 and 4 are blocked **only** by the driver. Everything else is developable on a box
without a new driver, because compilation and SASS inspection need no driver at all.

---

## 2. Phase 1: verified, and how to re-verify in 30 seconds

```
cd prototypes/cutile && make && make sass
```

Expected, exactly:

```
     80 HMMA.16816.F32
```

Tensor cores, selected by `tileiras` from one `ct::mma`, with zero hand-written PTX.
`make resources` should print `REG:232 STACK:0 SHARED:24576 ... CONSTANT[0]:420`.

### Environment setup (userspace, no root)

```
pip install --user 'cuda-tile[tileiras]'   # nvcc 13.3, tileiras, cuda_tile.h
pip install --user nvidia-cuda-cccl        # REQUIRED, see gotcha 1
```

Everything lands in `~/.local/lib/python3.10/site-packages/nvidia/cu13/`. Requirements:
CUDA Tile C++ needs toolkit **13.3+**, compute capability **8.0+**; the pip `nvcc` satisfies
the toolkit part regardless of the system CUDA (this box still has 12.6 installed).

### Gotchas found the hard way - do not rediscover these

1. **`nv/target` is missing from the toolkit wheels.** Without `nvidia-cuda-cccl`,
   `cuda_fp16.h` fails with `identifier "__NV_IS_DEVICE" is undefined`. Install the CCCL wheel.
2. **Never fix that with `-I/usr/local/cuda/include`.** Pulling CUDA 12.6 headers in makes
   12.6's `crt/host_defines.h` override 13.3's `__tile__`, and the build fails differently:
   `crt/cuda_tile.h(172): error: this declaration has no storage class or type specifier`.
3. **`extern "C" __tile_global__` works and is what we want.** Without it the entry symbol is
   mangled (`_Z4gemmPhS_S_S_S_S_S_iii`); with it, it is plain `gemm`. TornadoVM's existing
   `KERNEL_MODIFIER` convention carries over unchanged - keep `extern "C"` in the tile prologue.
4. **The param block matches TornadoVM's ABI exactly.** `CONSTANT[0]:420` = 352 B driver-reserved
   + 68 B of parameters = 7 pointers x 8 + 3 ints x 4, for the signature in `tile_gemm.cu`.
   Positional marshalling through `CUDAInstalledCode.setKernelArgs` needs no change.
5. **TMA did not fire** (no `UBLKCP` in the SASS) with `load_masked` on sm_89, despite
   `ct::assume_aligned(p, 16_ic)`. Unresolved. Probe unmasked loads on divisible extents before
   concluding anything about TornadoVM's payload alignment.

### The ABI the kernels are written against

```cpp
extern "C" __tile_global__ void gemm(
    unsigned char *_kernel_context, unsigned char *_constant_region,
    unsigned char *_local_region,   unsigned char *_atomics,   // 4 reserved slots, ignored
    unsigned char *a, unsigned char *b, unsigned char *c,      // buffers, positional
    int M, int N, int K);                                      // primitives
```

The four reserved parameters are declared and ignored **on purpose**: keeping them leaves
`CUDAArchitecture.getABI()` and `CUDAInstalledCode.setKernelArgs` untouched. `PAYLOAD_OFFSET`
in the kernels is 32, which must be replaced by the real value:
`TornadoNativeArray.ARRAY_HEADER` (24) plus the 6.1.0 payload padding
(`-Dtornado.cuda.payloadAlignment`, commit `94886413f`). Hardcoding 24 does not error, it
silently loses TMA eligibility.

---

## 3. Phase 3a: the Java API, done

`tornado-api/src/main/java/uk/ac/manchester/tornado/api/tile/`: `TileContext`, `Tile`,
`TensorView`, `PartitionView`, `DType`. Exported in `tornado-api/src/main/java/module-info.java`.

Every method has a working JVM fallback, so a tile kernel runs and debugs as plain Java and unit
tests have a reference. Verify:

```
J=$HOME/.sdkman/candidates/java/25.0.2-open      # api classes are class-file major 66
CP=/home/michalis/TornadoVM/tornado-api/target/classes:/tmp/tilecheck
$J/bin/javac -d /tmp/tilecheck -cp "$CP" prototypes/cutile/TileFallbackCheck.java
$J/bin/java -cp "$CP:/tmp/tilecheck" TileFallbackCheck
```

Expect `max abs error vs naive = 1.03e-7`, the three guard messages, then `PASS`.

### Two API decisions that differ from the design note - keep them

- **Fixed arity, not varargs.** `load(int bx)`, `load(int bx, int by)`, `partition(view, r, c)`,
  never `int...`. A varargs index list forces the invocation plugin to constant-fold an array
  allocation, which is the same fragility the MMA intrinsics already work around.
- **No `range()` iterator.** A plain counted `for (int t = 0; t < k / 32; t++)` is what lowers to
  `ct::irange`. An `Iterable<Integer>` would put an iterator allocation inside the kernel, and
  TornadoVM's existing loop phases already handle counted loops.
- Block indices are **public fields** (`tc.bidX`), mirroring `KernelContext.globalIdx`, so the
  existing `registerParameterPlugins` field-replacement mechanism applies rather than a new one.

---

## 4. What a tile task looks like end to end

```java
GridScheduler grid = new GridScheduler("s0.gemm", new WorkerGrid2D(M / 64, N / 64)); // TILE BLOCKS
grid.setLocalWork("s0.gemm", 1, 1, 1);                       // CUDA Tile requires block == 1

TaskGraph g = new TaskGraph("s0")
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
    .task("gemm", Gemm::mm, tileCtx, a, b, c, M, N, K)       // TileContext first param
    .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
```

A task is a tile task because its kernel's first parameter is a `TileContext`, exactly as a
leading `KernelContext` selects the kernel-parallel API. No new `TaskGraph` verb.

---

## 5. Phase 3b: compiler integration, the work in flight

All paths relative to
`tornado-drivers/cuda/src/main/java/uk/ac/manchester/tornado/drivers/cuda`.

Follow the MMA integration pattern exactly; it is the in-tree precedent that works.

1. **Two registration sites, both mandatory.**
   - `graal/compiler/plugins/CUDAGraphBuilderPlugins.java`: add `registerTileContextPlugins`
     beside `registerMMAPlugins` (around line 587), one `InvocationPlugin` per `TileContext`
     method, constant-folding shape arguments the way `resolveShape` folds `MMAShape`. Also skip
     the `TileContext` parameter in `registerParameterPlugins`, as `KernelContext` is skipped.
   - `graal/phases/TornadoCUDAIntrinsicsReplacements.java`: add a
     `case "Direct#TileContext.<name>"` for **every** intrinsic. Reflection-resolved kernels miss
     plugin lookup; an intrinsic registered in only one place works in some launch modes and
     fails silently in others.
2. **Nodes** in `graal/nodes/`: `CUDATileViewNode`, `CUDATilePartitionViewNode`,
   `CUDATileLoadNode` (masked flag), `CUDATileStoreNode`, `CUDATileCreateNode`,
   `CUDATileElementwiseNode`, `CUDATileMmaNode`, `CUDATileReduceNode`, `CUDATileShuffleNode`,
   `CUDATileCastNode`, `CUDATileBlockIdNode`. All `LIRLowerable`, all modelled on
   `CUDAMMAComputeNode.generate`.
3. **Tile-typed LIR values.** `CUDAKind` is an enum, so per-shape tile kinds cannot be enumerated
   the way the fixed `MMA_FRAG_*` set at `graal/lir/CUDAKind.java:199` is. Add one synthetic
   `TILE` kind plus a per-compilation `Variable -> declared C++ type` table on
   `CUDACompilationResultBuilder`, and generalise the hardcoded `isMMAFragment()` /
   `fragmentElementCType` switch in `CUDABackend.emitVariableDefs` into a
   `getDeclarationCType(Variable)` lookup. `Tile.toCppType()` already produces the spelling.
   Note a `Tile` carried across a loop becomes an **object phi**, the same situation the MMA
   fragment phis already handle (`float ul_52[4];`) - reuse that path.
4. **Emitters** in `graal/lir/CUDALIRStmt.java`, modelled on the `MMAComputeStmt` family at
   :2205, emitting `ct::` calls instead of inline PTX.
5. **Prologue** in `graal/backend/CUDABackend.java#emitPrologue`: `extern "C" __tile_global__`
   for entries, `__tile__` for non-inlined callees. `CUDACompilationResultBuilder#finish` (:241)
   already prepends `cuda_fp16.h`/`cuda_fp8.h` conditionally - add `cuda_tile.h` plus
   `namespace ct = cuda::tiles;` and `using namespace ct::literals;` there.
6. **Capability gate**: new `graal/phases/CUDATileSupportPhase.java`, appended first in
   `graal/compiler/CUDALowTier.java` beside `CUDATensorCoreSupportPhase`. Require cc >= 8.0,
   toolkit/NVRTC >= 13.3 via `CUDAProgram.getNvrtcVersion()` as the FP8 gate does, driver >= R580,
   and validate every shape/dtype pair. Throw a new `TornadoDeviceTileNotSupported`.
7. **Compile path**: `CUDACodeCache.installSource` already branches on content
   (`isInputSourceSPIRVBinary`). Add a third branch for tile source into a new
   `ffm/CUDATileCompiler.java` that runs `nvcc -tilecubin --tile-only -std=c++20 -arch=sm_XX`
   and loads the cubin through the existing `CUDAContext.createProgramWithBinary` (:220) seam.
   Discover `nvcc` the way NVRTC is discovered, including the pip wheel path.
8. **Cache keys**: add a `tile | simt` strategy tag plus toolkit identity to both the
   `"id-entryPoint"` map key and `moduleCacheKey(source, flags)`. Without it a SIMT and a tile
   compilation of the same method collide, and since `__tile__` cannot be called from
   `__global__`, a helper shared between a tile task and a SIMT task legitimately compiles twice.
9. **Launch**: new `scheduler/CUDATileScheduler` forcing local work `(1,1,1)` and never
   consulting `cuOccupancyMaxPotentialBlockSize` or `DEFAULT_BLOCK_SIZE`; new
   `graal/CUDATileInstalledCode` reusing `setKernelArgs` unchanged and rejecting batch
   processing. Note `CUDACommandQueue.clEnqueueNDRangeKernel` already hardcodes
   `sharedMemBytes = 0` and computes `grid = ceil(global/block)`, so with local work 1 the
   existing path emits exactly the launch CUDA Tile needs - **do not change that method**.

Build and check with:

```
make BACKEND=cuda          # the only supported invocation, see below
make checkstyle            # judge by exit code, never by grepping for ^[ERROR]
```

Repo conventions that will bite otherwise: build only with `make BACKEND=cuda` (or `ptx`);
**ASCII only in Java sources** - an em-dash in javadoc fails checkstyle; and deleting code tends
to leave unused imports, which also fails checkstyle.

---

## 6. PHASE 2 - for an agent on a box with driver R580+

**Goal:** run a cuTile kernel inside a TornadoVM `TaskGraph` with *zero* TornadoVM code changes,
proving the load-and-launch path before any codegen exists.

**Preconditions:** `nvidia-smi` reports driver **>= 580**; GPU compute capability >= 8.0;
`prototypes/cutile && make` succeeds (do the setup in section 2 first).

Why the driver: on driver 565 `cuModuleLoadData` of a CUDA-13 cubin returns
`CUDA_ERROR_INVALID_IMAGE`. Compilation is unaffected, only loading.

**Steps**

1. Confirm the blocker is actually gone. Smallest possible check, before involving TornadoVM:
   load `prototypes/cutile/tile_saxpy.cubin` with `cuModuleLoadData` and launch it with
   `grid=(n/256,1,1)`, `block=(1,1,1)`, `sharedMemBytes=0`. A few lines of ctypes against
   `libcuda.so` is enough. If this fails, stop - nothing downstream can work.
2. Rebuild `tile_saxpy.cu` with the **real** payload offset, not 32: read
   `TornadoNativeArray.ARRAY_HEADER` and the value of `-Dtornado.cuda.payloadAlignment` in this
   tree and compute it. Getting this wrong produces wrong results, not an error.
3. Register it as a prebuilt task. `TaskGraph.prebuiltTask(id, entryPoint, filename,
   AccessorParameters)` reaches `CUDATornadoDevice#compilePreBuiltTask` (:307), which reads any
   file's bytes and calls `installCode`. Entry point is `saxpy` (this is why `extern "C"`
   matters). Set the worker grid to **tile blocks** with local work `(1,1,1)`:
   `new WorkerGrid1D(n / 256)` and `setLocalWork(..., 1, 1, 1)`.
4. Chain it: put a plain `@Parallel` TornadoVM task before it and another after it, on the same
   buffers, and check the result is what both stages together should produce. This is the first
   real evidence that a tile kernel and a JIT kernel share device buffers with no host
   round-trip.
5. Then the GEMM: same procedure with `tile_gemm.cubin`, entry `gemm`, `WorkerGrid2D(M/64, N/64)`,
   compared against a CPU reference. `prototypes/cutile/TileFallbackCheck.java` is the reference
   semantics, and the `TileContext` JVM fallback is a second opinion.

**Exit criteria**

- A cuTile cubin executes inside a `TaskGraph`, results correct, no TornadoVM sources modified.
- `--enableProfiler console` reports a device kernel time for the tile task.
- Record in this file: driver version, whether step 1 passed first try, the payload offset you
  computed, and whether `UBLKCP` appears in the SASS once loads are unmasked (gotcha 5).

## 7. PHASE 4 - for the same box, after phase 3b lands

**Goal:** a mixed graph is correct, ordered, and does not touch the host in between.

1. **The mixed graph.** Build exactly this and check the numerics against a CPU reference:

```java
TaskGraph g = new TaskGraph("mixed")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, w1, w2)
    .transferToDevice(DataTransferMode.EVERY_EXECUTION, x)
    .task("norm", Kernels::rmsNorm, ctx, x, gamma, xn)              // JIT SIMT
    .task("ffn",  Ffn::gemmSilu,    tileCtx, xn, w1, h)             // cuTile
    .libraryTask("proj", CuBlas::cublasSgemm, /* ... */ h, w2, out) // native cuBLAS
    .task("add",  Kernels::residual, ctx, out, x, y)                // JIT SIMT
    .transferToHost(DataTransferMode.EVERY_EXECUTION, y);
```

2. **Prove the ordering claim rather than assuming it.** The design asserts that no new
   synchronisation is needed because library tasks bind to the plan's stream
   (`cublasSetStream(handle, getNativeStream(planId))`) and the interpreter brackets library
   launches with markers that join the role queues. Verify with NVTX/nsys: one stream, the four
   tasks in order, and **no H2D/D2H between them**. Library tasks already emit
   `nvidia/cublas/...` ranges. Re-run under `withIntraPlanConcurrency()` too - that is where the
   role-queue join actually matters.
3. **Undocumented interop, must be measured.** Neither is documented by NVIDIA:
   - CUDA-graph capture of a tile kernel (`executionPlan.withCUDAGraph()`). Until measured, the
     guard should refuse with a clear message; if capture works, remove the guard and say so
     here.
   - Mixing tile kernels and cuBLAS on one stream. Expected to work; confirm.
4. **Cache-key correctness.** In one JVM run, execute the same helper method from both a SIMT
   task and a tile task and confirm two distinct cached modules (`/var/cuda-codecache` or
   `-Dtornado.cuda.codecache.dir`), not one clobbering the other.
5. **Fallback.** Force the gate to fail (fake a low compute capability or an old toolkit) and
   confirm the task either falls back to the SIMT path or fails with
   `TornadoDeviceTileNotSupported` naming the missing requirement - never a silent wrong answer.
6. **Benchmark.** GEMM at 512^3 / 1024^3 / 2048^3: cuTile through TornadoVM, versus jTile M2's
   hand-written `mma.sync` (~87 us at 512^3 on a 4090), versus cuBLAS as a library task, versus
   hand-written tile C++. The number that matters is the last comparison, the codegen overhead,
   because `tileiras` is the same backend on both sides of it.

**Exit criteria**: mixed graph correct on one stream with no host round-trip; both interop
questions answered with evidence; benchmark table recorded here.

---

## 8. Notes for whoever picks this up

- Do not work in `/home/michalis/TornadoVM` - it has unrelated uncommitted MMA changes on
  `perf/mma-shape-operand-validation`. This branch lives in a worktree (`/tmp/tvm-cutile`);
  create your own with
  `git worktree add <dir> feat/cutile`.
- Tile IR is Apache-2.0 (`NVIDIA/cuda-tile`) with a published MLIR C-API, and `tileiras` ships in
  the toolkit. There is nothing to request access to. `tileiras` itself is closed, which is why
  phases 1-5 emit tile **source** and never try to reimplement tensor-core codegen.
- NVRTC does support tile in 13.3 (`--enable-tile`, `--tile-only`, `--simt-only`,
  `nvrtcGetTileIR`); the cubin from a tile compilation deliberately excludes tile code, so
  phase 5 must take the tile-IR blob and load *that*. This box has only CUDA 12.6's `libnvrtc`,
  so phase 5 also needs `pip install --user nvidia-cuda-nvrtc`.
- Keep the constraint honest: if a shortcut tempts you into emitting `mma.sync` from a
  `TileContext` op, that defeats the entire point of the branch.
