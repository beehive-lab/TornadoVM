# TC on Metal: hand-over for the Mac

This file is for continuing the TC Metal backend on a Mac with an Apple-silicon GPU. Everything up to
running on the device was done and checked on Linux (2026-10-11). This lists:
- what exists;
- what was verified without a Mac and how;
- the first steps to take on the Mac;
- the open questions only the device can answer.

TC is the Graal-free TornadoVM compiler (see `docs/compiler-replacement/`). CUDA and OpenCL already
pass the same tests as Graal ([parity-matrix.md](docs/compiler-replacement/parity-matrix.md)). Metal
is the third dialect on the same C core.

## What exists

| Piece | Where | State |
|---|---|---|
| MSL dialect | `tornado-compiler/tc-codegen/.../MetalDialect.java` | Done. Kernel ABI, address-space pointees, types, `as_type`, Java float→int helpers, preamble |
| Intrinsics | `Intrinsics.metal()` | Done. Thread ids, AtomicInteger region, array atomics, barriers, simd functions, math |
| Helpers | `Helpers.METAL` | Done. Java `Math.min/max` for floats; atomics overloaded on `device`/`threadgroup`; float atomic add as a CAS loop |
| Dialect hooks | `CDialect.kernelParameter`, `trailingKernelParameters`, `printf()` | Done. Metal's `[[buffer(n)]]` parameters and system values |
| Target caps | `TargetCaps.METAL_APPLE` (headless), `MetalTornadoDevice.tcTarget()` (runtime) | Done. No fp64, fp16, SIMD 32 |
| Runtime hook | `MetalTornadoDevice.compileTask` | Written like the OpenCL hook; **compiles on Linux, never run** |
| Golden tests | `tc-codegen/src/test/lit/Metal/*.tcir` (8 files) | Pass. Each also runs `mslcheck` |
| Whole-suite sweep | `tc-runtime/.../tools/EmitSweep` | 1338 unit-test kernels → 770 Metal sources |
| Offline MSL check | `tornado-compiler/utils/mslcheck/` | clang plus a mock `metal_stdlib`: **770/770 pass** |
| Visualiser | `tornado --tcirviz`, Metal target | Shows every stage and the emitted MSL |

### The kernel ABI (must match `MetalInstalledCode`)

```
kernel void Owner_method(
    device long *_kernel_context [[buffer(0)]], constant uchar *_constant_region [[buffer(1)]],
    threadgroup uchar *_local_region [[threadgroup(2)]], device int *_atomics [[buffer(3)]],
    device unsigned char *a [[buffer(4)]],          // arrays / objects: the device buffer
    constant int &n [[buffer(5)]],                  // scalars: setBytes, by reference in constant
    ...
    uint3 _thread_position_in_grid [[thread_position_in_grid]], ... [[threads_per_threadgroup]],
    uint3 _threadgroups_per_grid [[threadgroups_per_grid]],   // TC only; system value, no buffer
    device uint *_global_sizes [[buffer(N)]])       // N = 4 + number of parameters, bound last
```

The ABI was taken from the Graal backend (`MetalBackend.emitPrologue`), the binding code
(`MetalInstalledCode`, `MetalObjects.launch`) and the generated example
`tornado-assembly/src/examples/generated/add.metal`. TC differences:
- `constant T&` instead of `constant uniform<T>&`;
- the extra `_threadgroups_per_grid` system value, used for `KernelContext` group counts;
- local arrays declared in the body as `threadgroup T x[N]` instead of carving `_local_region`.

## What was verified without a Mac, and how

1. **Golden tests.** `./mvnw -o -Pjdk21 -pl tornado-compiler/tc-codegen -am install` runs
   `lit/Metal/*.tcir`: FileCheck on the exact text, and `mslcheck` on each.
2. **Every unit-test kernel.** Run the sweep, then check its output:
   ```bash
   source setvars.sh
   TC=$TORNADOVM_HOME/share/java/tornado; UT=$(ls $TC/tornado-unittests-*.jar)
   java --enable-preview -cp "$TC/*" uk.ac.manchester.tornado.tc.runtime.tools.EmitSweep \
       "$UT:$TC/tornado-api-7.2.1-jdk21-dev.jar" $UT --target=metal --out=/tmp/sweep-metal
   tornado-compiler/utils/mslcheck/mslcheck /tmp/sweep-metal/*.metal     # 770/770 on Linux
   ```
   `mslcheck` parses and type-checks with clang against `utils/mslcheck/include/metal_stdlib`. That
   header is a mock written from the MSL specification, with `device`, `threadgroup` and `constant`
   as distinct clang address spaces. It caught a real bug: `as_type<half>(15360)` needed a `(short)`
   cast, since MSL requires equal sizes. It is **not** Apple's compiler; see step 1 below.
3. **Refusals.** Against the OpenCL sweep, the kernels only Metal refuses are:
   - 135 using `double` (TC-E4012), because Apple GPUs have no fp64;
   - one 64-bit atomic add (TC-E4002).
