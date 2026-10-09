# Backend contracts — DRAFT for review at gates C2/C3

A TC backend is **data plus a printer**, not a compiler tier. Everything that can be shared —
legality checks, lowering, structuring, expression folding — lives in the core.

## 1. `TargetCaps` (record, immutable, part of the cache key)

| Field | Example (RTX 4090, CUDA) | Used by |
|---|---|---|
| `dialect` | `CUDA` / `OPENCL` / `METAL` | codegen |
| `computeCapability` | 8.9 | MMA, tile, CDP checks |
| `fp64`, `fp16`, `bf16`, `fp8` | true / true / true / true | legality (`TC-E4xxx`) |
| `maxWorkGroupSize`, `maxLocalMemoryBytes`, `warpSize` | 1024, 48 KiB (static), 32 | local-memory sizing, reductions |
| `schedule` | `PER_ITERATION` (GPU) / `PER_BLOCK` (CPU) | parallel loop lowering |
| `arrayHeaderBytes` | 16 | ABI lowering |
| `toolchain` | NVRTC 12.6, nvcc 12.6 | feature gates, cache key |
| `nativeAtomics` | int/long/float add, CAS | atomics lowering |

Built from the existing driver device queries; TC never queries devices itself.

## 2. Intrinsic registry

One table (Java records) keyed by TC op. Each row: semantics notes, operand/result types, and a
spelling per dialect plus `llvm` and `mlir` columns for `tc-interop`. Example rows:

| TC op | CUDA | OpenCL C | MSL |
|---|---|---|---|
| `tornado.thread_id.global.x` | `blockIdx.x*blockDim.x+threadIdx.x` | `get_global_id(0)` | `_thread_position_in_grid.x` (ABI parameter) |
| `tornado.barrier.local` | `__syncthreads()` | `barrier(CLK_LOCAL_MEM_FENCE)` | `threadgroup_barrier(mem_flags::mem_threadgroup)` |
| `math.rsqrt` (f32) | `rsqrtf(x)` | `rsqrt(x)` | `rsqrt(x)` |
| `tornado.atomic.add` (i32, global) | `atomicAdd(p, v)` | `atomic_add(p, v)` | `atomic_fetch_add_explicit(p, v, memory_order_relaxed)` |

Spellings are taken from the vendor specifications (CUDA C++ Programming Guide, OpenCL C 3.0,
Metal Shading Language Specification), not from TornadoVM's Graal backends.

## 3. Dialect printer interface

```
sealed interface Dialect permits CudaDialect, OpenClDialect, MetalDialect
  kernelHeader(SKernel, TargetCaps)        -> text   // entry modifiers + 4 ABI regions + params
  typeName(Type, AddressSpace)             -> text
  constant(Const)                          -> text   // e.g. infinity spelling per dialect
  intrinsic(IntrinsicRow, args)            -> text
  sharedDecl(alloca.shared)                -> text   // __shared__ / __local / threadgroup
  preamble(features used)                  -> text   // includes, half/bf16 headers, tile preamble
```

The C core printer handles statements, expressions, precedence and indentation; a dialect only
answers these questions. Golden tests run every dialect on the same TC-S input.

## 4. Driver hand-off

| Backend | Install entry | Compiler | Status |
|---|---|---|---|
| CUDA | `CUDADeviceContext.installCode(planId, meta, id, entry, source)` | NVRTC (tile: nvcc `-tilecubin`) | first target (C3, C5b) |
| OpenCL | `OCLDeviceContext.installCode(…)` source path | `clBuildProgram` | Phase 6 |
| Metal | Metal source-install path | Metal library compile | Phase 6 (no local device: golden + build only) |
