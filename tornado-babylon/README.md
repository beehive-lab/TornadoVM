# tornado-babylon: Babylon code models through TC (spike)

Spike on branch `spike/babylon`, not part of the TC pull request. It asks what TornadoVM needs to
accept Babylon (`jdk.incubator.code`) programs, and answers with code that runs:

1. **A TC frontend from Babylon code models.** `BabylonFrontend` (a `ModelFrontend` service) turns
   a `@Reflect` method's code model (lowered, SSA) into TC IR. The rest of TC (specialisation,
   ABI, optimisation, structuring, CUDA/OpenCL emission) is unchanged.
2. **TornadoVM kernels written as `@Reflect` methods.** With `-Dtornado.tc.frontend=babylon`, a
   plain TornadoVM task (`KernelContext`, `FloatArray`, shared memory, barriers, helper methods) is
   compiled from its code model rather than its bytecode. The CUDA it gives is the same as the
   bytecode frontend's, character for character.
3. **A HAT backend.** `TornadoHatBackend` implements HAT's `hat.backend.Backend`, so unmodified
   HAT programs (`Accelerator`, `ComputeContext.dispatchKernel`, `F32Array`, ...) run on TornadoVM
   devices (CUDA, OpenCL; Metal untested), compiled by TC.

Nothing here imports Graal or JVMCI, and no native code is involved: HAT's own backends are not
used. TC is the only compiler.

## How it fits

```
HAT program ──Accelerator──▶ TornadoHatBackend.dispatchKernel
                              │  NDRange → WorkerGrid, HAT buffers → ByteArray (+ bound layouts)
                              ▼
                     TaskGraph("hat").task("t", <kernel as TaskN>, args) → TornadoVM runtime
                              │
TornadoVM program ───────────▶│ TcHook → TcCompiler.compileJava
                              ▼
                 Frontend.translateWithCallees ── asks each ModelFrontend first
                              │                       └─ BabylonFrontend: Op.ofMethod → lower → SSA
                              │                          → BabylonTranslator → TC IR (kernel + callees)
                              ▼
                 TC pipeline (unchanged) → CUDA C / OpenCL C → NVRTC / clBuildProgram
```

## What TC needed (the answer to the spike)

Only four small changes to TC; everything else is in this module.

| Change | Where | Why |
|---|---|---|
| `ModelFrontend` service interface, consulted before the bytecode frontend | `tc-frontend` (`ModelFrontend`, `Frontend.translateWithCallees`, `module-info` `uses`) | Lets a frontend that is not bytecode-based supply the IR module. TC itself does not depend on `jdk.incubator.code`, so it still builds on JDK 21. |
| `BytecodeSource.loader()` | `tc-frontend` | A model frontend needs the kernel's `Class`, not only its bytes. |
| Public helpers: `typeOf`, `kernelName`, `calleeName`, `translateCallee`, `apiField`, `apiCall` | `tc-frontend` `Frontend` | Names and types match the bytecode frontend's exactly. Non-`@Reflect` callees come from bytecode. TornadoVM API calls (native arrays, `KernelContext`, `TornadoMath`, boxing) are lowered by the same binding table. |
| Structurizer: a condition passed as a block argument by a plain `br` | `tc-lower` `Structurizer.isInline` (+ lit test `Structure/bool-args.tcir`) | Babylon lowers `a && b` to an `i1` block argument. The bytecode frontend never produced that shape, and TC crashed (TC-E9003) on it. |

What the Babylon side needed, in `BabylonTranslator`:

- **Types.** `hat.KernelContext` maps to TornadoVM's `KernelContext`, a HAT buffer to a `ByteArray`, and `hat.types.F16` to `f16`.
- **Ops.**
  - Arithmetic, comparisons and conversions.
  - Java array accesses.
  - Static final fields, folded to constants.
  - Branches.
  - Static calls, which become TC device functions. `@Reflect` callees come from their models, the others from bytecode.
