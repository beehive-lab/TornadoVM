# tornado-babylon: Babylon code models and HAT programs through TC

[Project Babylon](https://openjdk.org/projects/babylon/) gives Java *code reflection*: a method
annotated `@Reflect` carries its **code model** (`jdk.incubator.code`), a typed SSA form of its body
that tools can read and transform at run time. HAT (Heterogeneous Accelerator Toolkit) is Babylon's
GPU programming model built on it. This module lets TornadoVM consume both:

1. **A TC frontend from code models.** `BabylonFrontend` (a `ModelFrontend` service) turns a
   method's code model (lowered, SSA) into TC IR, and the rest of TC is unchanged (specialisation,
   ABI, optimisation, structuring, CUDA / OpenCL / CUDA Tile emission).
2. **TornadoVM kernels from their code models.** With `-Dtornado.tc.frontend=babylon`, any
   TornadoVM task whose method has a code model (`@Reflect`) is compiled from it rather than from
   its bytecode. The whole TornadoVM unit-test suite runs this way with the same results as the
   bytecode frontend (see [Testing](#testing)).
3. **A HAT backend.** `TornadoHatBackend` implements HAT's `hat.backend.Backend`, so unmodified HAT
   programs (`Accelerator`, `ComputeContext.dispatchKernel` / `dispatchTile`, `F32Array`, ...) run
   on TornadoVM devices, compiled by TC.

Nothing here imports Graal or JVMCI, and no native code is involved: HAT's own native backends are
not used. TC is the only compiler.

## How it fits

```
HAT program ──Accelerator──▶ TornadoHatBackend.dispatchKernel / dispatchTile
                              │  NDRange → WorkerGrid; HAT buffers → ByteArray in place (HeaderArena)
                              ▼
                     TaskGraph("hat").task("t", <kernel as TaskN>, args) → TornadoVM runtime
                              │
TornadoVM program ───────────▶│ TcHook → TcCompiler.compileJava
                              ▼
                 Frontend.translateWithCallees ── asks each ModelFrontend first
                              │                       └─ BabylonFrontend: Op.ofMethod → lower → (inline tile helpers)
                              │                          → mark @Parallel → SSA → BabylonTranslator → TC IR
                              ▼
                 TC pipeline (unchanged) → CUDA C / OpenCL C / CUDA Tile C++ → NVRTC / clBuildProgram / nvcc -tile
```

## What is supported

**Java, as the bytecode frontend compiles it.** Arithmetic, comparisons, conversions (with Java's
saturating float-to-int), `&&`/`||`/ternaries, loops (`for`, `while`, enhanced `for`, `break`,
`continue`), `switch`, static calls into `@Reflect` methods (translated from their models) and into
anything else (translated from bytecode: `TornadoMath`, `java.lang.Math` bodies, `Integer.bitCount`,
user helpers), calls on objects (vectors, collections) and enums (as ordinals), static final
constants, enum constants, fields of object parameters, Java arrays, `new T[n]` private arrays,
`Debug.printf`.

**The TornadoVM API.** Native arrays, `KernelContext` (thread indices, barriers,
`allocate*LocalArray`, atomics, warp votes, tensor-core MMA fragments under the bytecode frontend's
rules), `@Parallel` loops (code models do not keep annotations on locals: the frontend reads them from
the class file and marks the loop), `@Reduce`, `HalfFloat`, the vector types and vector collections
(including in-place updates of vector parameters), `AtomicInteger`, `TileContext` (CUDA Tile) and
dynamic parallelism (`KernelContext.launch` with a `static final DeviceKernel`).

**HAT.**

| HAT feature | How |
|---|---|
| `KernelContext.GIX()` … `BSZ()`, `barrier()` | TC thread intrinsics |
| Buffers (`S32Array`, `F32Array`, `F16Array`, …, user `Buffer`s such as `ComplexArray`) | loads and stores at the offsets of each buffer's *bound* layout |
| Structs in buffers (`input.complex(k).real()`) | a view (buffer, offset, layout) with no IR value of its own |
| `arrayView()` (1D and 2D), `localArrayView()`, `privateArrayView()` | Java arrays over HAT memory, indexed as HAT's backends index them (`arr[i][j]` is `array[j * stride + i]`) |
| Local and private memory (`createLocal()`, `createPrivate()`) | shared or private memory laid out from the type's `deviceSchema`: arrays, structs, arrays of structs (`DeviceLayouts`) |
| `F16`, `BF16` | `f16`, and bfloat16 as the bits in an int; arithmetic in f32 and rounded back exactly as the Java bodies round (bfloat16: to nearest even, in integer arithmetic) |
| `Float2`, `Float4` (`float4View`, `storeFloat4View`, `float4ArrayView`, lanes, `of`, `makeMutable`, `add`/`sub`/`mul`/`div`) | TC vectors; a mutated vector (`vA.x(v)`) lives in a private slot |
| `HATMath` (`maxf16`, `cosf`, `native_sinf`, …) | TC math intrinsics at the argument's precision |
| Tiles (`TileContext.load/store/index/shape/irange/BIDX`, `Tile.zeros/full/add/…/mma/transpose/sum/min/max/numTiles/ceildiv`) | CUDA Tile through TC's `tile.*` IR: a tensor is a strided view past HAT's header; 1D tiles are 1 x n; helpers that take tiles are inlined |
| `dispatchTile` | one CUDA Tile block per tile, numbered as HAT's backends number them (a warp flag on X flattens them) |

**Not supported yet:** HAT's `Tensor` (warp-level WMMA) and `WRS()`. Mixed row/column-major loads
need a column-major B-fragment load that TC's MMA intrinsics do not have. CUDA Tile kernels run on
CUDA only (TC refuses them elsewhere with TC-E4010).

## Build and run

Needs:
- a Babylon JDK at `$BABYLON/build/linux-x86_64-server-release/images/jdk` (`env.sh`; override with
  `BABYLON=` / `BABYLON_JDK=`);
- HAT built in `$BABYLON/hat/build` (`cd $BABYLON/hat && mvn package`);
- a TornadoVM SDK of this branch (`make jdk22plus BACKEND=opencl,cuda`).

```bash
source ../setvars.sh                      # this branch's SDK
./build.sh                                # build/tornado-babylon.jar + build/examples

# A HAT program on TornadoVM (-d <driver>:<device>; CUDA is 1:0 here, OpenCL 0:0 / 0:1):
H=$BABYLON/hat/build
./run.sh -d 1:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=2DTILING --check --size=256

# TornadoVM @Reflect kernels from their code models:
source ./env.sh
java @build/tornado.args --enable-preview --add-modules=jdk.incubator.code -Dtornado.tc.frontend=babylon \
     -cp build/tornado-babylon.jar:build/examples reflect.ReflectKernels
```

Options:
- `-Dtornado.tc.frontend=auto` (default: code models for HAT kernels; HAT kernels have no other way
  in), `babylon` (every method with a code model) or `bytecode` (off).
- `-Dhat.t.device=<driver>:<device>` picks the HAT backend's device.
- `-Dtornado.hat.zeroCopy=false` copies HAT buffers around each dispatch instead of wrapping them in
  place.
- `-Dtornado.hat.strictFP=true` gives the plans strict floating point.
- `-Dtornado.babylon.dumpModels=true` prints each lowered model as TC reads it.

`@Parallel` through code models needs the class file's local variable names: compile with `-g`
(Maven's default).

## Testing

| Script | What it runs |
|---|---|
| `./hat-tests.sh [-d dev] [Class[#method] ...]` | HAT's own unit tests (`hat/tests`, through HAT's test engine) on TornadoVM; a class whose JVM dies is rerun one method per JVM |
| `./hat-sweep.sh [dev ...]` | HAT's example programs, validated by their `--check` or against HAT's own CUDA output |
| `./reflect-all-sdk.sh` | a copy of the SDK whose unit tests are compiled by Babylon's javac with every static method `@Reflect`, to run the whole TornadoVM unit-test suite with kernels from their code models |

### HAT's unit tests (156 tests in 30 classes)

| Device | TornadoVM (TC) | HAT's own backend |
|---|---|---|
| RTX 4090, CUDA | **150** pass, 2 expected-precision (as HAT), 4 `Tensor` unsupported | 130 pass, 6 fail, 4 expected-precision, some classes not launched |
| RTX 4090, OpenCL | **134** pass; 18 tile tests refused (CUDA Tile is CUDA-only), FlashAttention F16 hits an NVIDIA OpenCL compiler error (beehive-lab/TornadoVM#1297), 4 `Tensor` | 133 pass, 21 fail, 2 expected-precision |
| Intel UHD 770, OpenCL | **132** pass; the rest are tiles, `Tensor`, and 4 kernels using `Math.exp` in double precision on a device without FP64 (TC-E4012) | not run |

The expected-precision tests (`test_bfloat16_14`, `testNbody`) give exactly the values HAT's own
backends give.

### The TornadoVM unit-test suite from code models

`reflect-all-sdk.sh` + `tc-diff` with `-Dtornado.tc.frontend=babylon`, RTX 4090 CUDA: **1627 of 1627
passing tests pass**, the same as the bytecode frontend on the same SDK, JDK and devices. Output and
launch configurations are compared test by test.

### HAT's examples (`hat-sweep.sh`, RTX 4090 CUDA)

squares, mandel, matmul (MT, 1D, 1DFC, 2D, 2DLI, 2DTILING, 2DREGISTERTILING,
2DREGISTERTILING_V, 2DREGISTERTILING_FP16, strict-FP FP16), dft and flash-attention pass. matmul
2DLIF16 prints the same values as HAT's own OpenCL backend (`66.481575 != 63.870296`): the example's
check ignores the F16 rounding of each product. HAT's CUDA backend is further off. `tensors` needs
`Tensor`.
