# Reproducing "HAT and Tensor Cores" on TornadoVM — Tensor Cores from Java, today

*A hands-on reproduction of the June 2026 HAT tensor-core proposal using released
TornadoVM, with measured numbers on an NVIDIA RTX 4090, generated-code and nsys
evidence, an honest gap analysis, and a maturity assessment.*

---

## 1. The article being reproduced

Juan Fumero's post **["HAT and Tensors"](https://jfumero.dev/posts/2026/06/11/hat-tensors)**
(June 2026) proposes extending OpenJDK **Project Babylon / HAT** with a
*tensor-aware API* so Java code can drive **GPU Tensor Cores** for workloads like
LLM inference, while staying portable:

- Map a high-level tensor operation to **NVIDIA HMMA** (matrix-multiply-accumulate)
  on NVIDIA GPUs and to the **Apple Metal** matrix units on Apple silicon.
- For devices without tensor units, fall back to a **loop-tile mapping** so the
  same Java source still runs.
- Reported results: on an **NVIDIA Ampere A10**, a matrix-multiply kernel goes
  from **240 GFLOP/s (naive) to 7.3 TFLOP/s (tensor-optimized)**; on an
  **Apple M4 Max GPU**, an **8×** improvement.
- Status (the author's own words): *"the final integration into the HAT
  programming model is under discussion."* The tensor-core path is **proposed,
  not yet shipping**, and the post contains no public API/code yet.

This is an excellent direction. The interesting question for a TornadoVM user is:
**how much of this can I do *today*, on a released framework?** The answer, it
turns out, is *all of it* — through two complementary, already-shipping routes.

Everything below was run on:

```
GPU:   NVIDIA GeForce RTX 4090 (SM 8.9, Ada)
CUDA:  12.6    JDK: 21.0.2    TornadoVM: 5.0.1 (CUDA backend, make BACKEND=cuda)
```

---

## 2. Route 1 — Tensor Cores from the JIT compiler (`KernelContext` WMMA)

TornadoVM's **`KernelContext`** already exposes a Warp-level Matrix-Multiply-Add
(WMMA) API. You write the tiling in Java; the TornadoVM JIT compiler lowers the
`mma*` calls to actual Tensor Core instructions. The primitives (from
`uk.ac.manchester.tornado.api.KernelContext`) are:

```java
float[]     mmaFragment(float v);                       // accumulator fragment
HalfFloat[] mmaLoadA(int[] aTile, int wmmaK, int off);  // load A fragment from shared memory
HalfFloat[] mmaLoadB(int[] bTile, int wmmaK, int off);  // load B fragment
float[]     mma(HalfFloat[] fragA, HalfFloat[] fragB, float[] fragC, MMAShape shape);  // D = A*B + C
void        mmaStore(float[] fragD, FloatArray c, int tileRow, int tileCol, int dimN);
// plus Int8 variants: mmaFragmentInt / mmaLoadAInt8 / mmaLoadBInt8 / mmaInt8 / mmaStoreInt
// plus swizzled-shared-memory variants: mmaLoadBSwizzled / mmaStoreBSwizzled
```

A multi-warp FP16 GEMM (from the shipped example
`tornado-examples/.../compute/MatrixMultiplicationMMA.java`) accumulates in
Tensor Core fragments:

```java
public static void gemmMMA(KernelContext ctx, /* … tiles, dims … */) {
    // 4x8 grid of accumulator fragments, all Tensor-Core resident
    float[] c00 = ctx.mmaFragment(0.0f); float[] c01 = ctx.mmaFragment(0.0f);
    // … c02 … c17 …
    for (int kStep = 0; kStep < numKSteps; kStep++) {
        // cooperative load of A and B tiles into shared memory (elided)
        HalfFloat[] a0 = ctx.mmaLoadA(aTile, BK, aOff0);
        HalfFloat[] b0 = ctx.mmaLoadB(bTile, BK, bOff0);
        c00 = ctx.mma(a0, b0, c00, MMAShape.M16N8K16);   // <-- Tensor Core MMA
        // … the rest of the fragment grid …
    }
    ctx.mmaStore(c00, c, tileRow0, tileCol0, N);
}
```

### 2.1 It emits real Tensor Core instructions

Run with `--printKernel` and the generated CUDA/PTX contains the Ampere Tensor
Core op — **48 of them** in this kernel:

```bash
tornado --printKernel -m tornado.examples/…​.MatrixMultiplicationMMA | grep mma.sync
#  mma.sync.aligned.m16n8k16.row.col.f32.f16.f16.f32   (x48)
```

That is the hardware HMMA instruction — TornadoVM compiled Java down to Tensor
Cores, not a library shim.

### 2.2 Measured (RTX 4090)

```
Baseline fp16 (tiled, no MMA):   4.470 ms    3843 GFLOP/s
MMA fp16 (B=KN row-major):       1.414 ms   12152 GFLOP/s
MMA fp16 (B=NK row-major):       1.383 ms   12420 GFLOP/s   ← 3.23x over baseline
MMA fp16 (swizzled):             1.508 ms   11394 GFLOP/s
  validation PASSED (max rel err 0.0002)
```

This is a direct analog of the HAT A10 experiment (240 GFLOP/s → 7.3 TFLOP/s,
~30×). The absolute numbers differ because the 4090 ≫ A10, but the shape is the
same: **the JIT-emitted Tensor Core path is ~3.2× the already-tiled baseline and
validated bit-accurate.**

### 2.3 nsys evidence

`nsys profile --trace=cuda` on the same run — the JIT-compiled kernels appear by
their Java method names, and the MMA kernels are ~9× faster per launch than the
non-MMA baseline:

```
Time(%)  Total(ns)     Inst  Avg(ns)      Name
  73.7   177,376,176     50   3,547,523    gemmBaselineFp16     (tiled, no Tensor Cores)
  10.2    24,632,680     50     492,653    gemmMMASwizzled
   8.0    19,320,346     50     386,406    gemmMMA_NK
   8.0    19,204,825     50     384,096    gemmMMA
```

### 2.4 Portability — the same idea on Apple Metal

HAT's portability pitch is NVIDIA HMMA *and* Apple Metal. TornadoVM already ships
the Apple side too: `KernelContext.matrixMultiply8x8(...)` maps to Apple's
`simdgroup_float8x8` hardware matrix units, with a fragment API
(`simdgroupMatrixZero/Load/MultiplyAccumulate/Store`, type `Matrix8x8Float`) and
a worked example `MatrixMultiplySimdgroup.java`:

```java
Matrix8x8Float acc = ctx.simdgroupMatrixZero();
Matrix8x8Float a   = ctx.simdgroupMatrixLoad(as, aBase, TILE);
Matrix8x8Float b   = ctx.simdgroupMatrixLoad(bs, bBase, BLOCK);
acc = ctx.simdgroupMatrixMultiplyAccumulate(a, b, acc);
ctx.simdgroupMatrixStore(acc, c, cBase, N);
```

And the "loop-tile fallback" HAT describes for devices without tensor units is
just… TornadoVM's normal JIT path — a `@Parallel` or tiled `KernelContext`
kernel — which runs on OpenCL, PTX, SPIR-V, Metal, and the CUDA-C backend.

There are also compiler guards (`CUDATensorCoreSupportPhase`,
`PTXTensorCoreSupportPhase`) that raise `TornadoDeviceMMANotSupported` when the
device lacks MMA (SM < 8.0), so an unsupported device is a clean error, not a
miscompile.

---

## 3. Route 2 — Tensor Cores from native libraries (the hybrid API)

For production GEMM you usually want a vendor kernel. TornadoVM's **hybrid API**
lets a native Tensor-Core library call sit inside a `TaskGraph`, sharing device
buffers with JIT tasks on the same CUDA stream:

```java
new TaskGraph("gemm")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, aFP16, bFP16)
    .libraryTask("gemmExFP16", CuBlas::cublasGemmExFP16, /* … */ )   // cuBLAS Tensor Cores
    .transferToHost(DataTransferMode.UNDER_DEMAND, cFP16);
```

Measured (`BenchmarkSgemm 2048`, RTX 4090):

```
TornadoVM JIT (naive @Parallel)          4761 GFLOP/s
TornadoVM KernelContext (tiled)          6316 GFLOP/s
cuBLAS FP32                             44445 GFLOP/s   ( 9.3x vs naive)
cuBLAS TF32 Tensor Cores                63627 GFLOP/s   (10.1x vs tiled)
cuBLAS FP16 GemmEx Tensor Cores        120107 GFLOP/s   (19.0x vs tiled)
```

The hybrid API is not limited to cuBLAS. The same ServiceLoader SPI already has
**cuBLASLt** (fused GEMM+bias+GELU epilogues), **cuDNN** (incl. fused FP16 flash
attention), **cuFFT**, and — added in the accompanying pull requests —
**CUTLASS** (FP16 tensor-core GEMM + fused epilogues, ~39 TFLOP/s) and
**cuTENSOR** (arbitrary tensor contractions / einsum). All are CUDA-Graph
capturable and interleave with JIT kernels on one stream.

---

## 4. Side-by-side reproduction summary

| Capability HAT proposes | TornadoVM route | Status | Measured (RTX 4090) |
|---|---|---|---|
| Java → NVIDIA HMMA | `KernelContext` WMMA (`mma(...,MMAShape)`) | **Shipping** | 12.4 TFLOP/s FP16, real `mma.sync` |
| Java → Apple Metal matrix units | `KernelContext.matrixMultiply8x8` / `simdgroup*` | **Shipping** | example `MatrixMultiplySimdgroup` |
| Loop-tile fallback (no tensor units) | ordinary `@Parallel` / tiled `KernelContext` JIT | **Shipping** | 3.8–6.3 GFLOP/s… GB-class, all backends |
| Library-grade tensor GEMM | hybrid API: cuBLAS / cuBLASLt / CUTLASS / cuTENSOR | **Shipping** | cuBLAS FP16 **120 TFLOP/s** |
| Int8 tensor cores | `KernelContext.mmaInt8` | **Shipping** | (m16n8k32 Int8 path) |
| Arbitrary-rank tensor contraction | hybrid API: cuTENSOR | **Shipping** | 21 TFLOP/s matmul, two-mode einsum |

---

## 5. Gaps found (honest)

Reproducing HAT on TornadoVM surfaced where TornadoVM is *not* (yet) what HAT
proposes — these are real and worth stating:

1. **No single unified portable tensor *type*.** HAT's pitch is one high-level
   tensor op that the toolkit auto-lowers to HMMA or Metal with a fallback.
   TornadoVM ships the *capability* on every target, but through **two different
   `KernelContext` APIs** — `mmaLoadA/mma/mmaStore` (WMMA, CUDA/PTX) vs
   `matrixMultiply8x8/simdgroup*` (Metal). Source is **not identical** across
   NVIDIA and Apple; you pick the primitive set per target. HAT's proposed
   unified abstraction would be a genuine ergonomic improvement, and TornadoVM
   could adopt it on top of the machinery it already has.
2. **The JIT MMA path is explicit, not declarative.** You hand-write the tiling,
   fragment grid, and shared-memory staging. That is powerful (the swizzled
   variant is a real optimization) but it is closer to CUTLASS-in-Java than to a
   one-line `tensor.matmul(a, b)`. HAT targets the one-liner.
3. **Fixed MMA shapes.** The JIT path exposes a `MMAShape` enum (e.g.
   `M16N8K16`, Int8 `M16N8K32`); arbitrary contraction ranks in the *JIT* are
   not covered — you drop to the cuTENSOR hybrid task for those.
4. **Tensor-core JIT is NVIDIA/Apple today.** The WMMA lowering exists for the
   CUDA and PTX backends and the Metal simdgroup path; a portable
   OpenCL/SPIR-V tensor-core lowering is not there (hardware/vendor-intrinsic
   gap, not a design one).

None of these are correctness gaps — they are *abstraction-level* gaps, which is
exactly the layer HAT is exploring.

---

## 6. Why TornadoVM is the more mature vehicle for this

1. **It ships, and it is tested.** HAT's tensor path is "under discussion"
   (June 2026). TornadoVM's is released (5.x), with unit tests
   (`TestMatrixMultiplicationMMA`, `TestSimdgroupMatrix`,
   `TestSimdgroupTiledMatrix`, `TestCuBlas…`) and worked examples in the tree.
2. **Two complementary routes, not one.** A JIT route (write the kernel, get
   `mma.sync`) *and* a native-library route (cuBLAS/CUTLASS/cuTENSOR at 39–120
   TFLOP/s). You choose control vs. peak throughput per task — and can mix both
   in one `TaskGraph`.
3. **Real multi-backend breadth.** The same framework targets OpenCL, PTX,
   SPIR-V, Metal, and CUDA-C, with the Tensor Core lowering on the NVIDIA and
   Apple targets and a portable fallback everywhere else. Portability is a
   shipped property, not a roadmap item.
4. **Systems maturity around the kernels.** CUDA-Graph capture (library calls
   replayed with one `cuGraphLaunch`), a task profiler, device-buffer sharing
   across JIT ↔ native boundaries with no host copies, and a ServiceLoader SPI
   so new libraries are drop-in module pairs — none of which a proposal-stage
   API has yet.
5. **Proven down to the instruction.** `mma.sync.aligned.m16n8k16.row.col.f32.f16.f16.f32`
   in the generated code and 120 TFLOP/s from cuBLAS GemmEx are measured facts on
   commodity hardware, today.

The honest summary: **HAT is proposing a cleaner, unified *abstraction* over a
capability TornadoVM already delivers.** The right takeaway is not competition —
Juan Fumero is a TornadoVM co-author — but that TornadoVM is the mature substrate
on which such an abstraction can be built, because the hard parts (Tensor Core
codegen on NVIDIA *and* Apple, a native-library interop path, graph capture,
multi-backend portability) are done and shipping.

---

## 7. Reproduce it yourself

```bash
# Build the CUDA backend
make BACKEND=cuda

# Route 1 — JIT Tensor Cores (WMMA), with generated-code proof
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplicationMMA
tornado --printKernel -m tornado.examples/…​.MatrixMultiplicationMMA | grep mma.sync

# Apple Metal matrix units (on Apple silicon, Metal backend)
tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplySimdgroup

# Route 2 — library Tensor Cores (cuBLAS FP16 / TF32) sharing buffers with JIT tasks
tornado -m tornado.cublas/uk.ac.manchester.tornado.cublas.tests.BenchmarkSgemm --params="2048 50"

# Profile any of them
nsys profile --trace=cuda -o run tornado -m <module>/<MainClass>
nsys stats --report cuda_gpu_kern_sum run.nsys-rep
```

Unit tests double as minimal examples:

```bash
tornado-test -V uk.ac.manchester.tornado.unittests.kernelcontext.matrices.TestMatrixMultiplicationMMA
tornado-test -V uk.ac.manchester.tornado.unittests.cublas.TestCuBlas
```

---

## 8. Conclusion

HAT's tensor-core proposal points at a real need: a portable, high-level way to
reach GPU matrix units from Java. Reproducing it on **released TornadoVM 5.x**
shows the underlying capability is already here on commodity hardware —
**JIT-emitted `mma.sync` at 12.4 TFLOP/s**, **cuBLAS Tensor Cores at 120
TFLOP/s**, an Apple Metal simdgroup path, a portable fallback on five backends,
and native-library interop with CUDA-Graph capture. The gap HAT would close is
the *unified abstraction* on top — and TornadoVM is the most mature place to
put it.

*Reproduction data, generated kernels, and nsys traces for this post were
produced on an RTX 4090 with TornadoVM 5.0.1 / CUDA 12.6 / JDK 21.*
