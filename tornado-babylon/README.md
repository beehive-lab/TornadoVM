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

## HAT kernels in TornadoVM task graphs

`HatTasks` puts HAT kernels, over HAT buffers and their structs, into ordinary TornadoVM task graphs.
They run next to other tasks, with TornadoVM's transfers, grid schedulers and execution plans, CUDA
graphs included (`examples/src/hattasks/NBodyTaskGraph.java`):

```java
var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST); // TornadoHatBackend
Universe universe = Universe.create(accelerator, n);                      // HAT buffer of Body structs
F32Array energy = F32Array.create(accelerator, n);

var hat = HatTasks.on(accelerator);
var graph = new TaskGraph("nbody").transferToDevice(DataTransferMode.FIRST_EXECUTION, hat.of(universe));
hat.task(graph, "accelerate", NBodyTaskGraph::accelerate, universe, 1f, 0.005f);   // @Reflect HAT kernels
hat.task(graph, "move", NBodyTaskGraph::move, universe, 0.005f);
hat.task(graph, "energy", NBodyTaskGraph::energy, universe, energy);
graph.transferToHost(DataTransferMode.EVERY_EXECUTION, hat.of(energy));

var grid = new GridScheduler("nbody.accelerate", new WorkerGrid1D(n));
grid.addWorkerGrid("nbody.move", new WorkerGrid1D(n));
grid.addWorkerGrid("nbody.energy", new WorkerGrid1D(n));
try (var plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withDevice(TornadoExecutionPlan.getDevice(1, 0)).withGridScheduler(grid).withCUDAGraph();
    for (int s = 0; s < steps; s++) {
        plan.execute();
    }
}
```

`hat.of(buffer)` is the buffer's TornadoVM object, over its memory in place, and the same object every
time. RTX 4090, 4096 bodies: 0.311 ms a step, or 0.259 ms with a CUDA graph. Results match plain Java
to 4.8e-7 (relative), on CUDA, OpenCL and the UHD 770.

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
| `Tensor` (16 x 16 x 16 F16 multiply-accumulate: `create`/`zeros`/`fill`, `loadF16` and `store` row- or column-major, `mma`), `WRS()` | two m16n8k16 tensor-core operations per `mma`; operand fragments are loaded where the `mma` uses them (as A or B), accumulators live in a private slot; column-major loads and stores are TC intrinsics added for this (`tornado.mma.load_a.global_col`, `load_b.global_col`, `store_col`). CUDA only |

**Limits:** CUDA Tile and tensor-core kernels run on CUDA only (TC refuses tiles elsewhere with
TC-E4010). HAT tensors are loaded where `mma` uses them rather than where `loadF16` is called.

## Build and run

Tested on Linux x86-64 with an RTX 4090 (CUDA 12.6 toolkit, driver for CUDA 13) and an Intel UHD 770.

