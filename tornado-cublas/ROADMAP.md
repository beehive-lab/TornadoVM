# cuBLAS Hybrid API — Coverage Roadmap & Progress

Living document tracking cuBLAS API coverage for TornadoVM library tasks, derived from
the [cuBLAS documentation](https://docs.nvidia.com/cuda/cublas/#using-the-cublas-api).
Each feature lists the design, the extension points it exercises, and the test that
gates it. Status: `[x]` done / `[~]` in progress / `[ ]` planned / `[-]` not planned.

---

## Current status (2026-07-01, branch `hybrid`)

- [x] Library-task runtime: ServiceLoader SPI (`TornadoLibraryProvider`), device-buffer
      reuse, standard LAUNCH pipeline, per-(plan, device) contexts, teardown on plan close
- [x] `cublasSgemv` FP32 (incl. `beta != 0` → READ_WRITE outputs) — `TestCuBlasSgemv`,
      `TestCuBlasSgemvBeta`
- [x] `cublasSgemm` FP32 — `TestCuBlasSgemm`
- [x] Mixed JIT + library graphs (pre/post tasks) — `TestCuBlasSgemvWithTornadoVMTasksPOST` (MVP, 10/10)
- [x] CUDA Graph capture/replay of library tasks (context creation hoisted pre-capture,
      capture-safe dispatch) — `TestCuBlasSgemvWithTasksCudaGraph`
- [x] Profiler `TASK_KERNEL_TIME` for library calls (host-timed; disabled during capture)
- [x] Benchmarks: `BenchmarkSgemm` (naive/tiled/cuBLAS FP32/TF32: 51 / 82.6 TFLOP/s at 4096,
      8.8x / 14.2x vs tiled), `MatrixVectorRowMajorWithCuBlas` (SGEMV: 578 GB/s, 1.4x vs
      best hand-written kernel, 74x vs sequential Java)
- [x] I1 dispatch registry, I2 `CuBlasOptions` via `withTuning`, I3 workspace
      (`TestCuBlasWorkspace`), A1 TF32 math mode (`TestCuBlasSgemmTF32`)
- [x] C1/C2 GemmEx FP16 in / FP16 or FP32 out, FP32 Tensor Core accumulate
      (`TestCuBlasGemmExFP16`): 160.6 TFLOP/s at 4096, 27.4x vs tiled KernelContext
- [x] D1 `cublasSgemmStridedBatched` (LibraryTask16-18 arities added to tornado-api)
- [x] G5 JUnit suite in tornado-unittests (`cublas.TestCuBlas`, 10 tests, auto-skip
      without CUDA backend/libs), registered in `tornado-test`

---

## How a feature is added (the modular recipe)

Adding a cuBLAS function touches **only the `tornado-cublas` module pair** — never the
core runtime:

1. **Factory** in `CuBlas.java`: builds the `LibraryTaskDescriptor` (function name,
   `Object[]` params, `Access[]` — outputs `WRITE_ONLY`, or `READ_WRITE` when read back,
   e.g. `beta != 0`).
2. **Dispatch case** in `CuBlasLibraryProvider.dispatch`: positional marshalling from
   `LibraryInvocation` (device pointers for arrays, boxed scalars) to the JNI call.
3. **JNI wrapper** in `cublas-jni/src/main/cpp/source/tornado-cublas.cpp` + native
   declaration in `CuBlasNativeLib.java`: thin, stateless, returns `cublasStatus_t`.
4. **Test** in `uk.ac.manchester.tornado.cublas.tests`: validate vs a sequential Java
   reference (pattern: `TestCuBlasSgemv`), plus a CUDA Graph variant when relevant.

Planned infra refactor to keep step 2 from growing into a mega-switch:

- [x] **I1 — Dispatch registry**: replace the `switch` in `CuBlasLibraryProvider` with a
      `Map<String, CuBlasCall>` (`@FunctionalInterface CuBlasCall { int invoke(long handle, LibraryInvocation inv); }`).
      One map entry per function; unknown names keep the clear error message.
      *Test:* existing tests unchanged (pure refactor gate).
- [x] **I2 — Per-call options**: add an opaque `withTuning(Object)` field to
      `LibraryTaskDescriptor` (tornado-api stays library-agnostic); `tornado-cublas`
      defines a typed `CuBlasOptions` (math mode, compute type, algo, workspace size)
      that factories accept and `dispatch` consumes.
      *Test:* `TestCuBlasSgemmTF32` (below) is the first consumer.
- [x] **I3 — Workspace hook**: implemented as a context-level, grow-only workspace
      (`CuBlasOptions.withWorkspace` -> cudaMalloc in cublas-jni -> `cublasSetWorkspace`,
      freed with the context). The SPI-level `LibraryInvocation.allocateWorkspace` via
      `BufferProvider` is deferred to D2 (first consumer needing TornadoVM-managed scratch).
      *Test:* `TestCuBlasWorkspace` — sgemm with a 32 MiB user workspace over 5 executions.

---

## Track A — Handle configuration (foundation for everything below)

| # | Feature | cuBLAS API | Design | Test | Status |
|---|---|---|---|---|---|
| A1 | Math modes (TF32, pedantic) | `cublasSetMathMode`, `CUBLAS_TF32_TENSOR_OP_MATH`, `CUBLAS_PEDANTIC_MATH` | `CuBlasMathMode` enum in `CuBlasOptions` (I2); applied per call: set → call → restore; `CuBlas.cublasSgemmTF32` convenience factory | `TestCuBlasSgemmTF32`: 82.6 TFLOP/s at 4096 on RTX 4090 (14.2x vs tiled KernelContext), max rel error ~1e-4; TF32 row in `BenchmarkSgemm` | [x] |
| A2 | Workspace control | `cublasSetWorkspace` (256-byte aligned) | I3; per-context grow-only workspace (cudaMalloc, 256B-aligned) | `TestCuBlasWorkspace` | [x] |
| A3 | Pointer mode DEVICE | `cublasSetPointerMode(CUBLAS_POINTER_MODE_DEVICE)` | Scalars as 1-element TornadoVM arrays (device pointers). Two uses: (a) alpha/beta produced by a preceding JIT task; (b) CUDA Graphs with per-replay-varying scalars (HOST mode bakes values into the capture) | `TestCuBlasSgemvDeviceScalars`: pre-task writes alpha into a `FloatArray(1)` → sgemv consumes it; under `withCUDAGraph`, change alpha between replays and assert the new value is used | [ ] |
| A4 | Version/property query | `cublasGetVersion` | Log at context creation (debug flag); guard features by version | covered by any test with debug on | [ ] |
| A5 | Atomics mode | `cublasSetAtomicsMode` | Only affects symv/hemv; expose in `CuBlasOptions` when symv lands (E track) | with E3 | [ ] |

## Track B — Level-3 completion (FP32/FP64)

Each entry = 1 factory + 1 registry entry + 1 JNI wrapper + 1 test (recipe above).
Java reference implementations are the test oracle.

| # | Function | Types | Notes | Test | Status |
|---|---|---|---|---|---|
| B1 | `cublasDgemm` | `DoubleArray` | Trivial clone of Sgemm; needs FP64-capable device (all NVIDIA) | `TestCuBlasDgemm` (tolerance 1e-9) | [ ] |
| B2 | `cublasSgemmStridedBatched` | `FloatArray` | See Track D | `TestCuBlas#testSgemmStridedBatched` | [x] |
| B3 | `cublasStrsm` | `FloatArray` | Triangular solve; side/uplo/diag enums (`CuBlasSideMode`, `CuBlasFillMode`, `CuBlasDiagType` — port fill mode from prototype) | `TestCuBlasStrsm`: solve vs Java forward/back-substitution | [ ] |
| B4 | `cublasSsyrk` | `FloatArray` | Rank-k update; output is triangular part only — validate only referenced triangle | `TestCuBlasSsyrk` | [ ] |
| B5 | `cublasSgeam` | `FloatArray` | Element-wise C = αop(A) + βop(B); doubles as an on-device transpose (α=1, β=0, OP_T) — useful utility for the row/column-major seam | `TestCuBlasSgeam` + transpose sub-case | [ ] |
| B6 | `cublasSdgmm` | `FloatArray` | Diagonal scaling; trivial | `TestCuBlasSdgmm` | [ ] |
| B7 | Batched LAPACK-ish: `getrfBatched`/`getriBatched`/`gelsBatched` | `FloatArray` | Needs device pointer-array support (D2 infra) | `TestCuBlasGetrfBatched`: LU of a batch of small matrices vs Java | [ ] |

## Track C — Mixed precision: the `GemmEx` family (LLM payoff)

`cublasGemmEx` takes `cudaDataType` per operand + `cublasComputeType_t` + `cublasGemmAlgo_t`.
Bind it **once, generically**, then expose typed factories per supported TornadoVM type.

Infra: port `CudaDataType`/`CublasComputeType`/`CublasGemmAlgo` enums from the prototype
(`tornadovm-ee/tornado-cublas/.../enums/`); one JNI wrapper
`cublasGemmEx(handle, ..., long dA, int aType, ..., int cType, int computeType, int algo)`.

| # | Feature | TornadoVM types | Constraints (from docs) | Test | Status |
|---|---|---|---|---|---|
| C1 | GemmEx FP16 in / FP16 out, compute 32F | `HalfFloatArray` | Tensor Cores; alignment concern did NOT materialize: 160.6 TFLOP/s at 4096 (RTX 4090 FP16 peak) despite the +24B header offset | `TestCuBlasGemmExFP16` (vs Java reference from same FP16-rounded inputs); FP16 row in `BenchmarkSgemm`: 27.4x vs tiled KernelContext | [x] |
| C2 | GemmEx FP16 in / FP32 out | `HalfFloatArray` A,B + `FloatArray` C | Common inference config (`CuBlas.cublasGemmExFP16FP32`) | part of `TestCuBlasGemmExFP16` | [x] |
| C3 | GemmEx INT8 (8I in, 32I/32F out, compute 32I) | `Int8Array` / `ByteArray` A,B + `IntArray`/`FloatArray` C | Dims multiple-of-4 constraints for INT8; validate vs Java int accumulation | `TestCuBlasGemmExInt8` | [ ] |
| C4 | GemmEx BF16 | — | **Blocked: no BF16 array type in tornado-api.** Add `BFloat16`/`BFloat16Array` first (separate tornado-api work item) | `TestCuBlasGemmExBF16` | [ ] |
| C5 | GemmEx FP8 (E4M3/E5M2) | — | **Blocked: no FP8 type** (prototype had one; port needs the tensor-type track). Mandatory 16-byte alignment for all pointers/lds | `TestCuBlasGemmExFP8` | [ ] |
| C6 | Algo selection | `CublasGemmAlgo` in `CuBlasOptions` | `CUBLAS_GEMM_DEFAULT` default; `AUTOTUNE` is **not stream-capture safe** → provider must reject it when `insideCaptureRegion` (needs capture flag surfaced through `LibraryInvocation`) | `TestCuBlasGemmExAlgo`: run all valid algos, same result | [ ] |

## Track D — Batched GEMM (attention/transformer batches)

| # | Feature | Design | Test | Status |
|---|---|---|---|---|
| D1 | `cublasSgemmStridedBatched` (+`Ex` variant) | One flat `FloatArray` per operand + long strides + batchCount; needed LibraryTask16-18 arities in tornado-api | `TestCuBlas#testSgemmStridedBatched` (JUnit): batch of 8 128x128 GEMMs vs Java loop | [x] |
| D2 | `cublasSgemmBatched` (pointer arrays) | Needs a device array of pointers: provider computes `base + i*stride` (or per-array offsets) into a small device buffer via the workspace hook (I3) + JNI `cuMemcpyHtoD`. Introduces the "provider-owned scratch" pattern that B7 and cuBLASLt reuse | `TestCuBlasSgemmBatched` | [ ] |
| D3 | `gemvStridedBatched` | Same shape as D1 | `TestCuBlasSgemvStridedBatched` | [ ] |

## Track E — Level-1 / Level-2 menu

Two sub-groups by result location:

**E-a) Vector-output functions** — same pattern as gemv, straightforward:

