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
| 2 | Prebuilt-task bring-up: run a tile cubin inside a TaskGraph | **BLOCKED on driver R580+** - boundary reached and confirmed, see 5b (see 6) |
| 4 | Chaining: mixed JIT + tile + cuBLAS graph, cache keys, guards | **BLOCKED on driver R580+** (see 7) |
| 3c | Compile path: `CUDATileCompiler`, `installSource` branch, cache keys | **done; end to end to a cubin** (see 5b) |
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

## 5b. Phase 3c: the compile path, and the exact point where the driver stops us

`make BACKEND=cuda` green, `make checkstyle` green, and a tile task now runs all the way from
Java to a cubin. Reproduce with:

```
source ./setvars.sh && export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.2-open
tornado --printKernel --jvm="-Dtornado.recover.bailout=False" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.tile.TileVectorAdd
```

**What it prints.** This is TornadoVM compiler output, not hand-written:

```cpp
#include "cuda_tile.h"
namespace ct = cuda::tiles;
using namespace ct::literals;
extern "C" __tile_global__ void vectorAdd(long long *_kernel_context, unsigned char *_constant_region,
        unsigned char *_local_region, int *_atomics,
        unsigned char *arg1, unsigned char *arg2, unsigned char *arg3, int arg4)
{
  unsigned long long ul_0, ul_1, ul_2;
  int i_6;

  // BLOCK 0
  ul_0  =  (unsigned long long) arg1;
  ul_1  =  (unsigned long long) arg2;
  ul_2  =  (unsigned long long) arg3;
  auto tview_3 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_0 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  auto tview_4 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_1 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  auto tview_5 = ct::partition_view{ct::tensor_span{ct::assume_aligned(reinterpret_cast<float *>(ul_2 + 16), 16_ic), ct::extents{4096}}, ct::shape{256_ic}};
  i_6 = ct::bid().x;
  auto tile_7 = tview_3.load(i_6);
  auto tile_8 = tview_4.load(i_6);
  auto tile_9 = tile_7 + tile_8;
  tview_5.store(tile_9, i_6);
  return;
}  //  kernel
```

No `asm volatile`, no `mma.sync`, no thread indexing: the invariant held.

**Then it stops, in exactly one place:**

```
[TornadoVM-CUDA] cuModuleLoadDataEx failed: device kernel image is invalid
```

That is driver 565 refusing a CUDA 13 cubin. **nvcc had already succeeded** - the failure is at
module load, after compilation. Verified independently: that exact generated source, saved as
`prototypes/cutile/generated_vectoradd.cu`, compiles with
`nvcc -tilecubin --tile-only -std=c++20 -arch=sm_89` and disassembles to real sm_89 SASS
(`REG:17 SHARED:0 CONSTANT[0]:412`, and a `BAR.SYNC.DEFER_BLOCKING` - the tile compiler expanded
the single logical thread into multi-threaded code, which is the whole point of the model).
The entry symbol is the unmangled `vectorAdd`, so `extern "C" __tile_global__` behaves.

**So the remaining gap to a working tile kernel is the driver, and nothing else.**

**Five traps cleared getting here. Do not rediscover them:**

1. `receiver.get(true)` inserts a null-checking `PiNode`, so the receiver of `view.load(...)` is
   never the partition node. `resolveTileNode` unwraps it. Without this every tile access fails
   with "could not determine the tile type", which reads like an API misuse and is not.
2. `TornadoTaskGraph.isArgumentIgnorable` demanded a transfer for the `TileContext`. It now sits
   beside `KernelContext`, the only other host-side-only parameter.
3. The driver reflects over a context object's fields to allocate its (unused) device buffer, so
   `tornado-api` must `opens uk.ac.manchester.tornado.api.tile`, exactly as it already opens the
   package holding `KernelContext`. Skipping the allocation outright would be better and would
   help `KernelContext` too; it is not done here.
4. `CUDAVariablePrefix` needs an entry per `CUDAKind`, or the assembler throws
   "Unsupported type: tile_view" while naming variables.
5. **The capability gate must check nvcc, not NVRTC.** It originally read
   `CUDAProgram.getNvrtcVersion()`, which reports the *system* CUDA (12.6 here) and rejected
   every tile kernel even with a perfectly good userspace nvcc 13.3 - that is, it rejected
   exactly the setup section 2 recommends. It now calls `CUDATileCompiler.toolkitVersion()`.

**Still to do in 3c**, and only reachable with a driver, so it is deliberately left for phase 2:
`scheduler/CUDATileScheduler` (force local work to 1x1x1) and `graal/CUDATileInstalledCode`.
The example pins `setLocalWork(1, 1, 1)` by hand instead, which is why it gets as far as it does.

## 5c. Pending work, by whether it needs the driver

Audited against the API surface, not from memory. `A` items need a driver R580+ box; `B` items
can be done anywhere.

### A. Needs driver R580+ (the reason this handoff exists)