4. **Runtime hook.** The Metal driver builds with it on Linux
   (`./mvnw -o -Pjdk21,metal-backend -pl tornado-drivers/metal -am install -DskipTests`).

## On the Mac, in order

```bash
git clone git@github.com:mikepapadim/tornadovm-compiler.git && cd tornadovm-compiler
git checkout compiler/main
make BACKEND=metal          # or: bin/compile --jdk jdk21 --backend metal
source setvars.sh
tornado --devices           # the Apple GPU under the Metal driver, e.g. 0:0
```

1. **Apple's compiler on the corpus.** Regenerate the sweep (command above), then compile every file
   with the real Metal compiler. Fix the dialect until this is clean:
   ```bash
   for f in /tmp/sweep-metal/*.metal; do xcrun -sdk macosx metal -std=metal3.0 -c "$f" -o /dev/null || echo "FAIL $f"; done
   ```
   Then add an `xcrun metal` tool to `LitTest`, as `clbuild` is for OpenCL (feature `metal`), and
   mark `lit/Metal/*.tcir` with `RUN: ... | metalc`.
2. **Smoke test.** One kernel on the device, TC with fallback off:
   ```bash
   tornado-test -V -J"-Dtornado.compiler=tc -Dtornado.compiler.fallback=false -Dtornado.printKernel=true" \
       uk.ac.manchester.tornado.unittests.foundation.TestIntegers
   ```
   If arguments arrive in the wrong buffers, compare `--printKernel` against Graal's
   (`-Dtornado.compiler=graal`) for the same task. Also check the runtime's argument-reflection log
   (`MetalInstalledCode`, `[Metal-reflection]`).
3. **Graal reference, then TC, with the differential harness.** Use the device id from
   `tornado --devices`:
   ```bash
   python3 docs/compiler-replacement/baseline/tc-diff.py run --device 0:0 --compilers graal,tc --out ~/tc-diff/metal1
   python3 docs/compiler-replacement/baseline/tc-diff.py diff --out ~/tc-diff/metal1
   ```
   The goal, as on CUDA and OpenCL: TC passes every test Graal on Metal passes, with fallback off.
   Each remaining difference is either fixed or recorded with its reason in
   `docs/compiler-replacement/parity-matrix.md`.
4. **jitLLM on Metal.** Greedy output must be token-identical, as on CUDA and OpenCL:
   `./jitllm run -m <model> --gpu --metal --temperature 0 ...` under `-Dtornado.compiler=graal` and
   `=tc`, then `jitllm bench`. See `docs/compiler-replacement/perf/jitllm/bench.sh`.
5. **Record the results.** Add a Metal section to `parity-matrix.md`, update `checkpoints.md` (C6)
   and `design-review.md`.

## Open questions only the device answers

| Question | Why it matters | Where to look |
|---|---|---|
| Fast math | Metal compiles with fast math unless `MTLCompileOptions` turns it off. That can drop `isnan` and change NaN/±0 handling in `java_fminf`/`tc_f2i`, and Java-semantics tests would fail on TC *and* Graal | `MetalObjects` (library compile options); compare with Graal's results |
| Threadgroup memory | TC declares `threadgroup T x[N]` in the kernel, while Graal binds `_local_region` with `setThreadgroupMemoryLength`. When a task has no local size, the runtime marks slot 2 unused; check that is valid with a declared but unbound `threadgroup` parameter | reductions, `kernelcontext` local-memory tests |
| `atomic_fetch_min/max` on `threadgroup` | MSL support depends on the GPU family | `atomics` family |
| Vectors wider than 4 lanes | Refused (TC-E4001). Graal emulates `float8`/`float16` with preamble structs. If Graal passes those tests on Metal, TC needs the same | `vectortypes.TestFloats` and similar |
| `double` | Refused (TC-E4012), like the hardware. Graal fails these too unless it emulates them | `TestDoubles` |
| `Debug.printf` | Dropped with a comment, like Graal (MSL has no printf) | `TestKernelPrintf`-style tests |
| `dp4a`, `simd_ballot`, tensor-core MMA (`simdgroup_matrix`), tiles | No Metal spelling yet (TC-E4002/E4001); OpenCL lacks some of these too | add rows to `Intrinsics.metal()` if Graal runs them |
| Argument sizes | `byte`/`char`/`short` scalars are declared at their width (`constant char &`), as `PrimitiveSerialiser` writes them | `TestBytes`, `TestShorts` |

## Pointers

- Plan and gates: `docs/compiler-replacement/plan.md` and `checkpoints.md`.
- CUDA/OpenCL precedent for every piece above: `CudaDialect` and `OpenClDialect`, plus the TcHook blocks in `CUDATornadoDevice` and `OCLTornadoDevice`.
- Debugging a kernel: `-Dtornado.tc.dump=all -Dtornado.tc.dumpDir=DIR`, or `tornado --tcirviz` with the Metal target. That shows each stage and pass with links back to the Java line.