| # | Functions | Test oracle | Status |
|---|---|---|---|
| E1 | `saxpy`, `sscal`, `scopy`, `sswap` | Java loops | [ ] |
| E2 | `sger` (rank-1 update; A is READ_WRITE) | Java loops | [ ] |
| E3 | `ssymv`, `strmv`, `strsv` | Java loops (+A5 atomics toggle for symv) | [ ] |

**E-b) Scalar-output functions** (`sdot`, `snrm2`, `sasum`, `isamax`) — the interesting
design case: with `CUBLAS_POINTER_MODE_HOST` these **synchronize the stream and break
CUDA Graph capture** (documented cuBLAS constraint). Design: always use
`CUBLAS_POINTER_MODE_DEVICE` for results — the result parameter is a 1-element
TornadoVM array (`FloatArray(1)` / `IntArray(1)`, `WRITE_ONLY`), resolved to a device
pointer like any other buffer. Stays async, graph-capturable, and the value flows to
the host through the normal `transferToHost`.

| # | Functions | Test | Status |
|---|---|---|---|
| E4 | `sdot`, `snrm2`, `sasum` (device-pointer results) | `TestCuBlasDot`: dot into `FloatArray(1)`, consumed by a post JIT task *and* under CUDA Graph capture | [ ] |
| E5 | `isamax`/`isamin` (int result, 1-based index — document!) | `TestCuBlasAmax` | [ ] |

