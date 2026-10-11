# TC on Metal: hand-over for the Mac

> **Validated after the Graal removal (2026-10-11, Apple M3 Pro).** TC only, upstream `develop`
> merged: 966 PASS against 941 in the last run with Graal present (`metal3`), no regression;
> `TestSimdgroupMatrixHalf` 6/6 after adding half/transposed/mixed SIMD-group matrices; jitLLM
> token-identical and, after caching a per-launch TC query, 2.6-3.3x faster than before; AOT cache
> and native image work on macOS. See
> [Results after the Graal removal](#results-after-the-graal-removal).

**Status (2026-10-11, Apple M3 Pro): done.** TC on Metal passes every test Graal on Metal passes,
with fallback off, except two accepted differences; jitLLM output is token-identical. See
[Results on the Mac](#results-on-the-mac) and the Metal section of
[parity-matrix.md](docs/compiler-replacement/parity-matrix.md).

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
| Golden tests | `tc-codegen/src/test/lit/Metal/*.tcir` (10 files) | Pass. Each also runs `mslcheck`, and `metalc` on a Mac |
| Whole-suite sweep | `tc-runtime/.../tools/EmitSweep` | 1338 unit-test kernels → 770 Metal sources |
| Offline MSL check | `tornado-compiler/utils/mslcheck/` | clang plus a mock `metal_stdlib`: **770/770 pass** on Linux |
| Apple's compiler | `tornado-compiler/utils/metalc/` (build line in `metalc.m`) | `newLibraryWithSource:` on the GPU, as the driver compiles: **778/778** |
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
3. **The suite, against the last run.** Use the device id from `tornado --devices`. Graal has
   been removed; the reference is the run recorded when Graal was still there (Metal parity: TC 941
   vs Graal 942 on the M3 Pro):
   ```bash
   python3 docs/compiler-replacement/baseline/tc-diff.py run --device 0:0 --out ~/tc-diff/metal2 --against ~/tc-diff/metal1
   ```
   The goal: no test that passed before fails now.
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

## Results on the Mac

Apple M3 Pro, macOS 26.6, JDK 21.0.2, `make BACKEND=metal`, device 0:0.

1. **Apple's compiler on the corpus**: 778/778 with `utils/metalc` after one fix (MSL has no
   `goto`). Xcode is not needed: `metalc` uses the Metal framework's runtime compiler, the call the
   driver makes. `LitTest` runs it on `lit/Metal/*.tcir` when `utils/metalc/metalc` is built.
2. **Smoke test**: `foundation.TestIntegers` 7/7 under TC with fallback off; arguments landed in
   the right buffers first time.
3. **Differential runs**: `metal1` Graal 942 / TC 921 → `metal3` Graal 942 / TC 941 (TC better on
   one test, two accepted differences). Fixes in between: goto-free control flow, fp64 refusals as
   `TornadoDeviceFP64NotSupported`, simdgroup matrices, dp4a, 64-bit atomic add, NaN tests under
   fast math. Details in parity-matrix.md.
4. **jitLLM**: Greedy output token-identical to Graal on Metal for Llama-3.2-1B-Instruct F16 (181 kernels by TC) and Qwen3-1.7B F16 (313 kernels), fallback off, `--fp32-kv-cache` (jitLLM has no FP16 key/value path on Metal). Bench (`jitllm bench --pp 128,512 --tg 64 --repetitions 5`, two rounds in alternating order, [perf/jitllm/metal](docs/compiler-replacement/perf/jitllm/metal)), TC/Graal mean of the two rounds' medians: Llama 1.04 / 1.01 / 1.08 (pp128/pp512/tg64), Qwen3 0.99 / 0.99 / 0.98. Round-to-round spread on this laptop is up to 15% (Qwen3 tg64: 1.08 then 0.89), so the two compilers are level within noise.

Answers to the open questions:

| Question | Answer |
|---|---|
| Fast math | On (the driver passes no `MTLCompileOptions`). It folds `x != x`; TC now spells NaN tests with `isnan`, which fast math keeps. `TestMath` and `TestTornadoMathCollection` match Graal |
| Threadgroup memory | Declared `threadgroup T x[N]` with slot 2 unbound works: reductions and `kernelcontext` local-memory tests match Graal |
| `atomic_fetch_min/max` on `threadgroup` | Compiles on the M3 (`TestAtomicRmw` kernels in the sweep); the tests themselves skip Metal under both compilers |
| Vectors wider than 4 lanes | `vectortypes` matches Graal (the same 2 tests unsupported under both) |
| `double` | Both compilers report the fp64 tests unsupported |
| `Debug.printf` | Dropped with a comment under both; `TestHello` matches Graal |
| `dp4a`, simdgroup matrices | Implemented (helpers; `simdgroup_float8x8` values). `simd_ballot` and tensor-core MMA: no Graal test passes with them on Metal |
| Argument sizes | `TestShorts` and `TestSignedBytes` match Graal |

Notes for whoever continues:
- `xcrun metal` needs full Xcode with its licence accepted (and, on Xcode 26+, the Metal
  toolchain component); `metalc` avoids both.
- On macOS, `mslcheck` uses Apple clang; 7 sweep files with `half2` across address spaces fail the
  mock header only (Apple's compiler accepts them).
- `tc-diff.py` launch-configuration differences in the reduction classes come from JUnit's method
  order, not from the compilers.

## Validate after the Graal removal

State of `compiler/main` (also PR beehive-lab/TornadoVM#1228, branch `feat/tc-compiler`):

- TC is the only compiler. `-Dtornado.compiler` and the fallback are gone, and there is no Graal to
  compare against. The reference is the TC half of the last Mac run, `~/tc-diff/metal3/tc`.
- Merged from upstream `develop`. #1216 and #1217 change `MetalObjects`, `ObjCRuntime`,
  `MetalAPI`, `MetalKernelStackFrame` and `MetalInstalledCode` (now in the driver's root package);
  git merged them without conflicts.
- Linux results with TC only: CUDA 1539, OpenCL (RTX 4090) 1057, UHD 770 934 PASS, with no
  regression. New upstream tests: `TestHalfFloatShortBits` now runs on every backend (TC
  reinterprets the bits everywhere), and `TestNativeArrayVectorLoads`, `TestLoopTransformations`,
  `TestConsumeNamedAndUnnamed` and `TestDependentLaunches` pass on CUDA and OpenCL.

On the Mac, in order:

1. **Build.** `git pull` on `compiler/main`, then `make BACKEND=metal` (JDK 21). To try JDK 22+,
   use `make jdk22plus BACKEND=metal` with `JAVA_HOME` set to a JDK 25 first. There are no
   `graalJars` any more: delete a stale `graalJars/` directory if one is left.
2. **Apple's compiler on the corpus.** Run `utils/metalc` over the EmitSweep output, and `LitTest`
   (`lit/Metal/*.tcir`), as before.
3. **The suite against the last Mac run:**
   ```bash
   python3 docs/compiler-replacement/baseline/tc-diff.py run --device 0:0 --out ~/tc-diff/metal4 --against ~/tc-diff/metal3
   ```
   Goal: `worse 0` against the 941 TC PASS of `metal3`.
4. **New upstream tests on Metal:**
   - `kernelcontext.matrices.TestSimdgroupMatrixHalf` (#1223): half and transposed SIMD-group
     matrices, plus mixed half/float multiply-accumulate. TC so far has `Matrix8x8Float` only. If it
     refuses (TC-E4001), add `simdgroup_half8x8`, `simdgroup_load(..., transpose)` and the mixed
     `simdgroup_multiply_accumulate` to `MetalDialect`/`Intrinsics.metal()`, plus a `lit/Metal`
     golden test.
   - `arrays.TestNativeArrayVectorLoads`: `getFloat4`/`getHalf4` become `float4`/`half4` loads.
     MSL has both types; check the 16- and 8-byte alignment.
   - `arrays.TestHalfFloatShortBits`: `as_type<half>(ushort)`.
   - `arrays.TestByteArrayWordAccess` (#1198): `ByteArray.getInt/getLong/setInt/setLong`, one 32- or
     64-bit access at a byte offset.
   - `kernelcontext.api.TestDependentLaunches`, `api.TestConsumeNamedAndUnnamed`,
     `loops.TestLoopTransformations`: runtime-side, and they exercise #1216 (batched command
     buffers).
5. **Runtime changes from upstream.** #1216 encodes kernels into shared command buffers, and the
   kernel stack frame is no longer written before every launch. Check `TestDependentLaunches` and
   the `api.*` families, which already run in step 3.
6. **jitLLM.** Rerun the greedy token-identity check against the recorded Graal outputs in
   `perf/jitllm/metal`, and the bench.
7. **Fast start-up on macOS (new):** `tornado --aotCache app.aot` (JDK 25) and
   `tornado-native-image` (GraalVM for JDK 25, aarch64). The Metal driver makes Objective-C calls
   through FFM, so the native-image agent must record `objc_msgSend` downcalls. See README, "Fast
   start-up".
8. **Record** the results here and in the Metal section of `parity-matrix.md`, then commit and push
   to `compiler/main`.

### Results after the Graal removal

Apple M3 Pro, `compiler/main` at `d86d063ac` plus the SIMD-group half work.

1. **Build**: `make BACKEND=metal` (JDK 21) and `make jdk22plus BACKEND=metal` (JDK 25.0.2) both
   build; `tornado --devices` shows the M3 Pro at 0:0.
2. **Apple's compiler**: 795/795 EmitSweep kernels with `utils/metalc`; `LitTest` 33/33 (FileCheck,
   `mslcheck` and `metalc` on every `lit/Metal` file).
3. **Suite against `metal3`**: `metal4` (as pulled) 960 PASS, `metal5` (with the fix below) **966**,
   `metal6` (with the `writesLoopIndex` cache, step 6) 965, against 941; `metal6`'s extra row is
   `TestWarmUp#test01`, a wall-clock test that misses its 8 ms window on macOS (see parity-matrix.md). Worse: only the two removed `*RejectedOnNonCuda` tests (now
   `testAtomicCasOnLocalArray`/`testAtomicMaxOnLocalArray`, PASS). Launch differences: JUnit method
   order.
4. **New upstream tests**: `TestHalfFloatShortBits` 6/6, `TestByteArrayWordAccess` 4/4,
   `TestNativeArrayVectorLoads` 3/3, `TestDependentLaunches` 2/2, `TestConsumeNamedAndUnnamed` 2/2,
   `TestLoopTransformations` 4/4. `TestSimdgroupMatrixHalf` was 0/6 (TC-E1003) and is **6/6** with
   `Matrix8x8Half` = `simdgroup_half8x8`, transposed loads from device and threadgroup memory,
   mixed half/float multiply-accumulate and threadgroup stores (`Bindings`, `Intrinsics.metal()`,
   the `tc_simdgroup_matrix` helper; golden test `lit/Metal/simdgroup-matrix-half.tcir`).
5. **Runtime changes (#1216/#1217)**: covered by step 3; `TestDependentLaunches` and the `api`
   families pass.
6. **jitLLM**: greedy output token-identical to the recorded Graal output (Llama-3.2-1B F16,
   Qwen3-1.7B F16; `docs/compiler-replacement/perf/jitllm/metal/greedy-*.txt`). Bench, TC only (`docs/compiler-replacement/perf/jitllm/metal/bench-tconly.sh`, tok/s medians): Llama-3.2-1B 43.3 / 33.1 / 45.7 (pp128 / pp512 / tg64; before the Graal removal 15.5 / 12.8 / 17.5), Qwen3-1.7B 28.6 / 19.5 / 31.1 (8.8 / 7.6 / 9.6). As pulled, decode had dropped to 4.8-4.9 (Llama) and 2.0 (Qwen3): since `78c431498` the runtime asks TC before every launch whether the kernel writes its loop index (`KernelAccess.writesLoopIndex`, `TornadoVMInterpreter`), and `TcAccessProvider` answered uncached, re-reading the class file and re-running the frontend each time; the Graal sketch had cached it. Found by bisecting (`de219e121` 18.0 tok/s, `78c431498` 4.9, and 18.4 with `-Dtornado.tc.checkAccess=true`, which built the sketch). Fixed by caching the answer per method; the rest of the gain over the old numbers is upstream's batched Metal dispatch (#1216). This affects every backend, not only Metal.
7. **Fast start-up** (JDK 25 SDK, saxpy on 2^20 floats, result checked): JVM 0.42 s, Leyden AOT
   cache 0.26 s (25 MB cache), GraalVM 25.0.1 native image 0.14 s (25 MB executable, 42 s build,
   no changes needed for the Objective-C downcalls). `tc emit --target metal` works.