| # | Item | Notes |
|---|---|---|
| A1 | `scheduler/CUDATileScheduler` | Force local work to 1x1x1; never consult `cuOccupancyMaxPotentialBlockSize` or `DEFAULT_BLOCK_SIZE`. Until it exists, a tile task only launches correctly if the user pins `setLocalWork(1, 1, 1)` by hand, as `TileVectorAdd` does. |
| A2 | `graal/CUDATileInstalledCode` | `TornadoInstalledCode` is four methods; reuse `setKernelArgs` unchanged, reject batch processing. Selected by the installed code so SIMT and tile tasks can share a device. |
| A3 | Phase 2 bring-up | Section 6. Start by running the section 5b command and seeing how much already passes. |
| A4 | Phase 4 chaining | Section 7. The mixed JIT + tile + cuBLAS graph, one stream, no host round trip. |
| A5 | CUDA-graph capture of a tile kernel | Undocumented by NVIDIA. Measure it; today nothing guards `withCUDAGraph()` on a tile task, and it should refuse until proven. |
| A6 | cuBLAS interop on one stream | Undocumented. Expected to work; confirm. |
| A7 | TMA question | The GEMM cubin shows no `UBLKCP` with masked loads on sm_89. Retry with unmasked loads on divisible extents before drawing any conclusion about alignment. |

### B. Not blocked by the driver

| # | Item | Notes |
|---|---|---|
| B1 | Plugins for `full`, `iota`, `scale`, `transpose`, `cast` | These five API methods have **no invocation plugin**, so calling one hits the unintrinsified guard in `CUDATileSupportPhase`. The failure is loud, not silent, but the API currently promises more than the compiler implements. |
| B2 | Unit tests | `tornado-unittests/.../tile/` does not exist yet. Port `TestTileElementwise` and `TestTileMatmul` from `feat/jtile` onto `TileContext`, add masked-edge and negative-guard cases, and register the suite in `tornado-assembly/src/bin/tornado-test`. |
| B3 | `TornadoCUDAIntrinsicsReplacements` tile cases | A reflectively resolved kernel skips plugin lookup. Guarded today, so it fails with an explanation; finishing it means one case per operation, as the MMA intrinsics have. |
| B4 | Documentation | No `docs/source/*.rst` page for the tile API, and no mention in the hybrid API guide. |
| B5 | Host-only API methods | `getRank`, `getTileDimension`, `getBlockCount`, `setBlockIndex`, `setBlockCount` exist for the JVM fallback and have no device meaning. Calling one inside a kernel hits the same guard. Document them as host-only, or split them out of the kernel-facing type. |

### Verified `ct::` spellings, so nobody has to guess

Probed against nvcc 13.3 rather than inferred from documentation:

| Operation | Spelling | Status |
|---|---|---|
| fill | `ct::full<TileType>(value)` | compiles |
| scale by a scalar | `tile * 2.0f` (broadcast, no named function) | compiles |
| transpose | `ct::transpose(tile)` | compiles |
| reduction | `ct::sum(tile, 1_ic)` | compiles |
| iota | `ct::iota<ct::tile<int, ct::shape<16>>>()` | compiles |
| element conversion | **`ct::element_cast<Element>(tile)`** | compiles. **Not** `ct::cast`, which does not exist. |
| matmul | both `ct::mma(a, b, acc)` and `ct::matmul` exist | `mma` is what the emitter uses |

**CUDA Tile rejects narrowing conversions.** `element_cast<__half>` on an f32 tile is refused by
the constraint `tile_convertible_to`; widening f16 to f32 is accepted. So a `cast` plugin must
validate direction and produce a real message, not pass the pair through to the tile compiler.
A tile of `__half` also requires `#include <cuda_fp16.h>`, which the existing preamble scan
already injects.

## 6. PHASE 2 - for an agent on a box with driver R580+

**Goal:** run a cuTile kernel inside a TornadoVM `TaskGraph` with *zero* TornadoVM code changes,
proving the load-and-launch path before any codegen exists.

**Preconditions:** `nvidia-smi` reports driver **>= 580**; GPU compute capability >= 8.0;
`prototypes/cutile && make` succeeds (do the setup in section 2 first).

Why the driver: on driver 565 `cuModuleLoadData` of a CUDA-13 cubin returns
`CUDA_ERROR_INVALID_IMAGE`. Compilation is unaffected, only loading.

**Before anything else:** phase 3c already took a tile task from Java to a cubin on a box with
driver 565, stopping only at `cuModuleLoadDataEx`. So on a box with R580+ your first job is not
to build anything - it is to run the section 5b command and see how much of phase 2 is already
done. Expect to need `CUDATileScheduler` and `CUDATileInstalledCode` (section 5b, last
paragraph) before a task works without pinning `setLocalWork(1, 1, 1)` by hand.

**Steps**

1. Confirm the blocker is actually gone, with the fastest possible check: run the example in
   section 5b. If `cuModuleLoadDataEx failed: device kernel image is invalid` is gone, the
   driver is new enough and **the tile path should run end to end immediately** - everything
   before that line already works. If you want an even smaller check first, load
   `prototypes/cutile/generated_vectoradd.cubin` (TornadoVM's own output, committed here) with
   `cuModuleLoadData` and launch it with `grid=(16,1,1)`, `block=(1,1,1)`, `sharedMemBytes=0`;
   a few lines of ctypes against `libcuda.so` is enough.
2. The payload offset is `TornadoNativeArray.ARRAY_HEADER`, which is **16** in this tree (the
   emitted source in section 5b shows `ul_0 + 16`). The plugin already reads the constant, so
   nothing to change there; only the standalone `.cu` files in `prototypes/cutile` hardcode it.
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