## Track F — cuBLASLt (epilogue fusion; separate provider)

Different enough to be its own provider (`"nvidia/cublaslt"`) inside the same module
pair (`CuBlasLtLibraryProvider`, JNI in the same `libtornado-cublas.so`, which already
transitively loads `libcublasLt`). Exercises everything the SPI reserved:

- **Context**: `cublasLtHandle_t` + heuristics cache of `(shape, types, epilogue) → algo`
  in the per-(plan, device) `LibraryContext` (this is *why* contexts are per-plan).
- **Workspace**: `cublasLtMatmul` wants an explicit workspace (recommend ≥32 MiB on
  Ada/Hopper) → first hard consumer of I3.
- **Descriptors**: `MatmulDesc`/`MatrixLayout` created per unique shape, cached in
  context, destroyed in `destroyContext`.

| # | Feature | Test | Status |
|---|---|---|---|
| F1 | `ltMatmul` FP32/FP16 plain (parity with GemmEx, heuristic algo) | `TestCuBlasLtMatmul`: vs sgemm results | [ ] |
| F2 | Epilogue `BIAS` (fused C = A*B + bias) | `TestCuBlasLtMatmulBias`: vs sgemm + Java bias add | [ ] |
| F3 | Epilogue `GELU_BIAS` / `RELU_BIAS` | `TestCuBlasLtMatmulGelu`: vs unfused reference (tolerance for tanh-approx GELU); benchmark vs sgemm + separate JIT activation task — the headline fusion number | [ ] |
| F4 | FP8 matmul (needs C5 types) | `TestCuBlasLtFP8` | [ ] |