- **HAT's `KernelContext`.** `GIX…`, `LIX…`, `BIX…`, `GSX…`, `LSX…`, `BSX…` and `barrier()` become TC intrinsics.
- **HAT buffers.** Accessor calls (`array(i)`, `array(i, v)`, `length()`, `width()`, ...) become loads and stores at the offsets of the buffer's *bound* `GroupLayout`. The backend records each layout per kernel parameter (`HatLayouts`) before compilation, and callees inherit it from their call sites.
- **Structs in buffers.** `input.complex(k).real()` becomes a view: buffer, byte offset and layout. It has no IR value of its own.
- **HAT local and private memory.** `T.createLocal()` / `T.createPrivate()` become a shared or private `alloca`, sized from `T.deviceSchema`, which is read reflectively.
- **Half floats.** `F16.add/sub/mul/div` compute in f32 and round back, as TC already does for `HalfFloat`. `f16ToFloat`, `floatToF16`, `of` and `value()` are handled too, including `value(short)` stores into local and private memory.
- **`HATMath`.** `cosf`, `native_cosf`, `sqrtf`, `maxf` and the rest map to TC's `math.*` intrinsics at the argument's precision, as HAT's own backends map them. Compiling their Java bodies would compute in double through `Math.cos`, which needs fp64.
- **Widths.** Sub-int fields and elements widen to int on load and narrow on store, as on the JVM stack.

What the HAT backend needed (`TornadoHatBackend`):

- **Compute methods.** `dispatchCompute` runs the compute method as plain Java, the same way HAT's Java backends do.
- **Tasks.** `dispatchKernel` builds a one-task graph. The kernel becomes a serializable `TaskN` lambda made at run time with `LambdaMetafactory` and defined in the kernel's own class, so private kernels work. TornadoVM then resolves it like any method reference.
- **Buffers.** HAT segments are copied into `ByteArray`s and back. A TornadoVM array carries its own 16-byte header, so a HAT segment cannot be wrapped in place: `fromSegmentShallow` would overwrite HAT's data with the length.
- **Launch shape.** `NDRange` global and local sizes become a `WorkerGrid1D/2D/3D`. The device is chosen with `-Dhat.t.device=<driver>:<device>`.
- **Plan cache.** Each kernel, buffer set, scalar set and `NDRange` gets one execution plan, compiled on the first dispatch and launched on every later one. `-Dtornado.hat.strictFP=true` makes the plans use strict floating point.

## Build and run

Needs:

- a Babylon JDK, at `$BABYLON/build/linux-x86_64-server-release/images/jdk`;
- HAT built in `$BABYLON/hat/build`;
- a TornadoVM SDK from this branch (`make jdk22plus BACKEND=opencl,cuda`).

`env.sh` picks the Babylon JDK. Override it with `BABYLON=` / `BABYLON_JDK=`.

```bash
source ../setvars.sh                      # the TornadoVM SDK of this branch
./build.sh                                # build/tornado-babylon.jar + build/examples

# A HAT program on TornadoVM (CUDA device 1:0; OpenCL is 0:0 / 0:1 here):
H=$HOME/src/babylon/hat/build
./run.sh -d 1:0 $H/hat-example-squares-1.0.jar squares.Main
./run.sh -d 1:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=2DTILING --check --size=256

# Every HAT example on three devices, validated:
./hat-sweep.sh                            # → build/hat-sweep.txt, logs in build/hat-sweep/

# TornadoVM @Reflect kernels from their code models:
source ./env.sh
tornado --jvm="--enable-preview --add-modules=jdk.incubator.code -Dtornado.tc.frontend=babylon -Dtornado.tc.verbose=true" \
        -cp build/tornado-babylon.jar:build/examples reflect.ReflectKernels
```

`run.sh` passes `-Dtornado.recover.bailout=False`, so a kernel that TC cannot compile fails
loudly instead of running as Java. Pass extra JVM flags in `TC_JVM`, for example
`TC_JVM=-Dtornado.printKernel=true`.

`-Dtornado.tc.frontend` takes three values:

- `auto` (the default) uses code models only for `@Reflect` methods with HAT parameter types;
- `babylon` uses them for every `@Reflect` kernel;
- `bytecode` turns the Babylon frontend off.

## Results (RTX 4090 CUDA 1:0, RTX 4090 OpenCL 0:0, Intel UHD 770 OpenCL 0:1)

| example / kernel | 4090 CUDA | 4090 OpenCL | UHD 770 OpenCL |
|---|---|---|---|
| `squares` | PASS | PASS | PASS |
| `mandel` | PASS | PASS | PASS |
| `matmul-MT` | PASS | PASS | PASS |
| `matmul-1D` | PASS | PASS | PASS |
| `matmul-1DFC` | PASS | PASS | PASS |
| `matmul-2D` | PASS | PASS | PASS |
| `matmul-2DLI` | PASS | PASS | PASS |
| `matmul-2DTILING` | PASS | PASS | PASS |
| `matmul-2DREGISTERTILING` | PASS | PASS | PASS |
| `matmul-2DREGISTERTILING_V` | FAIL | FAIL | FAIL |
| `matmul-2DLIF16` | FAIL | FAIL | FAIL |
| `matmul-2DREGISTERTILING_FP16` | PASS | FAIL | FAIL |
| `matmul-FP16-strictFP` | PASS | PASS | PASS |
| `dft` | PASS | PASS | PASS |
| `tensors` | FAIL | FAIL | FAIL |

