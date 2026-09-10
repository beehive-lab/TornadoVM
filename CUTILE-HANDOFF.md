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
| 3b | Compiler integration: plugins, nodes, LIR, emitter, gate | **done, builds clean** (see 5) |
| 2 | Prebuilt-task bring-up: run a tile cubin inside a TaskGraph | **BLOCKED on driver R580+** (see 6) |
| 4 | Chaining: mixed JIT + tile + cuBLAS graph, cache keys, guards | **BLOCKED on driver R580+** (see 7) |
| 3c | Compile path: `CUDATileCompiler`, `installSource` branch, cache keys, scheduler | **not started** (see 5, items 7-9) |
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
`CUDAArchitecture.getABI()` and `CUDAInstalledCode.setKernelArgs` untouched. The payload offset is
**`TornadoNativeArray.ARRAY_HEADER`**, nothing more. An earlier draft of this file said to add
the 6.1.0 payload padding on top; that is wrong. `CUDAMemorySegmentWrapper.HEADER_PAD` pads the
*allocation* so that `base + ARRAY_HEADER` lands on a `tornado.cuda.payloadAlignment` (32 byte)
boundary, and the kernel is handed the padded pointer and still indexes at `+ ARRAY_HEADER`.
So the alignment that `ct::assume_aligned(p, 16_ic)` promises is already guaranteed by the
runtime, and the plugin uses `TornadoOptions.PANAMA_OBJECT_HEADER_SIZE`.

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

## 5. Phase 3b: compiler integration, landed

All paths relative to
`tornado-drivers/cuda/src/main/java/uk/ac/manchester/tornado/drivers/cuda`.
`make BACKEND=cuda` is green with all of this in the tree.

**What exists**

1. `graal/lir/CUDAKind.java` - two synthetic kinds, `TILE` and `TILE_VIEW`, plus `isTileLike()`.
   They carry no Java class, because a tile's C++ type depends on its shape and cannot be
   enumerated the way the fixed `MMA_FRAG_*` set is.
2. `graal/lir/CUDATileStmt.java` - eight emitters: partition view, create, load, store, mma,
   binary, reduce, block id. Every one emits `ct::` calls; none emits inline PTX, and that is
   the invariant to preserve.
3. `graal/nodes/CUDATile*.java` - the nodes, all implementing the `CUDATileNode` marker, which
   also exposes `tileDType()` and `tileShape()` so a tile's type can be recovered from the node.
   `CUDATileViewNode` is deliberately *not* lowerable: it is a parse-time descriptor that the
   `partition(...)` plugin folds away.
4. `graal/compiler/plugins/CUDATileGraphBuilderPlugins.java` - the invocation plugins, called
   from `CUDAGraphBuilderPlugins#registerInvocationPlugins`. Two details worth keeping:
   `resolveTileNode` sees through a `ValuePhiNode`, without which the canonical GEMM is rejected
   because the accumulator arrives as a phi on every iteration after the first; and shapes are
   folded with a message naming the argument rather than bailing out of the sketch.
5. `graal/backend/CUDABackend.java` - `emitVariableDefs` skips tile-like variables (their
   defining statement declares them) and pre-declares *tile phis* with the concrete
   `ct::tile<...>` type, mirroring exactly what the MMA fragment phis already needed.
   `emitPrologue` emits `extern "C" __tile_global__` for a tile kernel, and the `TileContext`
   parameter is dropped from the signature as `KernelContext` is.
6. `graal/CUDATileKernels.java` - a task is a tile task when its kernel takes a `TileContext`.
   Matched by type name, so recognising one never depends on loading the API class.
7. `graal/backend/CUDAPreamble.java` + `graal/compiler/CUDACompilationResultBuilder.java` -
   `cuda_tile.h` and the `ct` / literals aliases are injected by the same source scan that
   already injects `cuda_fp16.h`.
8. `graal/phases/CUDATileSupportPhase.java`, registered first in `CUDALowTier` beside
   `CUDATensorCoreSupportPhase` - gates on cc >= 8.0 and toolkit >= 13.3, validates every mma
   operand/accumulator pair, and rejects a view that was never partitioned.

**What is missing, and the two traps in it**

- **`TornadoCUDAIntrinsicsReplacements` has no tile cases yet.** A reflectively resolved kernel
  skips plugin lookup, so the tile calls would survive as invokes. Rather than emit a call to a
  function that does not exist, `CUDATileSupportPhase#verifyNoUnintrinsifiedTileCalls` detects
  any surviving `TileContext.` or `PartitionView.` invoke and fails with an explanation. That
  makes the gap loud, not silent - **finishing it means adding a case per operation there**, the
  same way the MMA intrinsics are handled twice.
- **The compile and launch path is not wired** (section 3 phase A). Still to do:
  - `ffm/CUDATileCompiler.java` running `nvcc -tilecubin --tile-only -std=c++20 -arch=sm_XX`,
    reached from a third branch in `CUDACodeCache.installSource` next to the existing
    `isInputSourceSPIRVBinary` branch, loading the cubin through `CUDAContext
    .createProgramWithBinary` (:220). Discover `nvcc` the way NVRTC is discovered, including the
    pip wheel path in section 2.
  - a `tile | simt` strategy tag plus toolkit identity in both the `"id-entryPoint"` code-cache
    key and `moduleCacheKey(source, flags)`. Without it a SIMT and a tile compilation of the
    same method collide, and since `__tile__` cannot be called from `__global__`, a helper
    shared between a tile task and a SIMT task legitimately compiles twice.
  - `scheduler/CUDATileScheduler` forcing local work `(1,1,1)` and never consulting
    `cuOccupancyMaxPotentialBlockSize` or `DEFAULT_BLOCK_SIZE`, and
    `graal/CUDATileInstalledCode` reusing `setKernelArgs` unchanged. Note
    `CUDACommandQueue.clEnqueueNDRangeKernel` already hardcodes `sharedMemBytes = 0` and computes
    `grid = ceil(global/block)`, so with local work 1 the existing path emits exactly the launch
    CUDA Tile needs - **do not change that method**.

Build and check with:

```
make BACKEND=cuda          # the only supported invocation
make checkstyle            # judge by exit code, never by grepping for ^[ERROR]
```

Repo conventions that will bite otherwise: build only with `make BACKEND=cuda` (or `ptx`);
**ASCII only in Java sources** - an em-dash in javadoc fails checkstyle; and deleting code tends
to leave unused imports, which also fails checkstyle. Do not `source setvars.sh` before building,
and build with `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.2-open`.

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