## Track G — Robustness / long tail

| # | Item | Notes | Status |
|---|---|---|---|
| G1 | 64-bit interface (`cublas*_64`) | TornadoVM segments can exceed INT_MAX elements; switch to `_64` entry points wholesale (int64 dims) rather than dual-binding | [ ] |
| G2 | Graph-capture guard surfaced to providers | `LibraryInvocation.isCapturing()` so providers can reject capture-unsafe options (C6 AUTOTUNE, E-b host pointer mode) with a clear error instead of a corrupted capture | [ ] |
| G3 | Multi-device | One context per device already; needs TornadoVM-level multi-device task graphs. `cublasXt` is **not** the path (host-pointer API conflicts with device-buffer model) | [-] |
| G4 | Legacy cuBLAS API / emulation modes (`CUBLAS_EMULATE_*`) | Env-var driven, work without binding changes; document only | [-] |
| G5 | tornado-test harness integration | `uk.ac.manchester.tornado.unittests.cublas.TestCuBlas` (10 JUnit tests, registered in `tornado-test`); skips via JUnit assumption when the default device is not CUDA or libtornado-cublas is missing. main()-runners kept as documented examples | [x] |

---

## Suggested execution order

1. **I1 + I2 + I3** (registry, options, workspace) — everything else hangs off these
2. **A1 + C1/C2 + C6** (TF32 + FP16 GemmEx) — biggest LLM-inference win available today
3. **D1** (strided-batched) — cheap, high value, great CUDA Graph story
4. **F1–F3** (cuBLASLt + fused epilogues) — the flagship feature
5. **E4/E5 + A3** (device-scalar pattern) — unlocks reductions and dynamic scalars
6. **B track + D2 + G1** as demand dictates

## Test conventions

- Every function: `main()`-runnable `TestCuBlas<Fn>` validating vs sequential Java
  (`Result is correct` / `Result is wrong` output; non-zero tolerance documented per type:
  FP32 1e-2 rel for large K, FP16 1e-1, FP64 1e-9).
- Every feature that changes dispatch/context behavior: a CUDA Graph variant
  (pattern: `TestCuBlasSgemvWithTasksCudaGraph`).
- Perf-relevant features: a row in `BenchmarkSgemm` / a dedicated benchmark with
  GFLOP/s / GB/s reporting (pattern: `MatrixVectorRowMajorWithCuBlas`).
- Column-major seam: any new matrix function documents its row-major calling convention
  in the factory javadoc (see `cublasSgemv` — `sgemv(OP_T, n, d, lda=n)` for row-major
  `W(d×n)`).