`ReflectKernels` (TornadoVM `@Reflect` kernels through the Babylon frontend): PASS on all three devices, CUDA byte-identical to the bytecode frontend's.

The fails: `2DREGISTERTILING_V` (Float4 views, not mapped), `tensors` (HAT warp API, not mapped), `2DLIF16` (the example's check compares F16 against fp32 at 0.01; HAT's own CUDA backend fails it too), and OpenCL `2DREGISTERTILING_FP16` with default flags (`-cl-mad-enable` contracts before rounding to half; passes with strict FP, row `matmul-FP16-strictFP`).

## Performance (matmul 1024², `./perf.sh` and `./perf-nsys.sh`)

Median ms per `compute()` (second half of 50 iterations; includes copies and transfers) and kernel-only µs (nsys, mean of 30 launches).

| kernel | TVM CUDA ms | TVM OpenCL ms | HAT CUDA ms | HAT OpenCL ms | TC kernel µs | HAT-CUDA kernel µs |
|---|---|---|---|---|---|---|
| `1D` | 20.224 | 20.277 | 37.593 | 393.889 | 17878.8 | 36785.4 |
| `2D` | 4.266 | 4.653 | 2.917 | 351.447 | 1787.7 | 1792.2 |
| `2DLI` | 2.899 | 3.237 | 1.537 | 65.249 | 426.3 | 444.1 |
| `2DTILING` | 2.856 | 3.270 | 1.403 | 28.991 | 331.3 | 332.5 |
| `2DREGISTERTILING` | 2.586 | 2.852 | 1.121 | 11.059 | 79.1 | 76.3 |
| `2DREGISTERTILING_FP16` | 1.485 | 1.657 | 0.707 ✗ | 7.557 | 215.9 | 59.0 |

Kernel for kernel, TC matches HAT's CUDA on fp32 (0.97–1.04×; 2× faster on 1D). The end-to-end gap is the per-dispatch buffer copies. FP16 is 3.7× slower because TC keeps Java's round-after-every-operation semantics (✗ = the example's own check fails).

How the results are validated:

- **Examples with `--check`** (matmul, dft, tensors) compare against sequential Java inside the example.
- **mandel and squares** are diffed against the output of HAT's own CUDA backend.
- **matmul 2DLIF16** says "Result is wrong!" on HAT's own CUDA backend too. Its check compares an F16 product against an fp32 reference with an fp32 tolerance, so this is the example's check failing, not this backend.
- **matmul 2DREGISTERTILING_FP16** passes here. HAT's native CUDA backend reports "wrong" on the same run.

## Gaps found (not done in the spike)

- **`Float4` / `float4View` vector views** (matmul `2DREGISTERTILING_V`): needs mapping HAT vector types to TC vector types.
- **Tensor-core / warp APIs** (`WRS…`, tensors `mxmTensorsCM`): TC has MMA intrinsics for TornadoVM's tile API, but HAT's warp API is not mapped.
- **Half-precision speed.** HAT's Java semantics for `F16.mul(a, b)` are `F16.of(f16ToFloat(a) * f16ToFloat(b))`: widen to f32, compute, round back to half after every operation. TC emits exactly that (`__float2half_rn((float) a * (float) b)`), so its results are bit-identical to the sequential Java reference, and the example's 0.01 check passes. HAT's native CUDA emits `__half` arithmetic directly (`acc.value + privA.value * privB.value`), which runs on the half units without conversions: 3.7× faster kernel time on matmul FP16. Its result does not match Java (the check fails), most likely because the multiply and add are contracted into one `hfma` with a single rounding; that is not verified here. A fast-math mode in TC could emit native half ops.
- **Instance calls on user objects, lambdas, and `switch` patterns in kernels.** HAT kernels do not use them; the bytecode frontend handles them.
- **Buffers are copied, not shared.** Every dispatch copies HAT buffers in and out. HAT's own backends track buffer state to skip copies (`minimizeCopies`). Zero-copy would need a TornadoVM array type with an external header, or HAT buffers allocated with TornadoVM's header.
- **One task per graph per dispatch.** A compute method's kernels could be batched into one task graph (and one CUDA graph), but that needs the compute model's buffer-tracking hooks.
- **No HAT `Config` flags** (`showCode`, `minimizeCopies`, ...) are honoured yet.