**1. A Babylon JDK** ([openjdk/babylon](https://github.com/openjdk/babylon), branch `code-reflection`;
tested at `87aaaa4b0`). Its boot JDK is JDK 26:

```bash
git clone -b code-reflection https://github.com/openjdk/babylon.git ~/src/babylon
cd ~/src/babylon
bash configure --with-boot-jdk=$HOME/.sdkman/candidates/java/26.0.1-open   # e.g. sdk install java 26.0.1-open
make images                         # build/linux-x86_64-server-release/images/jdk
```

**2. HAT**, in the same checkout, with that JDK (needs Maven and CMake):

```bash
cd ~/src/babylon/hat
JAVA_HOME=~/src/babylon/build/linux-x86_64-server-release/images/jdk mvn clean package   # jars in hat/build
```

**3. This TornadoVM branch**, with JDK 25 (`sdk install java 25.0.2-open`):

```bash
cd TornadoVM                                  # this branch
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
make jdk22plus BACKEND=opencl,cuda            # or BACKEND=opencl / BACKEND=cuda
source setvars.sh
```

**4. Build this module and run.** `env.sh` points at the Babylon checkout: set `BABYLON=~/src/babylon`
(default `~/src/babylon-new`), or `BABYLON_JDK=` to a built Babylon JDK.

```bash
cd tornado-babylon
export BABYLON=~/src/babylon
./build.sh                                    # build/tornado-babylon.jar + build/examples
tornado --devices                             # driver:device numbers; here CUDA is 1:0, OpenCL 0:0 / 0:1

# An unmodified HAT program on TornadoVM:
H=$BABYLON/hat/build
./run.sh -d 1:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=2DTILING --check --size=256

# HAT's own unit tests on TornadoVM (all of them, or some classes):
./hat-tests.sh -d 1:0
./hat-tests.sh -d 0:0 TestMatMul TestFlashAttention

# TornadoVM @Reflect kernels compiled from their code models:
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
| RTX 4090, CUDA | **154** pass, 2 expected-precision (the values HAT's own backends give) | 130 pass, 6 fail, 4 expected-precision, some classes not launched |
| RTX 4090, OpenCL | **135** pass, 2 expected-precision; 15 tile and 4 tensor tests refused (CUDA only) | 133 pass, 21 fail, 2 expected-precision |
| Intel UHD 770, OpenCL | **132** pass; the rest are tiles and tensors (CUDA only) and 4 kernels using `Math.exp` in double precision on a device without FP64 (TC-E4012) | not run |

The expected-precision tests (`test_bfloat16_14`, `testNbody`) give exactly the values HAT's own
backends give.

### The TornadoVM unit-test suite from code models

`reflect-all-sdk.sh` + `tc-diff` with `-Dtornado.tc.frontend=babylon`, RTX 4090 CUDA: **1627 of 1627
passing tests pass**, the same as the bytecode frontend on the same SDK, JDK and devices. Output and
launch configurations are compared test by test.

### HAT's examples (`hat-sweep.sh`, RTX 4090 CUDA)

squares, mandel, matmul (MT, 1D, 1DFC, 2D, 2DLI, 2DTILING, 2DREGISTERTILING,
2DREGISTERTILING_V, 2DREGISTERTILING_FP16, strict-FP FP16), dft, flash-attention and tensors pass.
matmul 2DLIF16 prints the same values as HAT's own OpenCL backend (`66.481575 != 63.870296`): the
example's check ignores the F16 rounding of each product. HAT's CUDA backend is further off.

## Performance

HAT's matrix-multiply example, 1024 x 1024 floats, RTX 4090, 30 iterations (`perf-nsys.sh`, `perf.sh`):
the same HAT kernels compiled by TC on TornadoVM, and by HAT's own CUDA and OpenCL backends.

| Kernel | TC kernel time (CUDA) | HAT CUDA kernel time | TC end to end, median | HAT CUDA end to end | HAT OpenCL end to end |
|---|---:|---:|---:|---:|---:|
| 1D | 18.1 ms | 37.2 ms | 18.9 ms | 37.7 ms | 394 ms |
| 2D | 1.79 ms | 1.79 ms | 2.97 ms | 2.95 ms | 351 ms |
| 2DLI | 428 µs | 444 µs | 1.58 ms | 1.63 ms | 65.3 ms |
| 2DTILING | 332 µs | 334 µs | 1.50 ms | 1.58 ms | 29.0 ms |
| 2DREGISTERTILING | 78.7 µs | 76.0 µs | 1.24 ms | 1.25 ms | 11.4 ms |
| 2DREGISTERTILING_V | 112.8 µs | 79.6 µs | 1.27 ms | 1.24 ms | 8.7 ms |
| 2DREGISTERTILING_FP16 | 202 µs | 58.8 µs (result wrong) | 0.87 ms | 0.77 ms (wrong) | 7.7 ms |

- Kernel time is the mean of Nsight Systems' CUDA kernel records; end to end is the example's own
  timing of a dispatch, including transfers. TornadoVM on OpenCL (same GPU) takes 1.03-1.48x its CUDA
  end-to-end times.
- The kernels are on par except three. **1D** is 2.1x faster with TC (not investigated). **2DREGISTERTILING_V** is 1.42x slower: TC loads each lane of a
  `Float4` view from a HAT buffer separately (four 32-bit loads, not one 128-bit load). **FP16** is
  3.4x slower: TC gives Java's results (each half operation rounded as `F16`'s Java methods round,
  in f32 arithmetic), and the faster HAT CUDA kernel's result fails the example's check.
- The first dispatch costs 0.55-0.60 s on TornadoVM against 0.16-0.23 s with HAT's CUDA backend: the
  plan, the TC compilation and NVRTC. Later dispatches reuse the plan.
